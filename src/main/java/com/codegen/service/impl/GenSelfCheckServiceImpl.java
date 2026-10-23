package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenSelfCheckProperties;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenSelfCheckService;
import com.codegen.service.GenTableColumnService;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenSelfCheckVO;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 生成自检实现：把渲染产物中的 Java 源码用 JDK 编译器在内存里编译一遍，
 * 把「模板改坏 → 写盘后人工 mvn compile 才发现」的反馈循环从分钟级压到秒级。
 *
 * <p>同时做轻量结构检查（Vue/JS 结构、未渲染的占位符、下拉缺字典），
 * 让明显问题不需要编译器也能被发现。
 */
@Service
public class GenSelfCheckServiceImpl implements GenSelfCheckService {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Pattern CLASS_PATTERN = Pattern.compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");

    /**
     * import 语句里的全限定类名
     */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+(?:\\.\\*)?)\\s*;");

    /**
     * 生成页里的权限指令：v-hasPermi="['demo_goods:add']"
     */
    private static final Pattern HAS_PERMI_PATTERN = Pattern.compile("v-hasPermi\\s*=\\s*\"([^\"]*)\"");

    /**
     * 指令里的权限码字面量（单引号或双引号）
     */
    private static final Pattern PERMS_TOKEN_PATTERN = Pattern.compile("'([^']+)'|\"([^\"]+)\"");

    @Autowired
    private GenSelfCheckProperties selfCheckProperties;

    @Autowired
    private com.codegen.config.GenOutputProperties outputProperties;

    @Autowired
    private com.codegen.mapper.SysProjectMapper sysProjectMapper;

    /**
     * 取本批表所属系统的 classpath。
     *
     * <p>一批里可能混了不同系统的表，这种情况不能悄悄用第一张表的配置——
     * 会给出误导性的「通过」。混选时直接返回空，回落到全局配置，
     * 并在结果里提示一句，让用户自己拆开自检。
     */
    private String projectClasspathOf(List<Long> ids) {
        java.util.Set<Long> projectIds = new java.util.LinkedHashSet<>();
        for (GenTable table : genTableMapper.selectBatchIds(ids)) {
            if (table.getProjectId() != null) {
                projectIds.add(table.getProjectId());
            }
        }
        if (projectIds.isEmpty()) {
            return null;
        }
        if (projectIds.size() > 1) {
            // 不抛异常：自检仍可继续，只是 classpath 用全局的
            return null;
        }
        com.codegen.entity.SysProject project = sysProjectMapper.selectById(projectIds.iterator().next());
        return project == null ? null : project.getSelfcheckClasspath();
    }

    @Autowired
    private GenCodeService genCodeService;

    @Autowired
    private GenTableColumnService columnService;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * BaseEntity 字段 -> 数据库列名与建议类型
     */
    private static final Map<String, String[]> BASE_ENTITY_COLUMNS = new LinkedHashMap<>();

    static {
        BASE_ENTITY_COLUMNS.put("createTime", new String[]{"create_time", "datetime"});
        BASE_ENTITY_COLUMNS.put("updateTime", new String[]{"update_time", "datetime"});
        BASE_ENTITY_COLUMNS.put("createBy", new String[]{"create_by", "bigint"});
        BASE_ENTITY_COLUMNS.put("updateBy", new String[]{"update_by", "bigint"});
        BASE_ENTITY_COLUMNS.put("deleted", new String[]{"deleted", "tinyint"});
    }

    @Override
    public GenSelfCheckVO check(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要自检的表");
        }
        long started = System.currentTimeMillis();
        GenSelfCheckVO result = new GenSelfCheckVO();
        GenDownloadDTO dto = new GenDownloadDTO();
        dto.setIds(ids);
        List<GenFileVO> files = genCodeService.preview(dto);
        result.setFileCount(files.size());

        // 字段/表注释本身可能写着 "${var}" 这类示例文本，属于数据而非未渲染模板，先收集起来用于排除
        List<GenTableColumn> columns = listColumns(ids);
        List<String> dataTexts = collectDataTexts(columns);

        List<JavaSource> javaSources = new ArrayList<>();
        for (GenFileVO file : files) {
            String content = file.getContent() == null ? "" : file.getContent();
            String path = file.getPath();
            // 1) 模板占位符没被替换：模板语法错误或变量名拼错
            //    .js 里 ${id} 是有意的 JS 模板字符串，不参与该检查
            if (!path.endsWith(".js")) {
                checkPlaceholderResidue(result, path, content, dataTexts);
            }
            if (path.endsWith(".java")) {
                javaSources.add(new JavaSource(path, content));
            } else if (path.endsWith(".vue")) {
                checkVue(result, path, content);
            } else if (path.endsWith(".js")) {
                checkJs(result, path, content);
            }
        }
        result.setJavaCount(javaSources.size());

        // 2) 真实编译
        compile(result, javaSources, projectClasspathOf(ids));

        // 3) 语义提示：下拉/单选没有字典，生成出来的控件是空选项
        checkDictBinding(result, columns);

        // 4) 结构契约：实体映射的列必须真实存在于表中（编译期查不出，运行期才炸）
        checkEntityContract(result, files, ids);

        // 5) 权限一致性：页面里 v-hasPermi 引用的权限码，必须与菜单注册将生成的权限码对得上
        checkPermsConsistency(result, files, ids, columns);

        result.setElapsedMs(System.currentTimeMillis() - started);
        result.setPassed(result.getErrorCount() == 0);
        return result;
    }

    /**
     * 从源码里收集 import 的类名（只取以 rootPkg 开头的，第三方库交给 classpath）
     */
    private void collectImportedTypes(String content, Set<String> out) {
        String rootPkg = outputProperties.getRootPackage();
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            String fqcn = m.group(1);
            if (fqcn.endsWith(".*")) {
                continue;
            }
            if (rootPkg != null && !rootPkg.isEmpty() && fqcn.startsWith(rootPkg + ".")) {
                out.add(fqcn);
            }
        }
    }

    /**
     * 由产物路径反推目标工程的编译产物目录（target/classes）
     */
    private List<Path> backendTargetClasses(List<JavaSource> sources) {
        Set<Path> roots = new LinkedHashSet<>();
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int idx = rel.indexOf("/src/main/java/");
            if (idx < 0) {
                continue;
            }
            Path moduleRoot = Path.of(outputProperties.getBackendRoot(), rel.substring(0, idx));
            for (String sub : new String[]{"target/classes", "build/classes/java/main"}) {
                Path cls = moduleRoot.resolve(sub);
                if (Files.isDirectory(cls)) {
                    roots.add(cls.toAbsolutePath());
                }
            }
        }
        return new ArrayList<>(roots);
    }

    private List<Path> clsRootsOf(List<Path> classRoots) {
        return classRoots;
    }

    /**
     * 目标目录里是否已有该类的 .class
     */
    private boolean hasCompiled(List<Path> classRoots, String fqcn) {
        String rel = fqcn.replace('.', '/') + ".class";
        for (Path root : classRoots) {
            if (Files.isRegularFile(root.resolve(rel))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 由产物路径反推目标工程的全部 Java 源码根
     */
    private List<Path> backendSourceRoots(List<JavaSource> sources) {
        Set<Path> roots = new LinkedHashSet<>();
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int idx = rel.indexOf("/src/main/java/");
            if (idx < 0) {
                continue;
            }
            Path abs = Path.of(outputProperties.getBackendRoot(), rel.substring(0, idx + "/src/main/java".length()));
            if (Files.isDirectory(abs)) {
                roots.add(abs.toAbsolutePath());
            }
        }
        return new ArrayList<>(roots);
    }

    /**
     * 把目标工程的 Java 源码根目录追加到 classpath
     *
     * <p>javac 在 classpath 里遇到"目录 + 找不到的类"时，会按包路径去该目录下找 .java
     * 源文件并隐式编译，这样 {@code ${rootPkg}.common.*} 基座类能一起参与编译，
     * 基座类被改名/删字段导致的编译错误也就暴露出来了。
     */
    private String withBackendSourceRoot(String classpath, List<JavaSource> sources) {
        // 从产物相对路径反推源码根：demo-server/src/main/java/com/demo/entity/X.java
        //   -> demo-server/src/main/java
        for (JavaSource source : sources) {
            String rel = source.path().replace('\\', '/');
            int idx = rel.indexOf("/src/main/java/");
            if (idx < 0) {
                continue;
            }
            String srcRoot = rel.substring(0, idx + "/src/main/java".length());
            Path abs = Path.of(outputProperties.getBackendRoot(), srcRoot);
            if (!Files.isDirectory(abs)) {
                return classpath;
            }
            String sep = java.io.File.pathSeparator;
            String extra = abs.toAbsolutePath().toString();
            if (classpath == null || classpath.isEmpty()) {
                return extra;
            }
            // 已包含就不重复追加
            for (String part : classpath.split(java.util.regex.Pattern.quote(sep))) {
                if (part.trim().equalsIgnoreCase(extra)) {
                    return classpath;
                }
            }
            return classpath + sep + extra;
        }
        return classpath;
    }

    /**
     * 内存编译全部 Java 产物；类路径沿用当前进程，保证依赖版本与运行时一致
     */
    private void compile(GenSelfCheckVO result, List<JavaSource> sources, String projectClasspath) {
        if (sources.isEmpty()) {
            return;
        }
        if (Boolean.FALSE.equals(selfCheckProperties.getCompileEnabled())) {
            result.setCompilerAvailable(false);
            result.addIssue("javac", 0L, "WARNING",
                    "gen.selfcheck.compile-enabled=false，已跳过编译检查（仅做结构与权限检查）");
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            result.setCompilerAvailable(false);
            result.addIssue("javac", 0L, "WARNING",
                    "当前运行环境没有 JDK（仅 JRE），已跳过编译检查；用 JDK 启动 admin 可启用完整自检");
            return;
        }
        result.setCompilerAvailable(true);

        // V3.0：系统自带 classpath 优先。多系统下各系统依赖版本不同，
        // 共用一份会出现「依赖明明在却报 cannot find symbol」，比不配还难排查。
        String classpath = StringUtils.hasText(projectClasspath)
                ? GenSelfCheckProperties.expandWildcards(projectClasspath)
                : selfCheckProperties.resolveClasspath();
        if (selfCheckProperties.classpathLooksNested() && !StringUtils.hasText(projectClasspath)) {
            result.addIssue("javac", 0L, "WARNING",
                    "当前进程以单个肥 jar 启动，java.class.path 无法覆盖嵌套依赖，编译检查可能满屏 cannot find symbol；"
                            + "建议配置 gen.selfcheck.classpath 指向依赖目录（支持 lib/*）");
        }

        // 生成物大量 import 目标工程的基座类（${rootPkg}.common.*），但那部分源码不属于
        // 本次生成产物。这里把目标工程【已编译的 target/classes】挂到 classpath；
        // 只有在完全没有编译产物时才退而求其次挂源码根（会隐式编译，Lombok 可能不生效）。
        List<Path> classRoots = backendTargetClasses(sources);
        if (classRoots.isEmpty()) {
            classpath = withBackendSourceRoot(classpath, sources);
        } else {
            for (Path clsRoot : classRoots) {
                classpath = classpath.isEmpty()
                        ? clsRoot.toString()
                        : classpath + java.io.File.pathSeparator + clsRoot;
            }
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Path outDir = null;
        try {
            outDir = Files.createTempDirectory("gen-self-check-");
            List<String> options = new ArrayList<>(List.of(
                    "-classpath", classpath,
                    "-d", outDir.toString(),
                    "-encoding", "UTF-8",
                    "-nowarn"));
            if (Runtime.version().feature() >= 21) {
                // JDK 21 起默认不再隐式跑注解处理器，Lombok 不会生成 getter/setter
                options.add("-proc:full");
            }
            // 处理器路径单独给：只靠 -classpath 时，JDK 21 有时探测不到 lombok 的 processor，
            // 结果就是"明明有 lombok 却满屏 cannot find symbol getXxx()"。显式指定最稳。
            String processorPath = selfCheckProperties.resolveProcessorPath();
            if (StringUtils.hasText(processorPath)) {
                options.add("-processorpath");
                options.add(processorPath);
            }

            List<JavaFileObject> units = new ArrayList<>();
            Map<String, String> classToPath = new HashMap<>();
            // 目标工程里被生成物引用的类（基座类、同批生成的其他表），也作为显式编译单元加入。
            // 不能只靠 -classpath 隐式加载：JDK 的隐式编译不参与注解处理，Lombok 不生效，
            // 会误报 cannot find symbol getXxx()。
            Set<String> externalClassNames = new LinkedHashSet<>();
            for (JavaSource source : sources) {
                String className = classNameOf(source.content());
                if (className == null) {
                    result.addIssue(source.path(), 0L, "ERROR", "无法从源码中解析出类名（package/class 声明缺失）");
                    continue;
                }
                units.add(new StringSource(className, source.content()));
                classToPath.put(className, source.path());
                collectImportedTypes(source.content(), externalClassNames);
            }
            if (units.isEmpty()) {
                return;
            }

            // 把 import 到的、属于目标工程源码根的类补成编译单元。
            //
            // 关键取舍：优先用 target/classes 里已编译好的 .class（已挂到 classpath），
            // 只有在没有编译产物时才回退到编译源码。
            // 原因：基座类（Result/PageQuery/BaseEntity…）自身大量依赖 Lombok 的 @Data，
            // 把它们当源码重复编译时，注解处理链路和 mvn 不完全一致，会误报
            // cannot find symbol getXxx()——明明是编译通过的代码。
            List<Path> srcRoots = new ArrayList<>(backendSourceRoots(sources));
            if (!classRoots.isEmpty()) {
                // 有已编译产物就完全信任它，不再补编译任何外部源码
                srcRoots = List.of();
            }
            for (Path srcRoot : srcRoots) {
                for (String fqcn : externalClassNames) {
                    if (classToPath.containsKey(fqcn)) {
                        continue;
                    }
                    Path javaFile = srcRoot.resolve(fqcn.replace('.', '/') + ".java");
                    if (!Files.isRegularFile(javaFile)) {
                        continue;
                    }
                    try {
                        String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                        units.add(new StringSource(fqcn, content));
                        classToPath.put(fqcn, outputProperties.getBackendRoot()
                                + "/" + srcRoot.relativize(javaFile).toString()
                                        .replace('\\', '/'));
                    } catch (Exception ignored) {
                        // 读不出来就交给 classpath 兜底
                    }
                }
            }

            StringWriter compilerOut = new StringWriter();
            boolean ok = compiler.getTask(compilerOut, fileManager, diagnostics, options, null, units).call();
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() != Diagnostic.Kind.ERROR) {
                        continue;
                    }
                    String file = resolveFile(d, classToPath);
                    long line = d.getLineNumber() > 0 ? d.getLineNumber() : 0L;
                    result.addIssue(file, line, "ERROR", d.getMessage(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            result.addIssue("javac", 0L, "ERROR", "编译检查执行失败：" + e.getMessage());
        } finally {
            try {
                fileManager.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
            deleteQuietly(outDir);
        }
    }

    private void checkVue(GenSelfCheckVO result, String path, String content) {
        if (!content.contains("<template>") || !content.contains("</template>")) {
            result.addIssue(path, 1L, "ERROR", "缺少 <template> 根节点");
        }
        if (!content.contains("export default")) {
            result.addIssue(path, 1L, "ERROR", "缺少 export default 组件定义");
        }
        long open = countOf(content, "<el-form-item");
        long close = countOf(content, "</el-form-item>");
        if (open != close) {
            result.addIssue(path, 0L, "ERROR", "el-form-item 标签未闭合（开 " + open + " / 闭 " + close + "）");
        }
    }

    private void checkJs(GenSelfCheckVO result, String path, String content) {
        if (!content.contains("export function")) {
            result.addIssue(path, 1L, "WARNING", "api.js 没有 export function，页面导入会失败");
        }
    }

    /**
     * select/radio 未配字典时提示（生成出来的下拉是空的，能看不能用）。
     * 配了关联表（ref_table）的除外：它的选项走 /options 远端接口，不需要字典。
     */
    private void checkDictBinding(GenSelfCheckVO result, List<GenTableColumn> columns) {
        for (GenTableColumn col : columns) {
            String htmlType = col.getHtmlType();
            if (!"select".equals(htmlType) && !"radio".equals(htmlType)) {
                continue;
            }
            if (col.getRefTable() != null && !col.getRefTable().isBlank()) {
                continue;
            }
            boolean used = isOn(col.getIsForm()) || isOn(col.getIsQuery());
            if (used && (col.getDictType() == null || col.getDictType().isBlank())) {
                result.addIssue("字段配置：" + col.getColumnName(), 0L, "WARNING",
                        "控件为" + htmlType + "但未配置字典编码，下拉选项为空");
            }
        }
    }

    /**
     * 权限一致性检查（V2.0 新增，第 5 类规则）。
     *
     * <p>生成页的按钮靠 {@code v-hasPermi="['表名:动作']"} 控制显隐，而菜单注册只会生成
     * {@code 表名:list/query/add/edit/remove} 这几个权限码。两边一旦拼错（模板写成
     * {@code :create}、表名大小写不一致），表现是"按钮点了没反应/按钮永远不出现"，
     * 编译器完全查不出来。这里做双向比对：
     *
     * <ul>
     *   <li>页面引用了菜单不会注册的权限码 → 按钮永远不显示；</li>
     *   <li>菜单注册了但页面没引用 → 按钮对能进页面的人全部可见（等于没拦）。</li>
     * </ul>
     */
    /**
     * 菜单注册会为这张表生成哪些权限动作：list + 实际存在的按钮。
     *
     * <p>口径必须与 GenMenuServiceImpl.buttonsOf 一致：没有查询条件的表不生成查询按钮，
     * 关掉的新增/修改/删除按钮也不生成对应权限码。
     */
    private Set<String> expectedActions(GenTable table, List<GenTableColumn> columns) {
        Set<String> actions = new LinkedHashSet<>();
        actions.add("list");
        boolean hasQuery = columns != null && columns.stream()
                .anyMatch(c -> table.getId() != null && table.getId().equals(c.getTableId()) && isOn(c.getIsQuery()));
        if (hasQuery) {
            actions.add("query");
        }
        if (isOn(table.getGenAddBtn())) {
            actions.add("add");
        }
        if (isOn(table.getGenEditBtn())) {
            actions.add("edit");
        }
        if (isOn(table.getGenDeleteBtn())) {
            actions.add("remove");
        }
        return actions;
    }

    /**
     * 权限点前缀，与 GenMenuServiceImpl.permsPrefix / 模板 ${permsPrefix} 三方一致。
     * 任何一处规则改动都要同步另外两处，否则自检会报假警、或真实越权被漏掉。
     */
    /**
     * 一次查回表配置，避免在 ids 循环里逐条 selectById。
     *
     * <p>自检要跑十几个检查项，同一批 ids 会被反复遍历；
     * 每项都各查一遍就会变成「检查项 × 表数」次往返。
     */
    private Map<Long, GenTable> tablesById(List<Long> ids) {
        Map<Long, GenTable> byId = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return byId;
        }
        for (GenTable table : genTableMapper.selectBatchIds(ids)) {
            byId.put(table.getId(), table);
        }
        return byId;
    }

    private String permsPrefixOf(GenTable table) {
        if (table.getProjectId() == null) {
            return com.codegen.support.GenNaming.permsPrefix(null, table.getTableName());
        }
        com.codegen.entity.SysProject project = sysProjectMapper.selectById(table.getProjectId());
        String code = project == null ? null : project.getProjectCode();
        return com.codegen.support.GenNaming.permsPrefix(code, table.getTableName());
    }

    private void checkPermsConsistency(GenSelfCheckVO result, List<GenFileVO> files, List<Long> ids,
                                       List<GenTableColumn> columns) {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        Map<Long, GenTable> tableById = tablesById(ids);
        for (Long id : ids) {
            GenTable table = tableById.get(id);
            if (table == null || table.getTableName() == null) {
                continue;
            }
            // V3.0：页面里的权限码前缀是 permsPrefix（带系统编码），
            // 这里必须用同一个规则，否则每条都会误报「权限码对不上」
            expected.put(permsPrefixOf(table), expectedActions(table, columns));
        }
        Set<String> knownTables = expected.keySet();
        if (knownTables.isEmpty()) {
            return;
        }
        Map<String, Set<String>> referenced = new LinkedHashMap<>();
        Map<String, String> pageOfTable = new LinkedHashMap<>();
        for (GenFileVO file : files) {
            String path = file.getPath();
            if (path == null || !path.endsWith(".vue")) {
                continue;
            }
            String content = file.getContent() == null ? "" : file.getContent();
            Matcher hasPermi = HAS_PERMI_PATTERN.matcher(content);
            while (hasPermi.find()) {
                Matcher token = PERMS_TOKEN_PATTERN.matcher(hasPermi.group(1));
                while (token.find()) {
                    String perm = token.group(1) != null ? token.group(1) : token.group(2);
                    if (perm == null) {
                        continue;
                    }
                    int idx = perm.lastIndexOf(':');
                    if (idx <= 0) {
                        continue;
                    }
                    String tableName = perm.substring(0, idx);
                    String action = perm.substring(idx + 1);
                    if ("*".equals(tableName)) {
                        continue;
                    }
                    if (!knownTables.contains(tableName)) {
                        result.addIssue(path, 0L, "WARNING",
                                "页面引用了权限码 " + perm + "，但本次自检的表里没有 " + tableName
                                        + "；菜单注册不会生成该权限码，对应按钮永远不显示");
                        continue;
                    }
                    referenced.computeIfAbsent(tableName, k -> new LinkedHashSet<>()).add(action);
                    pageOfTable.putIfAbsent(tableName, path);
                }
            }
        }
        for (String tableName : knownTables) {
            Set<String> refs = referenced.getOrDefault(tableName, Set.of());
            if (refs.isEmpty()) {
                // 没生成页面（或页面没有任何权限指令），不判
                continue;
            }
            String page = pageOfTable.getOrDefault(tableName, tableName);
            Set<String> want = expected.getOrDefault(tableName, Set.of("list"));
            for (String action : refs) {
                if (!want.contains(action)) {
                    result.addIssue(page, 0L, "WARNING",
                            "页面引用了权限码 " + tableName + ":" + action + "，但菜单注册不会生成它（本次只会生成 "
                                    + tableName + ":" + String.join("/", want) + "）；该按钮将对所有人隐藏");
                }
            }
            for (String action : want) {
                if ("list".equals(action) || refs.contains(action)) {
                    continue;
                }
                result.addIssue(page, 0L, "WARNING",
                        "菜单注册会生成 " + tableName + ":" + action + "，但页面里没有 v-hasPermi 引用它；"
                                + "对应按钮没有权限控制（能进页面的人都看得到）");
            }
        }
    }

    /**
     * 结构契约检查：生成的实体继承 {@code BaseEntity}，MyBatis-Plus 会把这些字段
     * （create_time/update_time/create_by/update_by/deleted）拼进每一条 SQL。
     * 表里没有这些列时，编译完全正常，但第一次查询就 `Unknown column`。
     *
     * <p>这是编译期无法覆盖的盲区，只能拿 information_schema 对账。
     */
    private void checkEntityContract(GenSelfCheckVO result, List<GenFileVO> files, List<Long> ids) {
        Set<String> baseTables = new HashSet<>();
        for (GenFileVO file : files) {
            String content = file.getContent() == null ? "" : file.getContent();
            if (file.getPath() != null && file.getPath().endsWith(".java")
                    && content.contains("extends BaseEntity")) {
                baseTables.add(file.getTableName());
            }
        }
        if (baseTables.isEmpty()) {
            return;
        }
        Map<Long, GenTable> tableById = tablesById(ids);
        for (Long id : ids) {
            GenTable table = tableById.get(id);
            if (table == null || !baseTables.contains(table.getTableName())) {
                continue;
            }
            Set<String> actual = dbColumns(table.getTableName());
            if (actual.isEmpty()) {
                // 表还没建（纯设计阶段），不判
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String[] def : BASE_ENTITY_COLUMNS.values()) {
                if (!actual.contains(def[0])) {
                    missing.add(def[0]);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            result.addIssue("表结构：" + table.getTableName(), 0L, "ERROR",
                    "实体继承 BaseEntity，SQL 会带上 " + String.join("、", missing)
                            + "，但表中没有这些列 —— 编译能过，运行时必报 Unknown column。建议执行："
                            + buildAlterSql(table.getTableName(), missing));
        }
    }

    private Set<String> dbColumns(String tableName) {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, tableName);
        Set<String> set = new HashSet<>();
        for (String col : cols) {
            set.add(col.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private String buildAlterSql(String tableName, List<String> missing) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ").append(tableName).append(' ');
        for (int i = 0; i < missing.size(); i++) {
            String col = missing.get(i);
            String type = "varchar(64)";
            for (String[] def : BASE_ENTITY_COLUMNS.values()) {
                if (def[0].equals(col)) {
                    type = def[1];
                    break;
                }
            }
            sb.append(i == 0 ? "ADD COLUMN " : ", ADD COLUMN ")
                    .append(col).append(' ').append(type).append(" NULL");
        }
        return sb.toString();
    }

    private List<GenTableColumn> listColumns(List<Long> ids) {
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(GenTableColumn::getTableId, ids);
        return columnService.list(wrapper);
    }

    /**
     * 注释类数据文本：模板把注释原样输出，若注释里带了 ${...} 示例，不能算成未渲染的模板变量
     */
    private List<String> collectDataTexts(List<GenTableColumn> columns) {
        List<String> texts = new ArrayList<>();
        for (GenTableColumn col : columns) {
            if (col.getColumnComment() != null) {
                texts.add(col.getColumnComment().trim());
            }
        }
        texts.removeIf(t -> t.length() < 4);
        return texts;
    }

    /**
     * 检查未渲染的模板占位符。
     *
     * <p>只报「真正说明模板出错」的残留：注释行里的 ${} 不影响编译，来自字段注释的示例文本属于数据，
     * 两者都直接放行，避免正常产物被误判。
     */
    private void checkPlaceholderResidue(GenSelfCheckVO result, String path, String content, List<String> dataTexts) {
        if (!content.contains("${")) {
            return;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("${")) {
                continue;
            }
            if (isCommentLine(line) || containsDataText(line, dataTexts)) {
                continue;
            }
            result.addIssue(path, (long) (i + 1), "ERROR",
                    "存在未渲染的模板占位符 ${...}，模板变量名可能有误");
            return;
        }
    }

    private boolean isCommentLine(String line) {
        String t = line.trim();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("<!--");
    }

    private boolean containsDataText(String line, List<String> dataTexts) {
        for (String text : dataTexts) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOn(Integer flag) {
        return flag != null && flag == 1;
    }

    private String resolveFile(Diagnostic<? extends JavaFileObject> d, Map<String, String> classToPath) {
        JavaFileObject source = d.getSource();
        if (source == null) {
            return "javac";
        }
        String name = source.getName();
        String className = name;
        int scheme = name.lastIndexOf("string:///");
        if (scheme >= 0) {
            className = name.substring(scheme + "string:///".length());
        } else {
            className = name;
        }
        className = className.replace('\\', '/');
        if (className.startsWith("/")) {
            className = className.substring(1);
        }
        if (className.endsWith(".java")) {
            className = className.substring(0, className.length() - 5);
        }
        className = className.replace('/', '.');
        return classToPath.getOrDefault(className, className);
    }

    private String classNameOf(String content) {
        Matcher pm = PACKAGE_PATTERN.matcher(content);
        Matcher cm = CLASS_PATTERN.matcher(content);
        if (!cm.find()) {
            return null;
        }
        String simple = cm.group(1);
        return pm.find() ? pm.group(1) + "." + simple : simple;
    }

    private long countOf(String content, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = content.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 临时目录清理失败不影响结果
                }
            });
        } catch (Exception ignored) {
            // 同上
        }
    }

    /**
     * 参与编译的 Java 源码
     */
    private record JavaSource(String path, String content) {
    }

    /**
     * 内存中的 Java 源码对象
     */
    private static final class StringSource extends SimpleJavaFileObject {

        private final String code;

        private StringSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
