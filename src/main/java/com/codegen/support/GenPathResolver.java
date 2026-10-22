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
}
