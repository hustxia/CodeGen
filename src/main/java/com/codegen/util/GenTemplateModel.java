package com.codegen.util;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 模板渲染模型构建：把表配置与字段配置展平为 Freemarker 可用的 Map 模型
 */
public final class GenTemplateModel {

    /**
     * 生成物引用基座类时使用的根包名（如 com.demo → com.demo.common.result.Result）。
     *
     * <p>旧工程把生成器写在目标工程里，模板直接 import com.shop.common.*；
     * 独立成平台后目标工程千差万别，改为由 gen.output.root-package 注入。
     */
    private static volatile String rootPkg = "com.demo";

    private GenTemplateModel() {
    }

    /**
     * 由 GenTemplateEngine 在启动时按配置注入（无配置时保持 com.demo）
     */
    public static void setRootPkg(String value) {
        if (StringUtils.hasText(value)) {
            rootPkg = value;
        }
    }

    /**
     * 当前生效的基座根包名
     */
    public static String rootPkg() {
        return rootPkg;
    }

    /**
     * 业务字段：排除主键与 BaseEntity 统一字段（与二期 GenTemplates.bizColumns 一致）
     */
    public static List<GenTableColumn> bizColumns(List<GenTableColumn> cols) {
        List<String> base = List.of("id", "deleted", "create_by", "create_time", "update_by", "update_time");
        List<GenTableColumn> result = new ArrayList<>();
        for (GenTableColumn c : cols) {
            if (on(c.getIsPk()) || base.contains(c.getColumnName())) {
                continue;
            }
            result.add(c);
        }
        return result;
    }

    /**
     * 主子表：子表配置（表 + 字段）
     */
    public record ChildInfo(GenTable table, List<GenTableColumn> cols) {
    }

    /**
     * 构建完整渲染模型（无子表）
     */
    public static Map<String, Object> build(GenTable table, List<GenTableColumn> cols) {
        return build(table, cols, null);
    }

