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
     * 主子表：子表配置（表 + 字段 + 它自己的子表）。
     *
     * <p>P3-1 起支持三级以上：{@code grandChild} 非空即表示这一层还有下一级。
     * 模板里仍然只用 {@code child.*} 这一套键，通过 {@code hasGrandChild} 判断要不要
     * 渲染下层。这样单层（订单→明细）与多层（订单→明细→批次）共用同一份模板，
     * 不会因为多一级就生成两套代码。
     *
     * @param grandChild 子表的子表，可为 null
     */
    public record ChildInfo(GenTable table, List<GenTableColumn> cols, ChildInfo grandChild) {

        /** 兼容单层构造：老调用方只关心一层 */
        public ChildInfo(GenTable table, List<GenTableColumn> cols) {
            this(table, cols, null);
        }
    }

    /**
     * 所属系统的输出配置（V3.0 多系统）。
     *
     * <p>刻意用 record 而不是直接传 {@code SysProject} 实体：
     * 模板层只需要这几个字符串，传整个实体会让 util 反向依赖 entity，
     * 也让「哪些字段参与渲染」变得不明确。
     *
     * @param packageName  默认包名，表级为空时用
     * @param gatewayPrefix 接口前缀，表级为空时用
     * @param rootPackage  基座根包名（工程级，表上不存）
     * @param projectCode  系统编码，用于权限点与字典编码前缀（多系统隔离）
     */
    public record ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                                String projectCode) {

        /** 兼容 V3.0 之前的构造：无系统编码，权限点不加前缀 */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage) {
            this(packageName, gatewayPrefix, rootPackage, null);
        }
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
        return build(table, cols, child, null);
    }

    /**
     * 构建完整渲染模型（带所属系统配置）。
     *
     * <p>V3.0 多系统：配置解析顺序是 **表级字段 → 所属系统 → 全局默认**。
     * 表级优先是为了保护用户手工微调过的值，不能被系统配置悄悄盖掉；
     * 系统为空（存量表未迁移）时自然落到全局 {@link #rootPkg}。
     *
     * @param project 所属系统，可为 null
     */
    public static Map<String, Object> build(GenTable table, List<GenTableColumn> cols,
                                            ChildInfo child, ProjectConfig project) {
        String pkg = firstText(table.getPackageName(), project == null ? null : project.packageName(),
                "com.demo");
        String className = table.getClassName();
        String module = orDefault(table.getModuleName(), "");
        String prefix = firstText(table.getGatewayPrefix(), project == null ? null : project.gatewayPrefix(),
                "demo");
        String backendBase = orDefault(table.getBackendPath(), "src/main/java/" + pkg.replace('.', '/'));
        // 根包名不进 gen_table（它是工程级属性），所以只看系统 → 全局静态值
        String effectiveRootPkg = firstText(project == null ? null : project.rootPackage(), rootPkg);
        // 权限点前缀只在多系统场景需要；单系统（无 project）保持原样，避免存量页面权限码全变
        String effectiveProjectCode = project == null ? "" : firstText(project.projectCode());
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
        m.put("rootPkg", effectiveRootPkg);
        m.put("backendBase", backendBase);
        m.put("className", className);
        m.put("varName", decap(className));
        m.put("tableName", table.getTableName());
        // V3.0 权限点前缀：多系统下必须带系统编码，否则同名表的权限点会串。
        // 规则统一放在 GenNaming，菜单服务与自检服务调的是同一个方法——
        // 这里若自己拼字符串，就会出现「大小写/空白处理不一致 → 按钮点了没反应」的静默 bug。
        m.put("permsPrefix", com.codegen.support.GenNaming.permsPrefix(effectiveProjectCode, table.getTableName()));
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
        // P2-2 操作日志：默认开启（null 视为开），显式设 0 才关
        m.put("genLog", table.getGenLog() == null || table.getGenLog() == 1);
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
            cm.put("rootPkg", effectiveRootPkg);
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

            // P3-1 三级以上：子表自己还有子表时，把它的子表也铺进来。
            // 键名沿用 child.xxx，这样模板里只要判断 hasGrandChild 就看能渲染几层。
            ChildInfo gc = child.grandChild();
            boolean hasGrandChild = gc != null && gc.table() != null;
            cm.put("hasGrandChild", hasGrandChild);
            if (hasGrandChild) {
                GenTable gct = gc.table();
                String gFk = orDefault(gct.getMasterForeignKey(), "");
                List<GenTableColumn> gBiz = bizColumns(gc.cols() == null ? List.of() : gc.cols());
                Map<String, Object> gcm = new HashMap<>();
                gcm.put("className", gct.getClassName());
                gcm.put("varName", decap(gct.getClassName()));
                gcm.put("tableName", gct.getTableName());
                gcm.put("comment", orDefault(gct.getTableComment(), gct.getClassName()));
                gcm.put("pkg", orDefault(gct.getPackageName(), childPkg));
                gcm.put("fkColumn", gFk);
                gcm.put("fkField", GenUtils.toCamel(gFk));
                gcm.put("fkCapField", capitalize(GenUtils.toCamel(gFk)));
                gcm.put("listColumns", toModel(gBiz.stream().filter(c -> on(c.getIsList())).toList()));
                gcm.put("formColumns", toModel(gBiz.stream()
                        .filter(c -> on(c.getIsForm()))
                        .filter(c -> c.getColumnName() == null || !c.getColumnName().equalsIgnoreCase(gFk))
                        .toList()));
                // 只支持到三层：再深的业务几乎都会拆成独立页面，强行递归收益不大
                cm.put("grandChild", gcm);
            }
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

        // ===== P3-2 多对多 =====
        // 中间表本身不需要一整套 CRUD 页面：它是关系，不是实体。
        // 这里给出标记，由模板决定「中间表只生成只读接口 + 双侧主表挂多选」。
        boolean isMiddle = "middle".equalsIgnoreCase(orDefault(table.getRelationType(), ""));
        m.put("isMiddle", isMiddle);
        List<Map<String, Object>> relKeys = new ArrayList<>();
        if (isMiddle) {
            for (Map<String, Object> cm : toModel(biz)) {
                Object rk = cm.get("relationKey");
                if (rk instanceof Integer i && i == 1) {
                    relKeys.add(cm);
                }
            }
        }
        m.put("relationKeys", relKeys);

        // ===== P3-3 审批流 =====
        // 绑定了流程编码的表，列表操作列多出「提交/审批/驳回」，页面多出「审批记录」抽屉。
        // 状态列约定用 status 字段（流程定义里 status_col 也是它），值 0 草稿 / 1 待审批 / 2 已通过 / 3 已驳回。
        String flowCode = orDefault(table.getFlowCode(), "").trim();
        boolean hasFlow = !flowCode.isEmpty();
        m.put("flowCode", flowCode);
        m.put("hasFlow", hasFlow);
        if (hasFlow) {
            // 流程按钮需要知道状态字段的 Java 属性名，默认 status
            GenTableColumn statusCol = biz.stream()
                    .filter(c -> c.getColumnName() != null && "status".equalsIgnoreCase(c.getColumnName()))
                    .findFirst().orElse(null);
            m.put("flowStatusField", statusCol == null ? "status" : statusCol.getJavaField());
            m.put("flowStatusInList", statusCol != null
                    && listCols.stream().anyMatch(c -> "status".equalsIgnoreCase(c.getColumnName())));
        }

        // ===== P1 字段级增强的派生标记 =====
        // P1-2 操作列宽度：按按钮数算，2 字按钮约 62px，避免按钮被挤换行
        int btnCount = (genEditBtn ? 1 : 0) + (genDeleteBtn ? 1 : 0) + (genFrontend && genDetailApi ? 1 : 0);
        // P3-3 审批流在操作列最多再塞 3 个按钮（提交/审批/驳回 + 记录），
        // 不预留宽度的话 el-table 会把「删除」挤成两行。
        if (hasFlow) {
            btnCount += 3;
        }
        m.put("opColWidth", Math.max(120, btnCount * 62 + 30));
        // ID 列：19 位雪花号需要约 180px 才能完整显示
        m.put("idColWidth", 180);

        // 脱敏：任意列配了 maskType 就需要 vo.ftl 输出脱敏 getter
        m.put("hasMask", cols.stream().anyMatch(c -> c.getMaskType() != null && !c.getMaskType().isBlank()));
        List<GenTableColumn> maskCols = cols.stream()
                .filter(c -> c.getMaskType() != null && !c.getMaskType().isBlank()).toList();
        m.put("maskColumns", toModel(maskCols));

        // 表单联动：任意列配了 linkRule，页面就要生成联动逻辑
        m.put("hasLink", cols.stream().anyMatch(c -> c.getLinkRule() != null && !c.getLinkRule().isBlank()));
        m.put("linkCount", (int) cols.stream()
                .filter(c -> c.getLinkRule() != null && !c.getLinkRule().isBlank()).count());

        // 唯一性校验：至少一列 unique_flag=1
        List<GenTableColumn> uniqueCols = cols.stream()
                .filter(c -> c.getUniqueFlag() != null && c.getUniqueFlag() == 1).toList();
        m.put("hasUnique", !uniqueCols.isEmpty());
        m.put("uniqueColumns", toModel(uniqueCols));
        // 单列唯一时才做前端异步查重（联合唯一不适合逐字段实时校验）
        m.put("hasSingleUnique", uniqueCols.size() == 1);

        // 数据权限：任意列配了 dataScope，启动时要向拦截器登记这张表
        List<GenTableColumn> scopeCols = cols.stream()
                .filter(c -> c.getDataScope() != null && !c.getDataScope().isBlank()).toList();
        m.put("hasDataScope", !scopeCols.isEmpty());
        m.put("scopeColumns", toModel(scopeCols));
        // 一张表只登记一列（多列会让拦截器拼出 AND a=? AND b=? 这种过严条件），
        // 取 sort 最小的一列作为该表的权限列，行为可预期
        GenTableColumn scopeCol = scopeCols.stream()
                .min(java.util.Comparator.comparing(c -> c.getSort() == null ? 0 : c.getSort()))
                .orElse(null);
        m.put("scopeColumn", scopeCol == null ? "" : scopeCol.getColumnName());
        m.put("scopeType", scopeCol == null ? "" : scopeCol.getDataScope().trim());
        return m;
    }

    /**
     * 列宽推导：让表格第一眼就能看全，不用手动拖。
     *
     * <p>规则按「内容天然宽度」给：
     * 开关/日期/数字这类定长内容用窄固定宽，长文本给更宽的最小宽；
     * 再叠加列注释长度兜底（注释长的列，表头本身就需要空间）。
     */
    /**
     * 字符串最大长度：优先取校验规则里的 maxlen=，其次按 varchar(n) / char(n) 的 n 推断。
     * 取不到返回 0，模板里按「不校验长度」处理。
     */
    private static int resolveMaxLen(GenTableColumn c) {
        String rule = c.getValidateRule();
        if (rule != null) {
            for (String token : rule.split(",")) {
                String t = token.trim().toLowerCase(java.util.Locale.ROOT);
                if (t.startsWith("maxlen=")) {
                    try {
                        return Integer.parseInt(t.substring(7).trim());
                    } catch (NumberFormatException ignore) {
                        // 规则写错时退化为按列类型推断
                    }
                }
            }
        }
        String type = c.getColumnType();
        if (type == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\b(?:var)?char\\s*\\(\\s*(\\d+)\\s*\\)")
                .matcher(type);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * 注释 → 页面显示名。
     *
     * <p>字段注释里常带枚举说明（{@code 状态:1启用0停用}、{@code 订单状态（待付款、已付款）}），
     * 直接当 label 会把枚举说明整段显示出来。这里取冒号/括号前的主标题当显示名；
     * 枚举说明由字典识别（DictDetectService）解析并绑定字典，页面下拉/翻译用它。
     * 无分隔符的普通注释原样返回。
     */
    private static final java.util.regex.Pattern COMMENT_TITLE_SPLIT =
            java.util.regex.Pattern.compile("^([^:：（(]+)[:：（(].+$");

    private static String commentLabel(String comment) {
        if (comment == null || comment.isBlank()) {
            return "";
        }
        var m = COMMENT_TITLE_SPLIT.matcher(comment.trim());
        if (m.matches() && !m.group(1).isBlank()) {
            return m.group(1).trim();
        }
        return comment.trim();
    }

    private static int columnWidth(GenTableColumn c, String htmlType, String javaType) {
        int base;
        if ("switch".equals(htmlType)) {
            base = 90;
        } else if ("date".equals(htmlType)) {
            base = 120;
        } else if ("datetime".equals(htmlType)) {
            base = 170;
        } else if ("number".equals(htmlType)) {
            base = 110;
        } else if ("textarea".equals(htmlType)) {
            base = 220;
        } else if ("select".equals(htmlType) || "radio".equals(htmlType)) {
            base = 130;
        } else if ("Integer".equals(javaType) || "Long".equals(javaType) || "BigDecimal".equals(javaType)) {
            base = 110;
        } else {
            base = 140;
        }
        // 表头文字长的（如「商品分类名称（二级）」）至少留出字宽，按每字 14px 估
        String comment = c.getColumnComment();
        if (comment != null && !comment.isBlank()) {
            int need = comment.length() * 14 + 40;
            base = Math.max(base, Math.min(need, 260));
        }
        return base;
    }

    /**
     * 列模型展平
     */
    private static List<Map<String, Object>> toModel(List<GenTableColumn> cols) {        List<Map<String, Object>> list = new ArrayList<>();
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
            // 数据库列名：查重、导出表头映射等需要按真实列名拼 SQL
            cm.put("dbField", c.getColumnName());
            cm.put("javaType", javaType);
            // 原始注释：枚举说明等仍可能在少数场景被引用
            cm.put("columnComment", c.getColumnComment());
            // 页面显示名：剥掉注释里的枚举说明（状态:1启用0停用 -> 状态），
            // vue 模板的 label/placeholder/校验消息统一用这个，原始注释不再直接上屏
            cm.put("commentLabel", commentLabel(c.getColumnComment()));
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
            // P1-2 列宽自适应：按控件类型与宽度语义给默认值，避免所有列等宽
            cm.put("colWidth", columnWidth(c, htmlType, javaType));
            cm.put("dictType", hasDict ? dictType.trim() : "");
            cm.put("hasDict", hasDict);
            // P3-2 中间表外键列标记
            cm.put("relationKey", c.getRelationKey() == null ? 0 : c.getRelationKey());
            // getXxx / Xxx::getXxx 用的属性名（首字母大写），避免模板里做大小写转换
            cm.put("capProp", capitalize(c.getJavaField()));
            // P1-5 导入逐行校验用：从 validate_rule 里取 maxlen，取不到再从列类型 varchar(n) 推
            cm.put("maxLen", resolveMaxLen(c));
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

            // P1-1 脱敏：maskType 非空时，VO 的 getter 与列表渲染都走脱敏
            String maskType = c.getMaskType();
            boolean hasMask = maskType != null && !maskType.isBlank();
            cm.put("maskType", hasMask ? maskType.trim() : "");
            cm.put("hasMask", hasMask);

            // P1-3 唯一性校验
            cm.put("uniqueFlag", c.getUniqueFlag() != null && c.getUniqueFlag() == 1);

            // P1-4 表单联动：linkRule 是 JSON 字符串，模板里原样输出给前端解析
            String linkRule = c.getLinkRule();
            boolean hasLink = linkRule != null && !linkRule.isBlank();
            cm.put("linkRule", hasLink ? linkRule.trim() : "");
            cm.put("hasLink", hasLink);

            // P2-1 数据权限
            String dataScope = c.getDataScope();
            boolean hasScope = dataScope != null && !dataScope.isBlank();
            cm.put("dataScope", hasScope ? dataScope.trim() : "");
            cm.put("hasDataScope", hasScope);

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

    /**
     * 按优先级取第一个非空值。
     *
     * <p>三级回落用：表级 → 系统 → 全局默认。写成可变参数是因为
     * 将来若加第四级（比如组织级配置）不用再改方法签名。
     */
    private static String firstText(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
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
