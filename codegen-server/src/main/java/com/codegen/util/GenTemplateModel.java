package com.codegen.util;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.support.GenCascadeMode;
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
     * <p>V4.4 一主多子：主表可挂多张子表（{@code loadChildren} 返回列表），
     * 每张子表自己的下级也支持多张（{@code grandChildren}），深度仍封顶三层。
     * 模板里通过 {@code <#list children as child>} 循环渲染，
     * {@code hasGrandChild / grandChild} 保留为首张下级的便捷键（兼容存量模板片段）。
     *
     * @param grandChildren 本子表的下级列表，可为空列表
     */
    public record ChildInfo(GenTable table, List<GenTableColumn> cols, List<ChildInfo> grandChildren) {
        /** 兼容单层构造：老调用方只关心一层 */
        public ChildInfo(GenTable table, List<GenTableColumn> cols) {
            this(table, cols, List.of());
        }

        /** 兼容单下级构造：P3-1 时代只支持一张孙表 */
        public ChildInfo(GenTable table, List<GenTableColumn> cols, ChildInfo grandChild) {
            this(table, cols, grandChild == null ? List.of() : List.of(grandChild));
        }
    }

    /**
     * V4.4 模板分道：按表形态派发 serviceImpl 模板编码。
     *
     * <p>唯一派发入口——渲染引擎与基线测试都调它，判据只写这一处：
     * 有子表走 masterdetail（含「树形 + 子表」组合），否则树形走 tree，
     * 其余（绝大多数普通单表）走 simple。
     *
     * @param model {@link #build} 产出的模板模型，只读 hasChild / genTree 两个布尔键
     */
    public static String serviceImplTplCode(Map<String, Object> model) {
        if (Boolean.TRUE.equals(model.get("hasChild"))) {
            return "serviceImplMasterdetail";
        }
        if (Boolean.TRUE.equals(model.get("genTree"))) {
            return "serviceImplTree";
        }
        return "serviceImplSimple";
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
     * @param projectStructure V5.5 工程结构：null=单模块，"multi"=多模块（模块归属唯一实现在 GenPathResolver.moduleRelPrefix）
     */
    public record ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                                String projectCode, Integer defaultDeleteCascadeMode, Integer defaultGenPrint,
                                String projectStructure, Integer payEnabled, Integer mobileEnabled,
                                Integer fileEnabled, Integer testEnabled) {

        /** 兼容 V3.0 之前的构造：无系统编码，权限点不加前缀 */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage) {
            this(packageName, gatewayPrefix, rootPackage, null, null, null, null, null, null, null, null);
        }

        /** 兼容 V3.0 构造：无级联默认值（V4.4 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage, String projectCode) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, null, null, null, null, null, null, null);
        }

        /** 兼容 V4.4 构造：无打印默认值（V4.5 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                             String projectCode, Integer defaultDeleteCascadeMode) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, defaultDeleteCascadeMode, null, null, null, null, null, null);
        }

        /** 兼容 V4.5 构造：无工程结构（V5.5 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                             String projectCode, Integer defaultDeleteCascadeMode, Integer defaultGenPrint) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, defaultDeleteCascadeMode,
                    defaultGenPrint, null, null, null, null, null);
        }

        /** 兼容 V6.2 构造：无支付开关（V6.3 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                             String projectCode, Integer defaultDeleteCascadeMode, Integer defaultGenPrint,
                             String projectStructure) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, defaultDeleteCascadeMode,
                    defaultGenPrint, projectStructure, null, null, null, null);
        }

        /** 兼容 V6.3/V6.4 构造：无移动端开关（V6.5 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                             String projectCode, Integer defaultDeleteCascadeMode, Integer defaultGenPrint,
                             String projectStructure, Integer payEnabled) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, defaultDeleteCascadeMode,
                    defaultGenPrint, projectStructure, payEnabled, null, null, null);
        }

        /** 兼容 V6.5 构造：无文件能力开关（V6.6 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                             String projectCode, Integer defaultDeleteCascadeMode, Integer defaultGenPrint,
                             String projectStructure, Integer payEnabled, Integer mobileEnabled) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, defaultDeleteCascadeMode,
                    defaultGenPrint, projectStructure, payEnabled, mobileEnabled, null, null);
        }

        /** 兼容 V6.6 构造：无测试骨架开关（V6.7 前的调用方） */
        public ProjectConfig(String packageName, String gatewayPrefix, String rootPackage,
                             String projectCode, Integer defaultDeleteCascadeMode, Integer defaultGenPrint,
                             String projectStructure, Integer payEnabled, Integer mobileEnabled,
                             Integer fileEnabled) {
            this(packageName, gatewayPrefix, rootPackage, projectCode, defaultDeleteCascadeMode,
                    defaultGenPrint, projectStructure, payEnabled, mobileEnabled, fileEnabled, null);
        }
    }

    /**
     * V4.5 打印单据开关的三级回落唯一实现：表级 → 系统默认 → 硬编码关。
     *
     * <p>刻意不进 GenSwitchDefaults 17 键体系：打印的硬编码兜底是「关」而非「开」，
     * 塞进 17 键要改 resolve 的兜底语义。三态约定沿用 V4.4 级联模式的教训
     * （坑库 mysql.md）：表级必须可空，NULL=跟随系统，否则系统默认层成死配置。
     *
     * @param tableVal   gen_table.gen_print（NULL=未配置）
     * @param projectVal sys_project.default_gen_print（NULL=未配置）
     */
    public static boolean resolveGenPrint(Integer tableVal, Integer projectVal) {
        if (tableVal != null) {
            return tableVal == 1;
        }
        if (projectVal != null) {
            return projectVal == 1;
        }
        return false;
    }

    /**
     * V6.2 部署物开关的三级回落唯一实现：系统级 → 硬编码关。
     * 与 resolveGenPrint 同款语义；部署物无表级开关（Dockerfile 是工程级产物，按系统给）。
     */
    public static boolean resolveGenDeploy(Integer projectVal) {
        return projectVal != null && projectVal == 1;
    }

    /**
     * V6.3 支付能力包开关的回落唯一实现：系统级 → 硬编码关。
     * 与 resolveGenDeploy 同款语义；支付模块是系统级能力包，无表级开关
     * （业务表经 pay_status 列约定自动接入，不需要表级绑定）。
     */
    public static boolean resolvePay(Integer projectVal) {
        return projectVal != null && projectVal == 1;
    }

    /**
     * V6.5 移动端支持开关的回落唯一实现：系统级 → 硬编码关。
     * 与 resolvePay 同款语义；移动工程是系统级能力包，无表级开关
     * （业务表经 gen_vue_page 开关 + 表形态自动接入，从表/树表缺省排除）。
     */
    public static boolean resolveMobile(Integer projectVal) {
        return projectVal != null && projectVal == 1;
    }

    /**
     * V6.6 文件能力包开关的回落唯一实现：系统级 → 硬编码关。
     * 与 resolvePay 同款语义；文件上传下载基座（sys_file 表 + /file 端点 + 上传组件）
     * 是系统级能力包，无表级开关（业务表经 html_type=image/file 列配置自动接入，
     * 没开能力包时列仍按普通文本框渲染，不产生死链）。
     */
    public static boolean resolveFile(Integer projectVal) {
        return projectVal != null && projectVal == 1;
    }

    /**
     * V6.7 测试骨架开关的回落唯一实现：系统级 → 硬编码<b>开</b>。
     * 与 resolvePay/resolveFile 方向相反：测试只写 src/test 目录，零运行时侵入，
     * 没有理由默认关；test_enabled=0 才显式关闭。
     */
    public static boolean resolveTest(Integer projectVal) {
        return projectVal == null || projectVal == 1;
    }

    /**
     * V6.8 品牌主色的回落唯一实现：配置值 → 硬编码 {@code #e1251b}。
     * <p>只做格式宽松校验（{@code #rgb}/{@code #rrggbb}），非法值回落默认色，
     * 避免把 {@code red;} 这类串直接拼进 CSS 变量打坏整个页面。
     */
    public static String resolveThemeColor(String projectVal) {
        if (projectVal == null) {
            return DEFAULT_THEME_COLOR;
        }
        String v = projectVal.trim();
        return v.matches("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$") ? v : DEFAULT_THEME_COLOR;
    }

    /**
     * V6.8 应用显示名的回落唯一实现：配置值 → 项目名 → 项目编码。
     */
    public static String resolveAppName(String projectVal, String projectName, String projectCode) {
        return firstText(projectVal, projectName, projectCode, "应用");
    }

    /**
     * V6.8 品牌 Logo：空 = 调用方自行回落（favicon 用 {@link #FALLBACK_APP_LOGO}，
     * 侧边栏/登录页用文字标识）。绝不回落成不存在的 logo.png——那会让每个新工程的 favicon 都是 404。
     */
    public static final String FALLBACK_APP_LOGO = "placeholder.svg";

    public static String resolveAppLogo(String projectVal) {
        return projectVal == null ? "" : projectVal.trim();
    }

    /** V6.8 图片展示尺寸的唯一定义源（模板只消费模型键，禁止再写死像素） */
    public static final int IMAGE_LIST_SIZE = 80;
    public static final int IMAGE_DETAIL_SIZE = 120;
    /** 主题默认色（与后台既有主色一致） */
    public static final String DEFAULT_THEME_COLOR = "#e1251b";

    /**
     * 构建完整渲染模型（无子表）
     */
    public static Map<String, Object> build(GenTable table, List<GenTableColumn> cols) {
        return build(table, cols, (List<ChildInfo>) null, null);
    }

    /**
     * 构建完整渲染模型（单子表兼容签名，V4.4 起内部转列表）
     *
     * @param child 本表作为主表时的子表信息，可为 null
     */
    public static Map<String, Object> build(GenTable table, List<GenTableColumn> cols, ChildInfo child) {
        return build(table, cols, child == null ? List.of() : List.of(child), null);
    }

    /**
     * 构建完整渲染模型（带所属系统配置）。
     *
     * <p>V3.0 多系统：配置解析顺序是 **表级字段 → 所属系统 → 全局默认**。
     * 表级优先是为了保护用户手工微调过的值，不能被系统配置悄悄盖掉；
     * 系统为空（存量表未迁移）时自然落到全局 {@link #rootPkg}。
     *
     * <p>V4.4 一主多子：{@code children} 为本表挂接的全部子表（原单子表签名保留兼容）。
     *
     * @param children 本表作为主表时的全部子表，可为 null / 空列表
     * @param project  所属系统，可为 null
     */
    public static Map<String, Object> build(GenTable table, List<GenTableColumn> cols,
                                            List<ChildInfo> children, ProjectConfig project) {
        String pkg = firstText(table.getPackageName(), project == null ? null : project.packageName(),
                "com.demo");
        String className = table.getClassName();
        String module = orDefault(table.getModuleName(), "");
        // V5.0 F-09：删掉 "demo" 硬编码回落——空前缀就空前缀。
        // 以前空前缀会被兜成 demo，生成物 apiBase 出现 /api/demo，
        // 目标工程没有这个网关段时全部 404，排查起来像「代码生成器抽风」。
        String prefix = firstText(table.getGatewayPrefix(), project == null ? null : project.gatewayPrefix(),
                "");
        String backendBase = orDefault(table.getBackendPath(), "src/main/java/" + pkg.replace('.', '/'));
        // V6.7 测试骨架落点：与 backendBase 同根推导（src/main/java → src/test/java|resources）。
        // backendPath 自定义值不含 src/main/java 段时（罕见）按同前缀 + "-test" 兜底，
        // 生成 nextSteps 里会提示核对路径，避免静默写错位置
        String testJavaBase = backendBase.contains("src/main/java")
                ? backendBase.replaceFirst("src/main/java", "src/test/java")
                : backendBase + "-test";
        // resources 基路径只取模块根：src/test/resources 下没有包路径，
        // 照搬 testJavaBase 的 replace 会把包尾巴带进来（.../resources/com/demo），
        // application-test.yml 的 classpath*:schema/table-*.sql 就一张表也扫不到
        String testResBase = backendBase.contains("src/main/java")
                ? backendBase.substring(0, backendBase.indexOf("src/main/java")) + "src/test/resources"
                : backendBase + "-test/resources";
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
        m.put("testJavaBase", testJavaBase);
        m.put("testResBase", testResBase);
        m.put("className", className);
        m.put("varName", decap(className));
        m.put("tableName", table.getTableName());
        // V3.0 权限点前缀：多系统下必须带系统编码，否则同名表的权限点会串。
        // 规则统一放在 GenNaming，菜单服务与自检服务调的是同一个方法——
        // 这里若自己拼字符串，就会出现「大小写/空白处理不一致 → 按钮点了没反应」的静默 bug。
        m.put("permsPrefix", com.codegen.support.GenNaming.permsPrefix(effectiveProjectCode, table.getTableName()));
        m.put("comment", sanitizeComment(orDefault(table.getTableComment(), className)));
        m.put("moduleName", module);
        m.put("gatewayPrefix", prefix);
        m.put("author", orDefault(table.getAuthor(), ""));
        // F-09：空前缀 → /{module}（与后端 @RequestMapping("/${moduleName}") 直连，代理只剥 /dev-api）；
        // 有前缀 → /api/{prefix}/{module}（网关段原样保留，代理剥 /dev-api/api/{prefix}）
        // V6.0 micro：产物工程自带网关，网关按 /{module}/** 路由，前端不再拼 /api/{prefix} 段；
        // multi/单模块行为零变化（isMicro 严格匹配 "micro"）
        boolean microStructure = project != null
                && "micro".equalsIgnoreCase(String.valueOf(project.projectStructure()).trim());
        m.put("isMicro", microStructure);
        m.put("apiBase", microStructure ? "/" + module
                : prefix.isEmpty() ? "/" + module : "/api/" + prefix + "/" + module);
        // V6.8 字典等「非模块级」接口的前缀根：
        // apiBase 带模块段（/api/{prefix}/{module}），字典是全局接口，必须去掉模块段再拼。
        // 空前缀（直连 + dev 代理剥 /dev-api）时为 ""，micro（网关按 /{module} 路由）也为 ""。
        m.put("apiRoot", microStructure || prefix.isEmpty() ? "" : "/api/" + prefix);

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
        List<Map<String, Object>> bizModels = toModel(biz);
        m.put("columns", bizModels);
        m.put("queryColumns", toModel(queryCols));
        m.put("vueQueryColumns", toModel(vueQueryCols));
        // V6.7 测试建表模板需要全量业务列（formColumns 只是 is_form 子集）
        m.put("bizCols", toModel(biz));
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

        // 注释枚举：从列注释里解析出的静态选项（状态(0正常 1停用) 这类）。
        // 生成的独立项目不一定有 sys_dict 模块，字典接口取不到值时页面会整列空白，
        // 所以注释里写得明的枚举一律内联成前端常量，零网络请求即可回显。
        List<Map<String, Object>> enumColumns = new ArrayList<>();
        for (Map<String, Object> cm : bizModels) {
            if (Boolean.TRUE.equals(cm.get("hasEnum"))) {
                Map<String, Object> ec = new HashMap<>();
                ec.put("javaField", cm.get("javaField"));
                ec.put("enumJs", cm.get("enumJs"));
                enumColumns.add(ec);
            }
        }
        m.put("enumColumns", enumColumns);
        m.put("vueHasEnum", !enumColumns.isEmpty());

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
        // V5.0 修复：排班/考勤类表有 time 列（映射 LocalTime），
        // pageDto/saveDto/vo/controller 的导包标记此前漏了 LocalTime，
        // 生成物自检满屏 cannot find symbol: class LocalTime
        m.put("pageDtoHasLocalTime", hasType(biz, "LocalTime"));
        m.put("pageDtoHasImport", hasType(biz, "BigDecimal") || hasType(biz, "LocalDateTime")
                || hasType(biz, "LocalDate") || hasType(biz, "LocalTime"));
        m.put("saveDtoHasBigDecimal", hasType(formCols, "BigDecimal"));
        m.put("saveDtoHasLocalDateTime", hasType(formCols, "LocalDateTime"));
        m.put("saveDtoHasLocalDate", hasType(formCols, "LocalDate"));
        m.put("saveDtoHasLocalTime", hasType(formCols, "LocalTime"));
        m.put("saveDtoHasImport", hasType(formCols, "BigDecimal") || hasType(formCols, "LocalDateTime")
                || hasType(formCols, "LocalDate") || hasType(formCols, "LocalTime"));
        m.put("listHasBigDecimal", hasType(listCols, "BigDecimal"));
        m.put("listHasLocalDateTime", hasType(listCols, "LocalDateTime"));
        m.put("listHasLocalDate", hasType(listCols, "LocalDate"));
        m.put("listHasLocalTime", hasType(listCols, "LocalTime"));

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
        // V6.7 测试骨架：开关 + 出 Controller 的非从表 + 单模块工程才生成（multi/micro 启动类在
        // bootstrap、业务类在 service，跨模块 @SpringBootTest 落点 P1 再支持，先保证不产坏文件）。
        // 门控结果只算一次，引擎与模板只认结果（genPrint 同款约定）
        boolean multiStructure = project != null
                && ("multi".equals(orDefault(project.projectStructure(), ""))
                    || "micro".equals(orDefault(project.projectStructure(), "")));
        boolean testOn = resolveTest(project == null ? null : project.testEnabled()) && !multiStructure;
        m.put("testEnabled", testOn);
        m.put("hasControllerTest", testOn && on(table.getGenController()) && !isChild);
        m.put("masterFkColumn", isChild ? orDefault(table.getMasterForeignKey(), "") : "");
        m.put("masterFkField", isChild ? GenUtils.toCamel(orDefault(table.getMasterForeignKey(), "")) : "");
        m.put("masterFkInForm", isChild && formCols.stream().anyMatch(c -> c.getColumnName() != null
                && c.getColumnName().equalsIgnoreCase(orDefault(table.getMasterForeignKey(), ""))));

        // 本表作为主表：子表信息（V4.4 一主多子，按 master_table_id 全量挂接）
        List<Map<String, Object>> childrenModel = new ArrayList<>();
        if (children != null) {
            for (ChildInfo ci : children) {
                if (ci == null || ci.table() == null) {
                    continue;
                }
                childrenModel.add(childModel(ci, pkg, module, effectiveRootPkg));
            }
        }
        m.put("children", childrenModel);
        m.put("hasChild", !childrenModel.isEmpty());
        m.put("childCount", childrenModel.size());
        // 任一子表带下级时，页面才需要生成三级明细弹窗
        m.put("hasGrandDialog", childrenModel.stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.get("hasGrandChild"))));

        // V4.4 删除级联模式：表级 → 系统默认 → 硬编码级联（GenCascadeMode.resolve）
        int cascadeMode = GenCascadeMode.resolve(table.getDeleteCascadeMode(),
                project == null ? null : project.defaultDeleteCascadeMode());
        m.put("deleteCascadeMode", cascadeMode);
        m.put("deleteCascadeBlock", cascadeMode == GenCascadeMode.BLOCK);
        m.put("deleteCascadeOn", cascadeMode == GenCascadeMode.CASCADE);
        m.put("deleteCascadeOff", cascadeMode == GenCascadeMode.NONE);

        // V4.5 打印单据：表级 → 系统默认 → 硬编码关（resolveGenPrint 唯一实现）。
        // 前置条件：开前端 + api.js（打印页 import 取数函数）+ 非从表（从表无独立页面）
        // + 有可用取数接口（主子表 with-children / 单表详情）。
        // 缺任何一个，开关开了也不生成——否则产物必是坏页面。
        boolean genPrint = genFrontend && on(table.getGenApiJs()) && !isChild
                && resolveGenPrint(table.getGenPrint(), project == null ? null : project.defaultGenPrint())
                && (!childrenModel.isEmpty() || genDetailApi);
        m.put("genPrint", genPrint);

        // 关联下拉：字段里 ref_table 非空的列
        List<Map<String, Object>> refCols = new ArrayList<>();
        for (Map<String, Object> cm : toModel(biz)) {
            if (Boolean.TRUE.equals(cm.get("hasRef"))) {
                refCols.add(cm);
            }
        }
        m.put("refColumns", refCols);
        m.put("hasRef", !refCols.isEmpty());
        // refText 帮助函数跟随「主表或任一子表明细列有引用」：模板的详情明细列（child.listColumns）
        // 也会调 refText，若只按主表 hasRef 生成定义，主表无引用而子表有引用时会渲染崩溃
        boolean childRef = childrenModel.stream().anyMatch(c -> {
            Object childCols = c.get("listColumns");
            if (!(childCols instanceof List<?> list)) {
                return false;
            }
            return list.stream().anyMatch(x -> x instanceof Map<?, ?> col
                    && Boolean.TRUE.equals(col.get("hasRef")));
        });
        m.put("hasRefHelper", !refCols.isEmpty() || childRef);

        // Excel：按字段开关筛列
        List<GenTableColumn> excelCols = listCols.stream()
                .filter(c -> c.getExcelExport() == null || c.getExcelExport() == 1).toList();
        List<GenTableColumn> importCols = formCols.stream()
                .filter(c -> c.getExcelImport() != null && c.getExcelImport() == 1).toList();
        m.put("excelColumns", toModel(excelCols));
        m.put("excelImportColumns", toModel(importCols));
        m.put("hasExcelExport", genService && genPageApi && genFrontend && !excelCols.isEmpty());
        m.put("hasExcelImport", genService && genAddApi && genFrontend && !importCols.isEmpty());
        // 任一列绑了字典：controller 模板据此注入 DictTexts（导出列翻译中文）
        m.put("hasDictColumn", cols != null && cols.stream().anyMatch(c ->
                c.getDictType() != null && !c.getDictType().isBlank()));
        m.put("excelHasBigDecimal", hasType(excelCols, "BigDecimal"));
        m.put("excelHasLocalDateTime", hasType(excelCols, "LocalDateTime"));
        m.put("excelHasLocalDate", hasType(excelCols, "LocalDate"));

        // 详情抽屉：有详情接口才生成
        m.put("hasDetail", genFrontend && genDetailApi);

        // V6.3 支付接入约定：业务表含 pay_status 列且系统 pay_enabled=1 → 「去支付」入口（模板条件块，无侵入）。
        // 系统没开支付能力包时按钮不渲染（否则产物有按钮没收银台，必是死链）
        m.put("hasPayStatus", genFrontend && resolvePay(project == null ? null : project.payEnabled())
                && cols != null && cols.stream().anyMatch(c ->
                "pay_status".equalsIgnoreCase(c.getColumnName())));

        // V6.6 文件能力包：系统开了文件基座 + 页面里有 image/file 列 → 页面 import 上传组件（模板条件块）。
        // 系统没开时列按普通文本框渲染（htmlType 仍是 image/file 但组件不注册，导入即坏）——
        // 所以 hasUpload 一起受 fileEnabled 网控，与 hasPayStatus 防死链同款思路。
        // 统计范围必须含子表/孙表：模板对 child.formColumns / gc.formColumns 同样渲染上传组件，
        // 只看主表会让「主表无上传列、子表有」的页面漏注册组件（渲染即 Unknown custom element）
        boolean fileOn = genFrontend && resolveFile(project == null ? null : project.fileEnabled());
        m.put("fileEnabled", fileOn);
        List<Map<String, Object>> bizModel = toModel(biz);
        m.put("hasUpload", fileOn && (hasUploadColumn(bizModel)
                || childrenModel.stream().anyMatch(GenTemplateModel::childHasUpload)));
        // V6.8 图片展示尺寸与多图标记：唯一实现在 GenTemplateModel，模板只消费（禁止再写死 44/64px）
        m.put("imageListSize", IMAGE_LIST_SIZE);
        m.put("imageDetailSize", IMAGE_DETAIL_SIZE);
        // 多图门控必须与模板消费端同口径：模板按列级 isMultiImage 渲染 <multi-image-upload>、
        // firstImage/imageListOf；只按主表列名猜会让「显式配 htmlType=multiImage」或
        // 「多图列只在子表/孙表」的页面漏注册组件、漏生成方法（渲染即 TypeError）。
        boolean hasMultiImage = fileOn && (hasMultiImageColumn(bizModel)
                || childrenModel.stream().anyMatch(GenTemplateModel::childHasMultiImage));
        m.put("hasMultiImage", hasMultiImage);

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
        // V4.5 打印单据在操作列多一个「打印」按钮
        if (genPrint) {
            btnCount += 1;
        }
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
        // V6.7 集成测试新增/修改 body：Java 侧算好 Java 表达式文本（模板只输出不拼 JSON，
        // FreeMarker 里手拼转义引号必然翻车）；唯一列值运行时随机防测试互撞
        m.put("testFieldsJsonExpr", testFieldsJsonExpr(formCols, uniqueCols));
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
     * V6.7 产物测试出厂：集成测试新增/修改 body 的 Java 表达式文本。
     * 选列 = is_form 且必填，再补首个字符串列（保证 update 有可改字段）；
     * 值按 javaType 给最小合法字面量；唯一列的值运行时随机（suffix()）防测试互撞唯一键。
     * 返回的是「Java 源码表达式」，如 {@code "{\"name\":\"it\",\"age\":1}"}，
     * 含唯一列时为 {@code "{\"name\":\"" + suffix() + "\",\"age\":1}"}。
     */
    private static String testFieldsJsonExpr(List<GenTableColumn> formCols, List<GenTableColumn> uniqueCols) {
        List<GenTableColumn> chosen = new ArrayList<>();
        for (GenTableColumn c : formCols) {
            if (on(c.getIsRequired())) {
                chosen.add(c);
            }
        }
        if (chosen.stream().noneMatch(c -> "String".equals(c.getJavaType()))) {
            for (GenTableColumn c : formCols) {
                if ("String".equals(c.getJavaType()) && !chosen.contains(c)) {
                    chosen.add(c);
                    break;
                }
            }
        }
        if (chosen.isEmpty()) {
            return "\"{\\\"_gen_it\\\":1}\"";
        }
        java.util.Set<String> uniqueFields = new java.util.LinkedHashSet<>();
        for (GenTableColumn c : uniqueCols) {
            uniqueFields.add(c.getJavaField());
        }
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < chosen.size(); i++) {
            GenTableColumn c = chosen.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(c.getJavaField()).append("\":");
            String jt = c.getJavaType() == null ? "String" : c.getJavaType();
            if ("parentId".equals(c.getJavaField())) {
                json.append('0');   // 树表根节点约定
            } else if (uniqueFields.contains(c.getJavaField()) && "String".equals(jt)) {
                json.append("@U@");
            } else if (uniqueFields.contains(c.getJavaField())) {
                json.append("@N@");
            } else if ("String".equals(jt)) {
                json.append("\"it\"");
            } else if ("LocalDate".equals(jt)) {
                json.append("\"2026-01-01\"");
            } else if ("LocalDateTime".equals(jt)) {
                json.append("\"2026-01-01T00:00:00\"");
            } else if ("LocalTime".equals(jt)) {
                json.append("\"00:00:00\"");
            } else if ("Boolean".equals(jt)) {
                json.append("true");
            } else {
                json.append('1');   // Integer/Long/BigDecimal 等数值
            }
        }
        json.append('}');
        // 转 Java 字符串字面量：引号转义后嵌引号成表达式；唯一列占位符换成运行时拼接
        String escaped = json.toString().replace("\"", "\\\"");
        String expr = "\"" + escaped + "\"";
        expr = expr.replace("\\\"@U@\\\"", "\\\" + suffix() + \\\"");
        expr = expr.replace("@N@", "\" + (System.nanoTime() % 1000000) + \"");
        return expr;
    }

    /**
     * 单张子表的模板模型（V4.4 从 build 主流程抽出，支持一主多子循环调用）。
     *
     * <p>键名与 V3.x 单子表时代完全一致（className/varName/fkCapField/...），
     * 模板里 {@code <#list children as child>} 后沿用 {@code child.xxx} 即可。
     */
    private static Map<String, Object> childModel(ChildInfo ci, String pkg, String module, String rootPkg) {
        GenTable ct = ci.table();
        String childPkg = orDefault(ct.getPackageName(), pkg);
        String fkColumn = orDefault(ct.getMasterForeignKey(), "");
        List<GenTableColumn> childBiz = bizColumns(ci.cols() == null ? List.of() : ci.cols());
        Map<String, Object> cm = new HashMap<>();
        cm.put("className", ct.getClassName());
        cm.put("varName", decap(ct.getClassName()));
        cm.put("tableName", ct.getTableName());
        cm.put("comment", sanitizeComment(orDefault(ct.getTableComment(), ct.getClassName())));
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

        // 三级：本子表自己的下级（V4.4 起可多张），深度封顶三层
        List<Map<String, Object>> grands = new ArrayList<>();
        if (ci.grandChildren() != null) {
            for (ChildInfo gc : ci.grandChildren()) {
                if (gc == null || gc.table() == null) {
                    continue;
                }
                grands.add(grandModel(gc, childPkg));
            }
        }
        cm.put("grandChildren", grands);
        cm.put("hasGrandChild", !grands.isEmpty());
        if (!grands.isEmpty()) {
            // 兼容旧键：单下级场景模板仍可写 child.grandChild.xxx
            cm.put("grandChild", grands.get(0));
        }
        return cm;
    }

    /**
     * 孙表（第三级）模板模型，键名与 V3.x 一致。
     */
    private static Map<String, Object> grandModel(ChildInfo gc, String parentChildPkg) {
        GenTable gct = gc.table();
        String gFk = orDefault(gct.getMasterForeignKey(), "");
        List<GenTableColumn> gBiz = bizColumns(gc.cols() == null ? List.of() : gc.cols());
        Map<String, Object> gcm = new HashMap<>();
        gcm.put("className", gct.getClassName());
        gcm.put("varName", decap(gct.getClassName()));
        gcm.put("tableName", gct.getTableName());
        gcm.put("comment", sanitizeComment(orDefault(gct.getTableComment(), gct.getClassName())));
        gcm.put("pkg", orDefault(gct.getPackageName(), parentChildPkg));
        gcm.put("fkColumn", gFk);
        gcm.put("fkField", GenUtils.toCamel(gFk));
        gcm.put("fkCapField", capitalize(GenUtils.toCamel(gFk)));
        gcm.put("listColumns", toModel(gBiz.stream().filter(c -> on(c.getIsList())).toList()));
        gcm.put("formColumns", toModel(gBiz.stream()
                .filter(c -> on(c.getIsForm()))
                .filter(c -> c.getColumnName() == null || !c.getColumnName().equalsIgnoreCase(gFk))
                .toList()));
        return gcm;
    }

    /** 列模型是否带上传控件标记（image / file / multiImage，V6.8 起含多图） */
    private static boolean isUploadColumn(Object colModel) {
        return colModel instanceof Map<?, ?> cm && Boolean.TRUE.equals(cm.get("isUpload"));
    }

    private static boolean hasUploadColumn(List<?> columnModels) {
        return columnModels != null && columnModels.stream().anyMatch(GenTemplateModel::isUploadColumn);
    }

    /** 子表是否自带上传列（含它自己的下级明细表单列）——hasUpload 判定用 */
    private static boolean childHasUpload(Map<String, Object> child) {
        if (hasUploadColumn(asList(child.get("formColumns")))) {
            return true;
        }
        for (Object grand : asList(child.get("grandChildren"))) {
            if (grand instanceof Map<?, ?> gm && hasUploadColumn(asList(gm.get("formColumns")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMultiImageColumn(Object colModel) {
        return colModel instanceof Map<?, ?> cm && Boolean.TRUE.equals(cm.get("isMultiImage"));
    }

    private static boolean hasMultiImageColumn(List<?> columnModels) {
        return columnModels != null && columnModels.stream().anyMatch(GenTemplateModel::isMultiImageColumn);
    }

    /** 子表是否自带多图列（含下级明细表单列）——hasMultiImage 判定用，与 childHasUpload 同口径 */
    private static boolean childHasMultiImage(Map<String, Object> child) {
        if (hasMultiImageColumn(asList(child.get("formColumns")))) {
            return true;
        }
        for (Object grand : asList(child.get("grandChildren"))) {
            if (grand instanceof Map<?, ?> gm && hasMultiImageColumn(asList(gm.get("formColumns")))) {
                return true;
            }
        }
        return false;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
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

    /** 「上级分类ID，0=顶级 1=一级」这类逗号枚举尾巴：主标题同样只取前半段 */
    private static final java.util.regex.Pattern COMMENT_ENUM_TAIL =
            java.util.regex.Pattern.compile("^([^,，:：（(]{1,40})[,，]\\s*\\d+\\s*[-=:：]?.+$");

    /** 注释脱敏：剥掉单双引号，防止注释原文嵌进 Java/JS 字符串字面量打坏生成代码（坑 52） */
    private static String sanitizeComment(String s) {
        return s == null ? null : s.replace("\"", "").replace("'", "");
    }

    private static String commentLabel(String comment) {
        return commentLabel(comment, null);
    }

    /**
     * 注释 → 页面显示名，带字段名回落。
     *
     * <p>注释整体就是编号映射（{@code 1男 2女 0未知}、{@code 1启用0停用}）时没有标题可取，
     * 显示名回落到列名——和「注释缺失回落列名」的导入行为保持一致，避免映射串整段上屏。
     */
    private static String commentLabel(String comment, String fallbackField) {
        if (comment == null || comment.isBlank()) {
            return "";
        }
        String text = comment.trim();
        if (isPureEnumMapping(text)) {
            return fallbackField == null || fallbackField.isBlank() ? text : fallbackField;
        }
        var m = COMMENT_TITLE_SPLIT.matcher(text);
        if (m.matches() && !m.group(1).isBlank()) {
            text = m.group(1).trim();
        } else {
            var e = COMMENT_ENUM_TAIL.matcher(text);
            if (e.matches() && !e.group(1).isBlank()) {
                text = e.group(1).trim();
            }
        }
        // 「分类ID」「客户编号」在页面上显示成「分类」「客户」更自然；
        // 但纯粹的 "ID"/"编号" 保留原样，否则标签会变成空字符串。
        String stripped = text.replaceAll("(?<=[\\u4e00-\\u9fa5])(ID|Id|id|编号|主键)$", "").trim();
        return stripped.isEmpty() ? text : stripped;
    }

    /**
     * 判断注释是否「整体就是编号映射」、不含语义标题。
     *
     * <p>两种写法都算：{@code 1男 2女 0未知}（剥掉所有枚举对后只剩分隔符）、
     * {@code 1APP-GPS 2WiFi 3考勤机 4补卡}（分词后每个词都是「数字开头+非数字」，
     * 枚举对正则会截断 APP-GPS 这类带连字符的文案，所以补一条分词规则）。
     * 「3个工作日内完成」这类以数字开头的普通说明不属于映射：剥对后剩正文/只有单个词。
     */
    static boolean isPureEnumMapping(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // 规则 a：剥掉所有枚举对后只剩分隔符，且至少 2 个映射对（真映射至少两组对照）。
        // 只查剩余为空不够——「3个工作日内完成」会被 {1,10} 整段吞成一个"对"误判
        java.util.regex.Matcher pm = ENUM_PAIR.matcher(text);
        int pairs = 0;
        while (pm.find()) {
            pairs++;
        }
        if (pairs >= 2 && pm.reset().replaceAll(" ").replaceAll("[\\s,，、;；。]*", "").isEmpty()) {
            return true;
        }
        // 规则 b：分词后每个词都是「数字开头+非数字」（1男 / 1APP-GPS / 0-待付款），
        // 覆盖 APP-GPS 这类被枚举对正则截断、剩余不空的文案
        String[] tokens = text.trim().split("[\\s,，、]+");
        if (tokens.length < 2) {
            return false;
        }
        for (String token : tokens) {
            if (!token.matches("\\d{1,2}\\D.*")) {
                return false;
            }
        }
        return true;
    }

    /** 枚举项：数字 + 中文/字母文案，数字前不能紧邻数字（避免把 123ABC 拆成 23+ABC） */
    private static final java.util.regex.Pattern ENUM_PAIR = java.util.regex.Pattern
            .compile("(?<![\\dA-Za-z])(\\d{1,2})\\s*[-:：=]?\\s*([\\u4e00-\\u9fa5A-Za-z]{1,10})");

    /**
     * 从列注释解析枚举选项，返回 {@code [value, label]} 列表；解析不出返回空列表。
     *
     * <p>支持 {@code 1启用0停用}、{@code 0正常 1停用}、{@code 0-待付款 1-已付款 2-已发货}、
     * {@code 状态(待执行/已执行)}（按顺序映射 0/1）四种写法。
     * 纯文案斜杠写法只在列名本身是枚举语义（status/type/level/flag/state）时才采用，
     * 避免「商品/物料名称」这类注释被误拆。
     */
    public static List<String[]> parseEnumOptions(String comment, String columnName, String javaType) {
        List<String[]> out = new ArrayList<>();
        if (comment == null || comment.isBlank()) {
            return out;
        }
        String text = comment.trim();
        var m = COMMENT_TITLE_SPLIT.matcher(text);
        if (m.matches()) {
            text = text.substring(m.group(1).length());
        }
        text = text.replaceAll("[()（）\\[\\]【】]", "").trim();

        var pm = ENUM_PAIR.matcher(text);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        while (pm.find()) {
            String v = pm.group(1);
            if (seen.add(v)) {
                out.add(new String[]{v, pm.group(2)});
            }
        }
        if (out.size() >= 2) {
            return out;
        }

        out.clear();
        if (!looksEnumColumnName(columnName) || !text.contains("/")) {
            return out;
        }
        String[] parts = text.split("\\s*/\\s*");
        if (parts.length < 2 || parts.length > 6) {
            return out;
        }
        for (int i = 0; i < parts.length; i++) {
            String label = parts[i].trim();
            if (label.isEmpty() || label.length() > 8 || label.matches(".*\\d.*")) {
                out.clear();
                return out;
            }
            out.add(new String[]{String.valueOf(i), label});
        }
        return out;
    }

    private static boolean looksEnumColumnName(String columnName) {
        if (columnName == null) {
            return false;
        }
        String n = columnName.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith("status") || n.endsWith("state") || n.endsWith("type")
                || n.endsWith("level") || n.endsWith("flag") || n.endsWith("kind");
    }

    /** 枚举转前端常量：数值列给数字字面量，否则给字符串（el-select 用严格比较，类型错了就不回显） */
    private static String enumJs(List<String[]> opts, boolean isNumber) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < opts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String v = opts.get(i)[0];
            String t = opts.get(i)[1].replace("\\", "\\\\").replace("'", "\\'");
            sb.append("{value: ").append(isNumber ? v : "'" + v + "'").append(", label: '").append(t).append("'}");
        }
        return sb.append("]").toString();
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
            // 注释里写明的枚举优先于字典表：生成出来的独立项目常常没有 sys_dict 数据，
            // 字典接口取不到值时整列会显示空白，而注释是导入时就确定的真实语义
            List<String[]> enumOpts = parseEnumOptions(c.getColumnComment(), c.getColumnName(), javaType);
            boolean hasEnum = enumOpts.size() >= 2;
            boolean hasDict = dictType != null && !dictType.isBlank() && !hasEnum;
            // 枚举塞进数字框/文本框没有意义，统一升级成下拉
            if (hasEnum && ("number".equals(htmlType) || "input".equals(htmlType))) {
                htmlType = "select";
            }
            cm.put("javaField", c.getJavaField());
            cm.put("capField", capitalize(c.getJavaField()));
            // 数据库列名：查重、导出表头映射等需要按真实列名拼 SQL
            cm.put("dbField", c.getColumnName());
            cm.put("javaType", javaType);
            // 原始注释：枚举说明等仍可能在少数场景被引用（剥掉引号，防止打进字符串字面量打坏生成代码，坑 52）
            cm.put("columnComment", sanitizeComment(c.getColumnComment()));
            // 页面显示名：剥掉注释里的枚举说明（状态:1启用0停用 -> 状态）；
            // 注释整体是编号映射（1男 2女 0未知）时回落列名，映射串不再整段上屏。
            // vue 模板的 label/placeholder/校验消息统一用这个，原始注释不再直接上屏
            cm.put("commentLabel", sanitizeComment(commentLabel(c.getColumnComment(), c.getColumnName())));
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
            // V6.6 文件能力包：图片/文件上传控件派生标志（唯一实现在此，模板只消费）
            cm.put("isImage", "image".equals(htmlType));
            cm.put("isFile", "file".equals(htmlType));
            // V6.8 多图字段：JSON 数组字符串列，列表取首图、详情/表单渲染多图组件
            cm.put("isMultiImage", "multiImage".equals(htmlType));
            // 上传类控件统一标志（image/file/multiImage）——统计口径只认这一个，避免各处各写一遍
            cm.put("isUpload", "image".equals(htmlType) || "file".equals(htmlType) || "multiImage".equals(htmlType));
            // P1-2 列宽自适应：按控件类型与宽度语义给默认值，避免所有列等宽
            cm.put("colWidth", columnWidth(c, htmlType, javaType));
            cm.put("dictType", hasDict ? dictType.trim() : "");
            cm.put("hasDict", hasDict);
            cm.put("hasEnum", hasEnum);
            cm.put("enumJs", hasEnum ? enumJs(enumOpts, isNumber) : "[]");
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