    /**
     * 构建完整渲染模型
     *
     * @param child 本表作为主表时的子表信息，可为 null
     */
    public static Map<String, Object> build(GenTable table, List<GenTableColumn> cols, ChildInfo child) {
        String pkg = orDefault(table.getPackageName(), "com.demo");
        String className = table.getClassName();
        String module = orDefault(table.getModuleName(), "");
        String prefix = orDefault(table.getGatewayPrefix(), "demo");
        String backendBase = orDefault(table.getBackendPath(), "src/main/java/" + pkg.replace('.', '/'));
        List<GenTableColumn> biz = bizColumns(cols == null ? List.of() : cols);

        boolean genFrontend = on(table.getGenFrontend());
        boolean genPageApi = on(table.getGenPageApi());
        boolean genDetailApi = on(table.getGenDetailApi());
        boolean genAddApi = on(table.getGenAddApi());
        boolean genEditApi = on(table.getGenEditApi());
        boolean genDeleteApi = on(table.getGenDeleteApi());
        boolean genService = on(table.getGenService());
        boolean genQueryForm = on(table.getGenQueryForm());
        boolean genAddBtn = genFrontend && on(table.getGenAddBtn()) && genAddApi;
        boolean genEditBtn = genFrontend && on(table.getGenEditBtn()) && genEditApi;
        boolean genDeleteBtn = genFrontend && on(table.getGenDeleteBtn()) && genDeleteApi;

        List<GenTableColumn> queryCols = biz.stream().filter(c -> on(c.getIsQuery())).toList();
        List<GenTableColumn> vueQueryCols = biz.stream().filter(c -> genQueryForm && on(c.getIsQuery())).toList();
        List<GenTableColumn> listCols = biz.stream().filter(c -> on(c.getIsList())).toList();
        List<GenTableColumn> formCols = biz.stream().filter(c -> on(c.getIsForm())).toList();

        Map<String, Object> m = new HashMap<>();
        m.put("pkg", pkg);
        m.put("rootPkg", rootPkg);
        m.put("backendBase", backendBase);
        m.put("className", className);
        m.put("varName", decap(className));
        m.put("tableName", table.getTableName());
        m.put("comment", orDefault(table.getTableComment(), className));
        m.put("moduleName", module);
        m.put("gatewayPrefix", prefix);
        m.put("author", orDefault(table.getAuthor(), ""));
        m.put("apiBase", "/api/" + prefix + "/" + module);

        // 原始开关
        m.put("genEntity", on(table.getGenEntity()));
        m.put("genMapper", on(table.getGenMapper()));
        m.put("genService", genService);
        m.put("genController", on(table.getGenController()));
        m.put("genPageApi", genPageApi);
        m.put("genDetailApi", genDetailApi);
        m.put("genAddApi", genAddApi);
        m.put("genEditApi", genEditApi);
        m.put("genDeleteApi", genDeleteApi);
        m.put("genQueryForm", genQueryForm);
        // 前端有效开关
        m.put("genAddBtn", genAddBtn);
        m.put("genEditBtn", genEditBtn);
        m.put("genDeleteBtn", genDeleteBtn);
        m.put("hasDialog", genAddBtn || genEditBtn);
        // Controller 有效开关（genService 门控）
        m.put("ctrlPageApi", genService && genPageApi);
        m.put("ctrlDetailApi", genService && genDetailApi);
        m.put("ctrlAddApi", genService && genAddApi);
        m.put("ctrlEditApi", genService && genEditApi);
        m.put("ctrlDeleteApi", genService && genDeleteApi);
        m.put("ctrlAnyApi", genService && (genPageApi || genDetailApi || genAddApi || genEditApi || genDeleteApi));

        // 列集合
        m.put("columns", toModel(biz));
        m.put("queryColumns", toModel(queryCols));
        m.put("vueQueryColumns", toModel(vueQueryCols));
        m.put("listColumns", toModel(listCols));
        m.put("formColumns", toModel(formCols));

        // 校验注解导入（SaveDTO）与 Controller 的 @Valid 开关
        java.util.Set<String> validImports = new java.util.LinkedHashSet<>();
        boolean anyValidation = false;
        for (GenTableColumn c : formCols) {
            String rule = c.getValidateRule();
            if (rule != null && !rule.isBlank()) {
                anyValidation = true;
                validImports.addAll(GenValidateUtils.javaImports(c.getJavaType(), rule));
            }
        }
        m.put("validImports", new ArrayList<>(validImports));
        m.put("saveDtoHasValidation", anyValidation);
        m.put("ctrlValid", anyValidation && (genAddApi || genEditApi));

        // 字典：表单 / 查询 / 列表用到的 dict_code 去重（页面加载时统一拉取）
        java.util.Set<String> dictCodes = new java.util.LinkedHashSet<>();
        for (GenTableColumn c : biz) {
            boolean used = on(c.getIsForm()) || on(c.getIsQuery()) || on(c.getIsList());
            if (used && c.getDictType() != null && !c.getDictType().isBlank()) {
                dictCodes.add(c.getDictType().trim());
            }
        }
        m.put("dictCodes", new ArrayList<>(dictCodes));
        m.put("vueHasDict", !dictCodes.isEmpty());

        // 导包标记
        m.put("entityHasBigDecimal", hasType(biz, "BigDecimal"));
        m.put("entityHasLocalDateTime", hasType(biz, "LocalDateTime"));
        m.put("entityHasLocalDate", hasType(biz, "LocalDate"));
        m.put("entityHasLocalTime", hasType(biz, "LocalTime"));
        m.put("entityHasImport", hasType(biz, "BigDecimal") || hasType(biz, "LocalDateTime")
                || hasType(biz, "LocalDate") || hasType(biz, "LocalTime"));
        m.put("pageDtoHasBigDecimal", hasType(biz, "BigDecimal"));
        m.put("pageDtoHasLocalDateTime", hasType(biz, "LocalDateTime"));
        m.put("pageDtoHasLocalDate", hasType(biz, "LocalDate"));
        m.put("pageDtoHasImport", hasType(biz, "BigDecimal") || hasType(biz, "LocalDateTime")
                || hasType(biz, "LocalDate"));
        m.put("saveDtoHasBigDecimal", hasType(formCols, "BigDecimal"));
        m.put("saveDtoHasLocalDateTime", hasType(formCols, "LocalDateTime"));
        m.put("saveDtoHasLocalDate", hasType(formCols, "LocalDate"));
        m.put("saveDtoHasImport", hasType(formCols, "BigDecimal") || hasType(formCols, "LocalDateTime")
                || hasType(formCols, "LocalDate"));
        m.put("listHasBigDecimal", hasType(listCols, "BigDecimal"));
        m.put("listHasLocalDateTime", hasType(listCols, "LocalDateTime"));
        m.put("listHasLocalDate", hasType(listCols, "LocalDate"));

        // 前端函数串
        StringJoiner fns = new StringJoiner(", ");
        if (genPageApi) {
            fns.add("get" + className + "List");
        }
        if (genAddApi) {
            fns.add("create" + className);
        }
        if (genEditApi) {
            fns.add("update" + className);
        }
        if (genDeleteApi) {
            fns.add("delete" + className);
        }
        m.put("vueImportFns", fns.toString());

        // ==================== V1.4：树形 / 主子表 / 关联下拉 / Excel / 详情 ====================

        // 树形：treeParentColumn 非空即按树生成
        boolean genTree = StringUtils.hasText(table.getTreeParentColumn());
        List<GenTableColumn> bizAll = biz;
        String treeLabelColumn = table.getTreeLabelColumn();
        if (!StringUtils.hasText(treeLabelColumn)) {
            treeLabelColumn = bizAll.stream()
                    .filter(c -> "String".equals(c.getJavaType()))
                    .map(GenTableColumn::getColumnName)
                    .findFirst().orElse(null);
        }
        m.put("genTree", genTree);
        m.put("treeParentColumn", genTree ? table.getTreeParentColumn() : "");
        m.put("treeParentField", genTree ? GenUtils.toCamel(table.getTreeParentColumn()) : "");
        m.put("treeParentCapField", genTree
                ? capitalize(GenUtils.toCamel(table.getTreeParentColumn())) : "");
        m.put("treeLabelColumn", treeLabelColumn == null ? "" : treeLabelColumn);
        m.put("treeLabelField", treeLabelColumn == null ? "" : GenUtils.toCamel(treeLabelColumn));
        // VO 里是否已有父级字段（没有则模板单独补一个，树形接口要用）
        GenTableColumn treeParentCol = !genTree ? null : bizAll.stream()
                .filter(c -> c.getColumnName() != null
                        && c.getColumnName().equalsIgnoreCase(table.getTreeParentColumn()))
                .findFirst().orElse(null);
        m.put("treeParentJavaType", treeParentCol == null || treeParentCol.getJavaType() == null
                ? "Long" : treeParentCol.getJavaType());
        m.put("treeParentInList", treeParentCol != null
                && listCols.stream().anyMatch(c -> c.getColumnName() != null
                && c.getColumnName().equalsIgnoreCase(treeParentCol.getColumnName())));

        // 本表作为子表：唯一外键字段
        boolean isChild = table.getMasterTableId() != null && table.getMasterTableId() > 0;
        m.put("isChild", isChild);
        m.put("masterFkColumn", isChild ? orDefault(table.getMasterForeignKey(), "") : "");
        m.put("masterFkField", isChild ? GenUtils.toCamel(orDefault(table.getMasterForeignKey(), "")) : "");
        m.put("masterFkInForm", isChild && formCols.stream().anyMatch(c -> c.getColumnName() != null
                && c.getColumnName().equalsIgnoreCase(orDefault(table.getMasterForeignKey(), ""))));

        // 本表作为主表：子表信息（当前支持单子表）
        boolean hasChild = child != null && child.table() != null;
        m.put("hasChild", hasChild);
        if (hasChild) {
            GenTable ct = child.table();
            String childPkg = orDefault(ct.getPackageName(), pkg);
            String fkColumn = orDefault(ct.getMasterForeignKey(), "");
            List<GenTableColumn> childBiz = bizColumns(child.cols() == null ? List.of() : child.cols());
            Map<String, Object> cm = new HashMap<>();
            cm.put("className", ct.getClassName());
            cm.put("varName", decap(ct.getClassName()));
            cm.put("tableName", ct.getTableName());
            cm.put("comment", orDefault(ct.getTableComment(), ct.getClassName()));
            cm.put("moduleName", orDefault(ct.getModuleName(), module));
            cm.put("pkg", childPkg);
            cm.put("rootPkg", rootPkg);
            cm.put("samePkg", childPkg.equals(pkg));
            cm.put("fkColumn", fkColumn);
            cm.put("fkField", GenUtils.toCamel(fkColumn));
            cm.put("fkCapField", capitalize(GenUtils.toCamel(fkColumn)));
            cm.put("listColumns", toModel(childBiz.stream().filter(c -> on(c.getIsList())).toList()));
            // 外键列由后端按主表 ID 赋值，不能出现在可编辑表单里（否则前端会拿它当普通数字框）
            cm.put("formColumns", toModel(childBiz.stream()
                    .filter(c -> on(c.getIsForm()))
                    .filter(c -> c.getColumnName() == null
                            || !c.getColumnName().equalsIgnoreCase(fkColumn))
                    .toList()));
            m.put("child", cm);
        }

        // 关联下拉：字段里 ref_table 非空的列
        List<Map<String, Object>> refCols = new ArrayList<>();
        for (Map<String, Object> cm : toModel(biz)) {
            if (Boolean.TRUE.equals(cm.get("hasRef"))) {
                refCols.add(cm);
            }
        }
        m.put("refColumns", refCols);
        m.put("hasRef", !refCols.isEmpty());

        // Excel：按字段开关筛列
        List<GenTableColumn> excelCols = listCols.stream()
                .filter(c -> c.getExcelExport() == null || c.getExcelExport() == 1).toList();
        List<GenTableColumn> importCols = formCols.stream()
                .filter(c -> c.getExcelImport() != null && c.getExcelImport() == 1).toList();
        m.put("excelColumns", toModel(excelCols));
        m.put("excelImportColumns", toModel(importCols));
        m.put("hasExcelExport", genService && genPageApi && genFrontend && !excelCols.isEmpty());
        m.put("hasExcelImport", genService && genAddApi && genFrontend && !importCols.isEmpty());
        m.put("excelHasBigDecimal", hasType(excelCols, "BigDecimal"));
        m.put("excelHasLocalDateTime", hasType(excelCols, "LocalDateTime"));
        m.put("excelHasLocalDate", hasType(excelCols, "LocalDate"));

        // 详情抽屉：有详情接口才生成
        m.put("hasDetail", genFrontend && genDetailApi);
        return m;
    }

