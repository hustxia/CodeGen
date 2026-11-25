package com.codegen.support;

import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenPipelineAiVO.Column;
import com.codegen.vo.GenPipelineAiVO.TableModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * V9.19 模板包校验（纯函数，无 Spring 依赖，便于单测）。
 *
 * <p>一次返回<b>全部</b>错误而不是只报第一条，导入失败时前端可以逐条展示，
 * 用户改完一轮再传即可，避免"修一个报一个"的往返。
 *
 * <p>表名/列名与后端既有标识符口径一致：{@code ^[a-z][a-z0-9_]{0,62}$}；
 * 模板 key 额外收紧到可配置长度（默认 48），因为它同时是文件名与接口入参。
 */
public final class TemplatePackValidator {

    /** 与后端既有建表标识符正则一致 */
    public static final String IDENTIFIER_REGEX = "^[a-z][a-z0-9_]{0,62}$";

    /** 模板 key：小写字母开头 + 小写字母/数字/下划线，长度由 Limits 控制 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(IDENTIFIER_REGEX);

    /** 单次校验最多收集的错误条数，避免超大包刷出几千条错误 */
    private static final int MAX_ERRORS = 100;

    private TemplatePackValidator() {
    }

    /** 校验口径：表数量、key 长度、包字节数 */
    public record Limits(int maxTables, int maxKeyLength, long maxPackBytes) {
    }

    public record Result(List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** 单包大小校验（在读取/解压前后都能复用） */
    public static Result validateSize(long bytes, Limits limits) {
        List<String> errors = new ArrayList<>();
        if (limits.maxPackBytes() > 0 && bytes > limits.maxPackBytes()) {
            errors.add("模板包过大：" + bytes + " 字节，超过上限 "
                    + limits.maxPackBytes() + " 字节（" + (limits.maxPackBytes() / 1024 / 1024) + "MB）");
        }
        return new Result(errors);
    }

    /**
     * 校验单个模板结构。
     *
     * <p>必填：key / name / category / modules[].tables[]；
     * 每张表必须有 tableName 与至少一列，每列必须有 name 与 type。
     */
    public static Result validate(GenAiTemplateVO vo, Limits limits) {
        List<String> errors = new ArrayList<>();
        if (vo == null) {
            errors.add("模板内容为空，无法解析");
            return new Result(errors);
        }

        String key = trim(vo.getKey());
        if (key.isEmpty()) {
            errors.add("缺少模板 key（key 必填）");
        } else if (!KEY_PATTERN.matcher(key).matches()) {
            errors.add("模板 key 不合法：" + key + "（仅小写字母/数字/下划线，且以小写字母开头）");
        } else if (key.length() > limits.maxKeyLength()) {
            errors.add("模板 key 过长：" + key + "（最多 " + limits.maxKeyLength() + " 个字符）");
        }

        if (trim(vo.getName()).isEmpty()) {
            errors.add("缺少模板 name（name 必填）");
        }
        if (trim(vo.getCategory()).isEmpty()) {
            errors.add("缺少模板 category（category 必填）");
        }
        if (vo.getModules() == null || vo.getModules().isEmpty()) {
            errors.add("模板 modules 不能为空（至少一个模块）");
            return new Result(errors);
        }

        int tableCount = 0;
        Set<String> tableNames = new HashSet<>();
        Set<String> moduleNames = new HashSet<>();
        boolean tableLimitReported = false;

        for (int mi = 0; mi < vo.getModules().size(); mi++) {
            GenAiTemplateVO.Module module = vo.getModules().get(mi);
            String moduleLabel = "第 " + (mi + 1) + " 个模块";
            if (module == null) {
                addError(errors, moduleLabel + "为空");
                continue;
            }
            String moduleName = trim(module.getName());
            if (moduleName.isEmpty()) {
                addError(errors, moduleLabel + "缺少 name");
                moduleLabel = moduleLabel + "（未命名）";
            } else {
                moduleLabel = "模块 " + moduleName;
                if (!moduleNames.add(moduleName)) {
                    addError(errors, "模块名重复：" + moduleName);
                }
            }
            if (module.getTables() == null || module.getTables().isEmpty()) {
                addError(errors, moduleLabel + "没有表（tables 不能为空）");
                continue;
            }

            for (int ti = 0; ti < module.getTables().size(); ti++) {
                TableModel table = module.getTables().get(ti);
                String tableLabel = moduleLabel + " 第 " + (ti + 1) + " 张表";
                tableCount++;
                if (!tableLimitReported && tableCount > limits.maxTables()) {
                    addError(errors, "表数量超限：" + tableCount + " > " + limits.maxTables());
                    tableLimitReported = true;
                }
                if (table == null) {
                    addError(errors, tableLabel + "为空");
                    continue;
                }
                String tableName = trim(table.getTableName());
                if (tableName.isEmpty()) {
                    addError(errors, tableLabel + "缺少 tableName");
                } else if (!IDENTIFIER_PATTERN.matcher(tableName).matches()) {
                    addError(errors, "表名不合法：" + tableName + "（" + IDENTIFIER_REGEX + "）");
                } else if (!tableNames.add(tableName)) {
                    addError(errors, "表名重复：" + tableName);
                }
                if (table.getColumns() == null || table.getColumns().isEmpty()) {
                    addError(errors, tableLabel + "没有列（columns 不能为空）");
                    continue;
                }
                Set<String> columnNames = new HashSet<>();
                String columnOwner = tableName.isEmpty() ? tableLabel : "表 " + tableName;
                for (int ci = 0; ci < table.getColumns().size(); ci++) {
                    Column column = table.getColumns().get(ci);
                    String columnLabel = columnOwner + " 第 " + (ci + 1) + " 列";
                    if (column == null) {
                        addError(errors, columnLabel + "为空");
                        continue;
                    }
                    String columnName = trim(column.getName());
                    if (columnName.isEmpty()) {
                        addError(errors, columnLabel + "缺少 name");
                    } else if (!IDENTIFIER_PATTERN.matcher(columnName).matches()) {
                        addError(errors, columnOwner + " 列名不合法：" + columnName + "（" + IDENTIFIER_REGEX + "）");
                    } else if (!columnNames.add(columnName)) {
                        addError(errors, columnOwner + " 列名重复：" + columnName);
                    }
                    if (trim(column.getType()).isEmpty()) {
                        addError(errors, columnOwner + " 列 " + (columnName.isEmpty() ? columnLabel : columnName)
                                + " 缺少 type");
                    }
                }
            }
        }
        if (tableCount == 0 && errors.isEmpty()) {
            errors.add("模板没有任何表");
        }
        return new Result(errors);
    }

    private static void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("…错误过多，已截断（仅展示前 " + MAX_ERRORS + " 条）");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
