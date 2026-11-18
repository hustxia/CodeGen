package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenOutputProperties;
import com.codegen.entity.GenOutput;
import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import com.codegen.mapper.GenOutputMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.service.GenVerifyService;
import com.codegen.support.GenPathResolver;
import com.codegen.vo.GenVerifyVO;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真校验实现：校验**磁盘上的实际文件**（selfCheck 校验的是内存渲染产物，二者互补）。
 *
 * <p>校验依据是 gen_output 台账里该表最近一次写盘批次的产物清单，
 * 逐文件静态解析，输出逐项 PASS/WARN/FAIL：
 * <ul>
 *   <li>Java：package 声明与表配置包名一致、类名与文件名一致；</li>
 *   <li>vue：&lt;template&gt;/&lt;script&gt; 完整性；</li>
 *   <li>api.js：export function 清单可解析；vue 页面 import 与导出交叉核对；</li>
 *   <li>路由：模块已注册进生成物 src/router/gen-routes.js（component 指向 views/&lt;module&gt;/index.vue）。</li>
 * </ul>
 */
@Service
public class GenVerifyServiceImpl implements GenVerifyService {

    private static final Pattern PKG_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("\\b(?:class|interface|enum)\\s+(\\w+)");
    private static final Pattern EXPORT_FN_PATTERN = Pattern.compile("export\\s+function\\s+(\\w+)");
    private static final Pattern IMPORT_FN_PATTERN =
            Pattern.compile("import\\s*\\{([^}]+)}\\s*from\\s*['\"]@/api/([\\w-]+)['\"]");

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenOutputMapper genOutputMapper;

    @Autowired
    private GenOutputProperties outputProperties;

    @Autowired
    private SysProjectMapper projectMapper;

