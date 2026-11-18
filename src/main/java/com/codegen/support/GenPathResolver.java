package com.codegen.support;

import com.codegen.config.GenOutputProperties;
import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import org.springframework.util.StringUtils;

/**
 * 生成物落盘路径解析器：把「表级 → 系统 → 目标模块 → 全局」四级回落收敛到一处。
 *
 * <p>为什么单独抽出来：V3.0 出过一次「预览路径正确、文件写进别的仓库」的事故——
 * 模板渲染层接了系统配置，写盘层忘了接，两份代码各推一次路径。
 * 现在所有路径只有一个实现，预览、写盘、差异对比、ZIP 打包都调这里，
 * 想再出现两边不一致，除非有人绕过本类自己拼路径。
 *
 * <p>本类是纯函数：不读库、不写库、不改出入参。项目信息由调用方查好传进来，
 * 因此可以直接用 JUnit 断言四级回落，不需要启动 Spring。
 *
 * <p>回落顺序（从最具体到最通用）：
 * <pre>
 *   表级字段 → 所属系统(sys_project) → 目标模块(gen.output.modules) → 全局(gen.output)
 * </pre>
 * 表级必须最优先，否则用户对单张表的手工微调会被系统配置盖掉。
 */
public final class GenPathResolver {

    private GenPathResolver() {
    }

    /**
     * 表配置的目标模块，未配置或未命中返回 null。
     */
    public static GenOutputProperties.ModuleConfig moduleOf(GenTable table, GenOutputProperties output) {
        if (table == null || output == null || output.getModules() == null) {
            return null;
        }
        String key = table.getTargetModule();
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return output.getModules().get(key.trim());
    }

    /**
     * 后端仓库根目录：表级 → 系统 → 目标模块 → 全局。
     */
    public static String backendRoot(GenTable table, SysProject project, GenOutputProperties output) {
        if (table != null && StringUtils.hasText(table.getBackendRoot())) {
            return table.getBackendRoot().trim();
        }
        if (project != null && StringUtils.hasText(project.getBackendRoot())) {
            return project.getBackendRoot().trim();
        }
        GenOutputProperties.ModuleConfig module = moduleOf(table, output);
        if (module != null && StringUtils.hasText(module.getBackendRoot())) {
            return module.getBackendRoot().trim();
        }
        return output == null || output.getBackendRoot() == null ? "" : output.getBackendRoot().trim();
    }

    /**
     * 前端仓库根目录：表级 → 系统 → 全局。
     *
     * <p>模块级没有独立的 frontend-root（一个模块换整个前端仓库的场景不存在），
     * 所以这里不查 modules。
     */
    public static String frontendRoot(GenTable table, SysProject project, GenOutputProperties output) {
        if (table != null && StringUtils.hasText(table.getFrontendRoot())) {
            return table.getFrontendRoot().trim();
        }
        if (project != null && StringUtils.hasText(project.getFrontendRoot())) {
            return project.getFrontendRoot().trim();
        }
        return output == null || output.getFrontendRoot() == null ? "" : output.getFrontendRoot().trim();
    }

    /**
     * 后端相对路径前缀（相对 backendRoot），如 {@code demo-server/src/main/java/com/demo}。
     *
     * <p>四级都为空时按包名推 {@code src/main/java/com/x}，宁可推出一个可读的路径，
     * 也不让写盘拿到空串把文件写到根目录。
     */
    public static String backendBase(GenTable table, SysProject project, GenOutputProperties output) {
        if (table != null && StringUtils.hasText(table.getBackendPath())) {
            return table.getBackendPath().trim();
        }
        if (project != null && StringUtils.hasText(project.getBackendPath())) {
            return project.getBackendPath().trim();
        }
        GenOutputProperties.ModuleConfig module = moduleOf(table, output);
        if (module != null && StringUtils.hasText(module.getBackendPath())) {
            return module.getBackendPath().trim();
        }
        if (output != null && StringUtils.hasText(output.getBackendPath())) {
            return output.getBackendPath().trim();
        }
        String pkg = packageName(table, project, output);
        return "src/main/java/" + pkg.replace('.', '/');
    }

    /**
     * 包名：表级 → 系统根包 → 目标模块 → 全局。
     */
    public static String packageName(GenTable table, SysProject project, GenOutputProperties output) {
        if (table != null && StringUtils.hasText(table.getPackageName())) {
            return table.getPackageName().trim();
        }
        if (project != null && StringUtils.hasText(project.getRootPackage())) {
            return project.getRootPackage().trim();
        }
        GenOutputProperties.ModuleConfig module = moduleOf(table, output);
        if (module != null && StringUtils.hasText(module.getPackageName())) {
            return module.getPackageName().trim();
        }
        return output == null || output.getPackageName() == null ? "com.codegen" : output.getPackageName().trim();
    }

    /**
     * 网关前缀：表级 → 系统 → 目标模块 → 全局。
     */
    public static String gatewayPrefix(GenTable table, SysProject project, GenOutputProperties output) {
        if (table != null && StringUtils.hasText(table.getGatewayPrefix())) {
            return table.getGatewayPrefix().trim();
        }
        if (project != null && StringUtils.hasText(project.getGatewayPrefix())) {
            return project.getGatewayPrefix().trim();
        }
        GenOutputProperties.ModuleConfig module = moduleOf(table, output);
        if (module != null && StringUtils.hasText(module.getGatewayPrefix())) {
            return module.getGatewayPrefix().trim();
        }
        return output == null || output.getGatewayPrefix() == null ? "" : output.getGatewayPrefix().trim();
    }