    /**
     * 列模型展平
     */
    private static List<Map<String, Object>> toModel(List<GenTableColumn> cols) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GenTableColumn c : cols) {
            Map<String, Object> cm = new HashMap<>();
            String javaType = c.getJavaType();
            boolean isNumber = "Integer".equals(javaType) || "Long".equals(javaType) || "BigDecimal".equals(javaType);
            String htmlType = c.getHtmlType() == null || c.getHtmlType().isBlank()
                    ? GenUtils.inferHtmlType(c.getColumnType(), javaType, c.getColumnName())
                    : c.getHtmlType();
            String dictType = c.getDictType();
            boolean hasDict = dictType != null && !dictType.isBlank();
            cm.put("javaField", c.getJavaField());
            cm.put("capField", capitalize(c.getJavaField()));
            cm.put("javaType", javaType);
            cm.put("columnComment", c.getColumnComment());
            cm.put("columnType", c.getColumnType());
            cm.put("queryType", c.getQueryType());
            cm.put("queryOp", queryOp(c.getQueryType()));
            cm.put("isString", "String".equals(javaType));
            cm.put("isNumber", isNumber);
            cm.put("isText", c.getColumnType() != null && c.getColumnType().toLowerCase().contains("text"));
            cm.put("isRequired", on(c.getIsRequired()));
            cm.put("jsInit", "switch".equals(htmlType) ? "0"
                    : ("number".equals(htmlType) || "date".equals(htmlType) || "datetime".equals(htmlType)
                    ? "undefined" : "''"));
            // 查询区默认值：非空值会把列表默认过滤掉（如 switch 默认 0 只剩「停用」），统一用空
            cm.put("queryJsInit", "String".equals(javaType) ? "''" : "undefined");
            // 控件类型
            cm.put("htmlType", htmlType);
            cm.put("isInput", "input".equals(htmlType));
            cm.put("isTextarea", "textarea".equals(htmlType));
            cm.put("isNumberCtrl", "number".equals(htmlType));
            cm.put("isSelect", "select".equals(htmlType));
            cm.put("isRadio", "radio".equals(htmlType));
            cm.put("isSwitch", "switch".equals(htmlType));
            cm.put("isDate", "date".equals(htmlType));
            cm.put("isDatetime", "datetime".equals(htmlType));
            cm.put("isPick", GenValidateUtils.isPick(htmlType));
            cm.put("dictType", hasDict ? dictType.trim() : "");
            cm.put("hasDict", hasDict);
            // 关联下拉：ref_table 非空时渲染远程搜索 el-select
            String refTable = c.getRefTable();
            boolean hasRef = refTable != null && !refTable.isBlank();
            String refLabel = hasRef && c.getRefLabel() != null && !c.getRefLabel().isBlank()
                    ? c.getRefLabel().trim() : "name";
            cm.put("refTable", hasRef ? refTable.trim() : "");
            cm.put("refLabel", hasRef ? refLabel : "");
            cm.put("hasRef", hasRef);
            cm.put("excelExport", c.getExcelExport() == null || c.getExcelExport() == 1);
            cm.put("excelImport", c.getExcelImport() != null && c.getExcelImport() == 1);
            // 校验：后端注解 + 前端 rules
            String rule = c.getValidateRule();
            cm.put("validateRule", rule == null ? "" : rule);
            cm.put("validJava", GenValidateUtils.javaAnnotations(c.getColumnComment(), javaType, rule));
            String vueRule = GenValidateUtils.vueRules(c.getColumnComment(), htmlType, rule);
            cm.put("vueRule", vueRule == null ? "" : vueRule);
            cm.put("hasVueRule", vueRule != null);
            list.add(cm);
        }
        return list;
    }

    /**
     * 校验用样例模型（与对拍 fixtures 同构）
     */
    public static Map<String, Object> sample() {
        return build(sampleTable(), sampleCols());
    }

    public static GenTable sampleTable() {
        GenTable t = new GenTable();
        t.setTableName("sys_tag");
        t.setTableComment("标签管理");
        t.setClassName("SysTag");
        t.setModuleName("tag");
        t.setPackageName("com.codegen");
        t.setBackendPath("shop-service-admin/src/main/java/com/shop/admin");
        t.setGatewayPrefix("admin");
        t.setAuthor("admin");
        t.setGenEntity(1);
        t.setGenMapper(1);
        t.setGenService(1);
        t.setGenController(1);
        t.setGenPageApi(1);
        t.setGenDetailApi(1);
        t.setGenAddApi(1);
        t.setGenEditApi(1);
        t.setGenDeleteApi(1);
        t.setGenFrontend(1);
        t.setGenApiJs(1);
        t.setGenVuePage(1);
        t.setGenQueryForm(1);
        t.setGenAddBtn(1);
        t.setGenEditBtn(1);
        t.setGenDeleteBtn(1);
        return t;
    }

    public static List<GenTableColumn> sampleCols() {
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("tag_name", "标签名称", "varchar(50)", "tagName", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        cols.add(col("tag_type", "标签类型", "tinyint", "tagType", "Integer", 0, 1, 1, "EQ", 1, 1, 3));
        return cols;
    }

    private static GenTableColumn col(String name, String comment, String dbType, String javaField,
                                      String javaType, int isPk, int isRequired, int isQuery, String queryType,
                                      int isList, int isForm, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(isPk);
        c.setIsRequired(isRequired);
        c.setIsQuery(isQuery);
        c.setQueryType(queryType);
        c.setIsList(isList);
        c.setIsForm(isForm);
        c.setSort(sort);
        return c;
    }

    private static String queryOp(String queryType) {
        String qt = orDefault(queryType, "EQ").toUpperCase();
        return switch (qt) {
            case "LIKE" -> "like";
            case "GE" -> "ge";
            case "LE" -> "le";
            default -> "eq";
        };
    }

    private static boolean hasType(List<GenTableColumn> cols, String javaType) {
        return cols.stream().anyMatch(c -> javaType.equals(c.getJavaType()));
    }

    public static boolean on(Integer flag) {
        return flag != null && flag == 1;
    }

    private static String orDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value;
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String decap(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