    @Override
    public GenVerifyVO check(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要校验的表");
        }
        GenVerifyVO vo = new GenVerifyVO();
        // 一次查回：校验一批表时逐条 selectById 会随勾选数量线性增长
        Map<Long, GenTable> tableById = new HashMap<>();
        for (GenTable t : genTableMapper.selectBatchIds(ids)) {
            tableById.put(t.getId(), t);
        }
        for (Long id : ids) {
            GenTable table = tableById.get(id);
            if (table != null) {
                verifyTable(table, vo);
            }
        }
        if (vo.getItems().isEmpty()) {
            throw new BusinessException(404, "表配置不存在或没有可校验的写盘记录");
        }
        vo.setTotal(vo.getItems().size());
        vo.setPass(count(vo, "PASS"));
        vo.setWarn(count(vo, "WARN"));
        vo.setFail(count(vo, "FAIL"));
        vo.setPassed(vo.getFail() == 0);
        return vo;
    }

    private void verifyTable(GenTable table, GenVerifyVO vo) {
        // 取该表最近一次写盘批次的台账明细
        GenOutput latest = genOutputMapper.selectOne(new LambdaQueryWrapper<GenOutput>()
                .eq(GenOutput::getTableId, table.getId())
                .orderByDesc(GenOutput::getId)
                .last("LIMIT 1"));
        if (latest == null) {
            vo.getItems().add(new GenVerifyVO.Item(table.getTableName(), "WARN", "该表尚未写盘（gen_output 无记录）"));
            return;
        }
        List<GenOutput> rows = genOutputMapper.selectList(new LambdaQueryWrapper<GenOutput>()
                .eq(GenOutput::getTableId, table.getId())
                .eq(GenOutput::getBatchNo, latest.getBatchNo())
                .orderByAsc(GenOutput::getId));

        String vueContent = null;
        String apiPath = null;
        Set<String> apiExports = new HashSet<>();
        String vueRelPath = null;

        for (GenOutput row : rows) {
            String relPath = row.getRelPath();
            // 只有真正落盘的文件才需要校验磁盘
            if (!"WRITTEN".equals(row.getAction()) && !"OVERWRITTEN".equals(row.getAction())
                    && !"MERGED".equals(row.getAction())) {
                continue;
            }
            if (!StringUtils.hasText(row.getAbsPath())) {
                vo.getItems().add(new GenVerifyVO.Item(relPath, "FAIL", "台账缺少写盘绝对路径，无法校验"));
                continue;
            }
            Path target = Paths.get(row.getAbsPath());
            if (!Files.exists(target)) {
                vo.getItems().add(new GenVerifyVO.Item(relPath, "FAIL", "磁盘文件不存在：" + row.getAbsPath()));
                continue;
            }
            String content;
            try {
                content = Files.readString(target, StandardCharsets.UTF_8);
            } catch (Exception e) {
                vo.getItems().add(new GenVerifyVO.Item(relPath, "FAIL", "文件读取失败：" + e.getMessage()));
                continue;
            }
            if (relPath.endsWith(".java")) {
                verifyJava(table, relPath, content, vo);
            } else if (relPath.endsWith(".vue")) {
                verifyVue(relPath, content, vo);
                vueContent = content;
                vueRelPath = relPath;
            } else if (isApiJs(relPath)) {
                verifyApiJs(relPath, content, apiExports, vo);
                apiPath = relPath;
            } else {
                vo.getItems().add(new GenVerifyVO.Item(relPath, "PASS", "文件存在，无专属校验规则"));
            }
        }

        // api.js 导出 与 vue 页面 import 交叉核对（同批次内）
        if (vueContent != null && apiPath != null) {
            crossCheckApiImports(apiPath, apiExports, vueRelPath, vueContent, vo);
        }

        // 路由注册核对：生成页面必须出现在生成物 gen-routes.js 中
        if (isOn(table.getGenFrontend()) && isOn(table.getGenVuePage())) {
            verifyRoute(table, vo);
        }
    }

    /**
     * Java 文件：package 声明与文件所在目录一致；类名与文件名一致。
     * 期望包名按 Maven 标准布局从文件路径推导（src/main/java 之后的部分），
     * 而非表配置的基础包名——生成文件分布在 entity/mapper/service 等子包。
     */
    private void verifyJava(GenTable table, String relPath, String content, GenVerifyVO vo) {
        Matcher pkg = PKG_PATTERN.matcher(content);
        String expectedPkg = packageOfPath(relPath);
        if (expectedPkg == null) {
            vo.getItems().add(new GenVerifyVO.Item(relPath, "WARN", "路径不含 src/main/java 源根，跳过 package 核对"));
            return;
        }
        if (pkg.find()) {
            String actual = pkg.group(1);
            vo.getItems().add(new GenVerifyVO.Item(relPath, actual.equals(expectedPkg) ? "PASS" : "FAIL",
                    actual.equals(expectedPkg) ? "package 声明与文件路径一致（" + actual + "）"
                            : "package 声明 " + actual + " 与文件路径推导包 " + expectedPkg + " 不一致"));
        } else {
            vo.getItems().add(new GenVerifyVO.Item(relPath, "FAIL", "未解析到 package 声明"));
        }
        String fileName = relPath.substring(relPath.lastIndexOf('/') + 1);
        Matcher cls = CLASS_PATTERN.matcher(content);
        if (cls.find()) {
            String className = cls.group(1);
            vo.getItems().add(new GenVerifyVO.Item(relPath, fileName.equals(className + ".java") ? "PASS" : "FAIL",
                    fileName.equals(className + ".java") ? "类名与文件名一致（" + className + "）"
                            : "类名 " + className + " 与文件名 " + fileName + " 不一致"));
        } else {
            vo.getItems().add(new GenVerifyVO.Item(relPath, "FAIL", "未解析到类/接口/枚举声明"));
        }
    }

    /**
     * 从相对路径推导期望包名：…/src/main/java/com/shop/admin/entity/X.java → com.codegen.entity
     */
    private String packageOfPath(String relPath) {
        String normalized = relPath.replace('\\', '/');
        int idx = normalized.indexOf("src/main/java/");
        if (idx < 0) {
            return null;
        }
        String rest = normalized.substring(idx + "src/main/java/".length());
        int slash = rest.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        return rest.substring(0, slash).replace('/', '.');
    }

    /**
     * vue 页面：template/script 两段必须齐全且闭合
     */
    private void verifyVue(String relPath, String content, GenVerifyVO vo) {
        boolean ok = content.contains("<template") && content.contains("</template>")
                && content.contains("<script") && content.contains("</script>");
        vo.getItems().add(new GenVerifyVO.Item(relPath, ok ? "PASS" : "FAIL",
                ok ? "<template>/<script> 完整" : "缺少 <template> 或 <script> 段（或未闭合）"));
    }

    private void verifyApiJs(String relPath, String content, Set<String> exports, GenVerifyVO vo) {
        Matcher m = EXPORT_FN_PATTERN.matcher(content);
        while (m.find()) {
            exports.add(m.group(1));
        }
        vo.getItems().add(new GenVerifyVO.Item(relPath, exports.isEmpty() ? "FAIL" : "PASS",
                exports.isEmpty() ? "api.js 解析不到任何 export function"
                        : "解析到 " + exports.size() + " 个导出函数：" + String.join(",", exports)));
    }

    /**
     * 交叉核对：vue 页面 import 的函数必须在 api.js 有对应导出（FAIL）；
     * api.js 导出未被本表页面引用（WARN，可能供其他页面使用）
     */
    private void crossCheckApiImports(String apiPath, Set<String> exports, String vueRelPath,
                                      String vueContent, GenVerifyVO vo) {
        Set<String> imported = new HashSet<>();
        Matcher m = IMPORT_FN_PATTERN.matcher(vueContent);
        while (m.find()) {
            for (String name : m.group(1).split(",")) {
                String fn = name.trim();
                if (!fn.isEmpty()) {
                    imported.add(fn);
                }
            }
        }
        for (String fn : imported) {
            if (!exports.contains(fn)) {
                vo.getItems().add(new GenVerifyVO.Item(vueRelPath, "FAIL",
                        "页面 import 的 " + fn + " 未在 " + apiPath + " 导出"));
            }
        }
        for (String fn : exports) {
            if (!imported.contains(fn)) {
                vo.getItems().add(new GenVerifyVO.Item(apiPath, "WARN",
                        "导出函数 " + fn + " 未被本表页面引用（可能供其他页面使用）"));
            }
        }
        if (imported.isEmpty()) {
            vo.getItems().add(new GenVerifyVO.Item(vueRelPath, "WARN", "页面未从 @/api/* 导入任何函数"));
        }
    }

    /**
     * 路由注册核对：gen-routes.js 是代码生成器的产物（/gen/menu/route-sync 重建），
     * 以 component 指向 views/&lt;module&gt;/index.vue 为注册标志
     */
    private void verifyRoute(GenTable table, GenVerifyVO vo) {
        // 与写盘/路由重建同一个解析器：表级 → 系统 → 目标模块 → 全局。
        // 早先这里只读表级 frontendRoot + 全局 frontendPath，多系统下会去校验错的前端工程。
        SysProject project = table.getProjectId() == null ? null : projectMapper.selectById(table.getProjectId());
        String root = GenPathResolver.frontendRoot(table, project, outputProperties);
        if (!StringUtils.hasText(root)) {
            vo.getItems().add(new GenVerifyVO.Item("src/router/gen-routes.js", "WARN", "前端根目录未配置，跳过路由核对"));
            return;
        }
        Path base = Paths.get(root).toAbsolutePath().normalize();
        // 前端工程可能位于 frontend-root 的子目录（monorepo）
        String sub = GenPathResolver.frontendPath(table, project, outputProperties);
        if (StringUtils.hasText(sub)) {
            Path resolved = base.resolve(sub).normalize();
            if (resolved.startsWith(base)) {
                base = resolved;
            }
        }
        Path routeFile = base.resolve("src/router/gen-routes.js");
        if (!Files.exists(routeFile)) {
            vo.getItems().add(new GenVerifyVO.Item("src/router/gen-routes.js", "FAIL",
                    "路由文件不存在（请执行重建路由 /gen/menu/route-sync）"));
            return;
        }
        try {
            String content = Files.readString(routeFile, StandardCharsets.UTF_8);
            String marker = "import('@/views/" + table.getModuleName() + "/index.vue')";
            vo.getItems().add(new GenVerifyVO.Item("src/router/gen-routes.js",
                    content.contains(marker) ? "PASS" : "FAIL",
                    content.contains(marker) ? "模块 " + table.getModuleName() + " 路由已注册"
                            : "路由未注册：gen-routes.js 中未找到 " + marker));
        } catch (Exception e) {
            vo.getItems().add(new GenVerifyVO.Item("src/router/gen-routes.js", "FAIL", "路由文件读取失败：" + e.getMessage()));
        }
    }

    private boolean isApiJs(String relPath) {
        String normalized = relPath.replace('\\', '/');
        return normalized.contains("/api/") && normalized.endsWith(".js");
    }

    private boolean isOn(Integer flag) {
        return flag != null && flag == 1;
    }

    private int count(GenVerifyVO vo, String severity) {
        return (int) vo.getItems().stream().filter(i -> severity.equals(i.getSeverity())).count();
    }
}