    /**
     * 前端工程相对子目录：系统 → 目标模块 → 全局，都没有返回空串（前端工程就在 frontendRoot）。
     *
     * <p>表级没有 frontendPath 字段——一张表不可能换前端工程，第一个有资格说话的就是系统。
     */
    public static String frontendPath(GenTable table, SysProject project, GenOutputProperties output) {
        if (project != null && StringUtils.hasText(project.getFrontendPath())) {
            return project.getFrontendPath().trim();
        }
        GenOutputProperties.ModuleConfig module = moduleOf(table, output);
        if (module != null && StringUtils.hasText(module.getFrontendPath())) {
            return module.getFrontendPath().trim();
        }
        return output != null && StringUtils.hasText(output.getFrontendPath())
                ? output.getFrontendPath().trim()
                : "";
    }

    /* =========================================================
     * V5.5 多模块工程（project_structure = 'multi'）
     *
     * 模块拆分线即未来微服务拆分线：domain（entity+mapper）、service
     * （controller+service+dto/vo）、common（基座）、bootstrap（启动壳）。
     * 归属规则只有这一份实现；预览（GenTemplateEngine.put 加前缀）、
     * 写盘（GenCodeServiceImpl 判定后端文件）都调这里，不许各自判断。
     * ========================================================= */

    /**
     * 是否多模块工程：工程结构显式为 "multi" 才算，null/其他一律单模块。
     *
     * <p>吃 String 而不是 SysProject：调用方两侧——脚手架（GenScaffoldDTO，建工程时
     * 还没有 SysProject 记录）与模板引擎（GenTemplateModel.ProjectConfig，util 层
     * 刻意不依赖 entity）——手里都只有字符串。
     */
    public static boolean isMulti(String projectStructure) {
        return projectStructure != null && "multi".equalsIgnoreCase(projectStructure.trim());
    }

    /**
     * 是否微服务设施工程：工程结构显式为 "micro" 才算（V6.0 第三态）。
     *
     * <p>micro 在 multi 四模块上追加 gateway 模块与 Nacos/OpenFeign 设施，
     * 但产物（entity/mapper/controller/...）的模块归属与 multi 完全一致。
     */
    public static boolean isMicro(String projectStructure) {
        return projectStructure != null && "micro".equalsIgnoreCase(projectStructure.trim());
    }

    /**
     * 多模块或微服务设施：产物需要按模块分层落盘的两种结构。
     * 只用于「产物归属」判断，不要拿它决定 pom/依赖——那是脚手架自己的事。
     */
    public static boolean isMultiLike(String projectStructure) {
        return isMulti(projectStructure) || isMicro(projectStructure);
    }

    /**
     * 模板编码 → 模块归属。返回 null 表示该产物不属于任何后端子模块
     * （前端产物 apiJs/vuePage/printPage），多模块时原样落 backendRoot。
     */
    public static String moduleOfTpl(String tplCode) {
        if (tplCode == null) {
            return null;
        }
        // serviceImpl* 前缀判断必须在 service 之前（serviceImplSimple 也归 service）
        if (tplCode.equals("entity") || tplCode.equals("mapper")) {
            return "domain";
        }
        if (tplCode.equals("controller") || tplCode.equals("service")
                || tplCode.startsWith("serviceImpl")
                || tplCode.equals("pageDto") || tplCode.equals("saveDto") || tplCode.equals("vo")) {
            return "service";
        }
        return null;
    }

    /**
     * 产物相对路径是否属于后端（main 与 test 两侧都要认）。
     *
     * <p>{@code backendBase} 只覆盖 {@code src/main/java/<pkg>}，测试产物的基路径是
     * {@code src/test/java/<pkg>}（见 GenTemplateModel 的 backendTestBase 推导），两者字符串不同。
     * 早先只判 {@code relPath.contains(backendBase + "/")}，测试类与 schema 资源因此被判成前端产物，
     * 写进前端仓库的模块目录下（V6.8 S8 实测：{@code demo-web/demo-server/src/test/...}）。
     */
    public static boolean isBackendRelPath(String relPath, String backendBase) {
        if (relPath == null || relPath.isEmpty()) {
            return false;
        }
        if (StringUtils.hasText(backendBase)) {
            if (relPath.startsWith(backendBase + "/") || relPath.contains(backendBase + "/")) {
                return true;
            }
            // 多模块：模块目录在前，其后紧跟 src/main 或 src/test 都算后端
            int srcIdx = backendBase.indexOf("/src/");
            String modulePrefix = srcIdx > 0 ? backendBase.substring(0, srcIdx) : "";
            if (!modulePrefix.isEmpty()
                    && (relPath.startsWith(modulePrefix + "/src/main/")
                    || relPath.startsWith(modulePrefix + "/src/test/"))) {
                return true;
            }
        }
        // 单模块：后端路径直接从 src/main|src/test 起头（前端是 src/api、src/views…）
        return relPath.startsWith("src/main/") || relPath.startsWith("src/test/");
    }

    /**
     * 多模块时的相对路径前缀：{@code <stem>-<module>/}，插在产物 relPath 最前面
     * （backendBase 是包路径 src/main/java/...，模块目录在其工程根一层）。
     * 单模块、非后端产物（moduleOfTpl 为 null）、stem 为空时返回空串。
     */
    public static String moduleRelPrefix(String tplCode, String projectStructure, String rootPackage) {
        if (!isMultiLike(projectStructure)) {
            return "";
        }
        String module = moduleOfTpl(tplCode);
        if (module == null) {
            return "";
        }
        String stem = GenNaming.multiModuleStem(rootPackage);
        return stem.isEmpty() ? "" : stem + "-" + module + "/";
    }
}
