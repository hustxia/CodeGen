package com.codegen.support;

import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 17 个生成开关的「系统默认值」。
 *
 * <p>为什么需要：{@code gen_table} 的 17 个开关都是 {@code NOT NULL DEFAULT 1}，
 * 意味着每张新表默认全量生成。如果某套系统整体不需要前端、或者不需要操作日志，
 * 旧做法是一张表一张表去点 17 个开关——表一多就容易漏。
 *
 * <p>回落顺序：<b>表级 → 系统 → 硬编码 1</b>。
 * 注意这里是「导入/新建时的一次性取值」，不是生成时动态回落：
 * 生成时必须只读表级字段（用户可能事后单独调过），若在生成时再回落一次，
 * 用户手动关掉的开关会被系统默认值静默顶回去。
 *
 * <p>因此系统默认值只在两个时机写入表级：新表落地、用户点「恢复系统默认」。
 */
public final class GenSwitchDefaults {

    /**
     * 开关字段名，顺序必须与 {@link #of(SysProject, int)} 的索引一致。
     * 前端「恢复系统默认」和「默认值提示」都按这些 key 取值。
     */
    public static final String[] KEYS = {
            "genEntity", "genMapper", "genService", "genLog", "genController",
            "genPageApi", "genDetailApi", "genAddApi", "genEditApi", "genDeleteApi",
            "genFrontend", "genApiJs", "genVuePage", "genQueryForm",
            "genAddBtn", "genEditBtn", "genDeleteBtn"
    };

    private GenSwitchDefaults() {
    }

    /**
     * 单个开关取值：表级有值用表级，否则系统，都没有用 1。
     */
    public static int resolve(Integer tableValue, Integer projectValue) {
        if (tableValue != null) {
            return tableValue;
        }
        if (projectValue != null) {
            return projectValue;
        }
        return 1;
    }

    /**
     * 只补空开关（新建 / 导入时用）。
     *
     * <p>已有值不覆盖：导入 DDL 或手工新建时，用户可能已经在弹窗里点过预设，
     * 那是明确意图，不能被系统默认值冲掉。
     */
    public static void fillMissing(GenTable table, SysProject project) {
        if (table == null) {
            return;
        }
        table.setGenEntity(resolve(table.getGenEntity(), of(project, 0)));
        table.setGenMapper(resolve(table.getGenMapper(), of(project, 1)));
        table.setGenService(resolve(table.getGenService(), of(project, 2)));
        table.setGenLog(resolve(table.getGenLog(), of(project, 3)));
        table.setGenController(resolve(table.getGenController(), of(project, 4)));
        table.setGenPageApi(resolve(table.getGenPageApi(), of(project, 5)));
        table.setGenDetailApi(resolve(table.getGenDetailApi(), of(project, 6)));
        table.setGenAddApi(resolve(table.getGenAddApi(), of(project, 7)));
        table.setGenEditApi(resolve(table.getGenEditApi(), of(project, 8)));
        table.setGenDeleteApi(resolve(table.getGenDeleteApi(), of(project, 9)));
        table.setGenFrontend(resolve(table.getGenFrontend(), of(project, 10)));
        table.setGenApiJs(resolve(table.getGenApiJs(), of(project, 11)));
        table.setGenVuePage(resolve(table.getGenVuePage(), of(project, 12)));
        table.setGenQueryForm(resolve(table.getGenQueryForm(), of(project, 13)));
        table.setGenAddBtn(resolve(table.getGenAddBtn(), of(project, 14)));
        table.setGenEditBtn(resolve(table.getGenEditBtn(), of(project, 15)));
        table.setGenDeleteBtn(resolve(table.getGenDeleteBtn(), of(project, 16)));
    }

    /**
     * 全部重设为系统默认值（前端「恢复系统默认」按钮用）。
     */
    public static void resetToProject(GenTable table, SysProject project) {
        if (table == null) {
            return;
        }
        table.setGenEntity(resolve(null, of(project, 0)));
        table.setGenMapper(resolve(null, of(project, 1)));
        table.setGenService(resolve(null, of(project, 2)));
        table.setGenLog(resolve(null, of(project, 3)));
        table.setGenController(resolve(null, of(project, 4)));
        table.setGenPageApi(resolve(null, of(project, 5)));
        table.setGenDetailApi(resolve(null, of(project, 6)));
        table.setGenAddApi(resolve(null, of(project, 7)));
        table.setGenEditApi(resolve(null, of(project, 8)));
        table.setGenDeleteApi(resolve(null, of(project, 9)));
        table.setGenFrontend(resolve(null, of(project, 10)));
        table.setGenApiJs(resolve(null, of(project, 11)));
        table.setGenVuePage(resolve(null, of(project, 12)));
        table.setGenQueryForm(resolve(null, of(project, 13)));
        table.setGenAddBtn(resolve(null, of(project, 14)));
        table.setGenEditBtn(resolve(null, of(project, 15)));
        table.setGenDeleteBtn(resolve(null, of(project, 16)));
    }

    /**
     * 系统默认值的只读视图（已按「系统 → 硬编码 1」解析），key 与实体字段同名。
     */
    public static Map<String, Integer> defaultsOf(SysProject project) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < KEYS.length; i++) {
            map.put(KEYS[i], resolve(null, of(project, i)));
        }
        return map;
    }

    /**
     * 按索引取系统配置值。用 switch 而不是反射，字段对应关系编译期就能看出来，
     * 加开关时漏了哪一处也一目了然。
     */
    private static Integer of(SysProject project, int index) {
        if (project == null) {
            return null;
        }
        return switch (index) {
            case 0 -> project.getDefaultGenEntity();
            case 1 -> project.getDefaultGenMapper();
            case 2 -> project.getDefaultGenService();
            case 3 -> project.getDefaultGenLog();
            case 4 -> project.getDefaultGenController();
            case 5 -> project.getDefaultGenPageApi();
            case 6 -> project.getDefaultGenDetailApi();
            case 7 -> project.getDefaultGenAddApi();
            case 8 -> project.getDefaultGenEditApi();
            case 9 -> project.getDefaultGenDeleteApi();
            case 10 -> project.getDefaultGenFrontend();
            case 11 -> project.getDefaultGenApiJs();
            case 12 -> project.getDefaultGenVuePage();
            case 13 -> project.getDefaultGenQueryForm();
            case 14 -> project.getDefaultGenAddBtn();
            case 15 -> project.getDefaultGenEditBtn();
            case 16 -> project.getDefaultGenDeleteBtn();
            default -> null;
        };
    }
}
