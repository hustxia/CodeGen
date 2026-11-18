package com.codegen.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.entity.GenTemplate;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.GenTemplateMapper;
import com.codegen.config.GenOutputProperties;
import com.codegen.common.exception.BusinessException;
import com.codegen.support.GenPathResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代码模板渲染引擎：模板存库（表级覆盖优先，回落全局），Freemarker 渲染。
 * 渲染结果与二期硬编码 GenTemplates 逐字节一致（对拍守护）。
 */
@Component
public class GenTemplateEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GenTemplateEngine.class);

    private static final Configuration CFG = new Configuration(Configuration.VERSION_2_3_31);

    static {
        CFG.setDefaultEncoding("UTF-8");
        CFG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        CFG.setLogTemplateExceptions(false);
        CFG.setWrapUncheckedExceptions(true);
    }

    @Autowired
    private GenTemplateMapper genTemplateMapper;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenTableColumnMapper genTableColumnMapper;

    @Autowired
    private com.codegen.mapper.SysProjectMapper sysProjectMapper;

    @Autowired
    private GenOutputProperties outputProperties;

    /**
     * 把配置里的基座根包名注入模板模型（生成物 import 的 Result/BaseEntity 等按它拼接）
     */
    @PostConstruct
    public void initRootPackage() {
        GenTemplateModel.setRootPkg(outputProperties.getRootPackage());
    }

    /**
     * 全局模板内存缓存：tplCode -> 模板。首次渲染 1 条 SQL 全量载入，省掉每模板一次查询。
     * 模板增删改（含种子升级）后由 {@link #evict()} 失效。
     */
    private volatile Map<String, ResolvedTemplate> globalCache;

    /**
     * 表级覆盖缓存：{@code tableId|tplCode} -> 模板（覆盖行很少，只缓存命中的）
     */
    private final Map<String, ResolvedTemplate> tableCache = new ConcurrentHashMap<>();

    /**
     * 模板变更后清空缓存（保存 / 删除 / 复制 / 回滚 / 种子同步）
     */
    public void evict() {
        globalCache = null;
        tableCache.clear();
    }

    /**
     * 已解析模板（内容 + 路径模式）
     */
    public record ResolvedTemplate(String content, String pathPattern) {
    }

    /**
     * 模板解析器：按编码取模板（测试侧可从 classpath 加载）
     */
    public interface TemplateResolver {
        ResolvedTemplate resolve(String tplCode);
    }

    /**
     * 渲染单表全部生成物（相对路径 -> 内容），开关裁剪逻辑与二期 GenTemplates.render 一致。
     * 主子表：主表会额外加载其子表配置参与渲染。
     */
    public Map<String, String> render(GenTable table, List<GenTableColumn> cols) {
        return renderWithProject(table, cols, null);
    }

    /**
     * 渲染单表全部生成物（V3.0 带所属系统配置）。
     *
     * <p>单独起名字而不是重载 {@code render(table, cols, ...)}：
     * 重载会让 {@code render(table, cols, null)} 出现二义性（ChildInfo 还是 ProjectConfig），
     * 调用方每处都得强转，不如一个明确的方法名。
     *
     * @param project 所属系统配置，为 null 时按全局 yml 默认值渲染
     */
    public Map<String, String> renderWithProject(GenTable table, List<GenTableColumn> cols,
                                                 GenTemplateModel.ProjectConfig project) {
        Long tableId = table.getId() == null ? 0L : table.getId();
        List<GenTemplateModel.ChildInfo> children = loadChildren(table);
        return render(table, cols, children, tplCode -> resolveFromDb(tplCode, tableId), project);
    }

    /**
     * 渲染单表全部生成物（无子表，兼容旧签名）
     */
    public static Map<String, String> render(GenTable table, List<GenTableColumn> cols, TemplateResolver resolver) {
        return render(table, cols, (List<GenTemplateModel.ChildInfo>) null, resolver);
    }

    /**
     * 加载子表配置：{@code gen_table.master_table_id = 当前表id} 的**全部**子表（V4.4 一主多子）。
     *
     * <p>P3-1 起递归一层：子表自己的子表也会带出来，最多三层（订单→明细→批次）。
     * 再深就不递归了：更深的层级在真实业务里通常需要独立页面，
     * 强行塞进一个抽屉里反而难用，而且生成的代码量会翻倍。
     *
     * <p>V4.4 修复：原先 {@code LIMIT 1} 只取 id 最小的一张子表，
     * 多张子表挂同一主表时其余被静默丢弃——这是详情多 tab 与级联删除的前置缺口。
     */
    private List<GenTemplateModel.ChildInfo> loadChildren(GenTable table) {
        return loadChildren(table, 1);
    }

    private List<GenTemplateModel.ChildInfo> loadChildren(GenTable table, int depth) {
        if (table == null || table.getId() == null || depth > 2) {
            return List.of();
        }
        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTable::getMasterTableId, table.getId());
        wrapper.orderByAsc(GenTable::getId);
        List<GenTable> childTables = genTableMapper.selectList(wrapper);
        if (childTables.isEmpty()) {
            return List.of();
        }
        // V6.0 F-07：跨系统引用生成期拦截。多模块/微服务结构下子表产物必须与主表同模块归属，
        // 跨系统引用是配置事故（模板/菜单/级联删除都会按「同工程」假设走），静默生成只会把事故搬进产物。
        // 双侧均 null 视为存量数据放行（V3.0 之前的表没绑系统）。
        for (GenTable childTable : childTables) {
            if (table.getProjectId() != null && childTable.getProjectId() != null
                    && !table.getProjectId().equals(childTable.getProjectId())) {
                throw new BusinessException(400, "主子表跨系统引用：子表[" + childTable.getTableName()
                        + "]与主表[" + table.getTableName() + "]不属于同一个系统，"
                        + "请先在生成台账里修正子表的所属系统");
            }
        }
        // 子表列一次查完：逐子表 selectList 会让「一主多子」每次渲染多出 N 次往返，
        // 而主子表场景下渲染是逐文件调用的，放大效应明显
        List<Long> childIds = new ArrayList<>(childTables.size());
        for (GenTable childTable : childTables) {
            childIds.add(childTable.getId());
        }
        LambdaQueryWrapper<GenTableColumn> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.in(GenTableColumn::getTableId, childIds);
        colWrapper.orderByAsc(GenTableColumn::getTableId).orderByAsc(GenTableColumn::getSort);
        Map<Long, List<GenTableColumn>> colsByChild = new LinkedHashMap<>();
        for (GenTableColumn col : genTableColumnMapper.selectList(colWrapper)) {
            colsByChild.computeIfAbsent(col.getTableId(), k -> new ArrayList<>()).add(col);
        }
        List<GenTemplateModel.ChildInfo> result = new ArrayList<>();
        for (GenTable childTable : childTables) {
            List<GenTableColumn> childCols =
                    colsByChild.getOrDefault(childTable.getId(), List.of());
            result.add(new GenTemplateModel.ChildInfo(childTable, childCols, loadChildren(childTable, depth + 1)));
        }
        return result;
    }

    /**
     * 渲染单表全部生成物（可注入模板解析器与子表信息，便于对拍测试）
     */
    public static Map<String, String> render(GenTable table, List<GenTableColumn> cols,
                                             List<GenTemplateModel.ChildInfo> children,
                                             TemplateResolver resolver) {
        return render(table, cols, children, resolver, null);
    }

    /**
     * 渲染单表全部生成物（单子表兼容签名，V4.4 起内部转列表）
     */
    public static Map<String, String> render(GenTable table, List<GenTableColumn> cols,
                                             GenTemplateModel.ChildInfo child, TemplateResolver resolver) {
        return render(table, cols, child == null ? List.of() : List.of(child), resolver, null);
    }

    /**
     * 渲染单表全部生成物（V3.0：带所属系统配置的三级回落；V4.4：子表列表化）
     */
    public static Map<String, String> render(GenTable table, List<GenTableColumn> cols,
                                             List<GenTemplateModel.ChildInfo> children,
                                             TemplateResolver resolver,
                                             GenTemplateModel.ProjectConfig project) {
        Map<String, Object> model = GenTemplateModel.build(table, cols, children, project);
        boolean genService = GenTemplateModel.on(table.getGenService());
        boolean genPageApi = GenTemplateModel.on(table.getGenPageApi());
        boolean genDetailApi = GenTemplateModel.on(table.getGenDetailApi());
        boolean genAddApi = GenTemplateModel.on(table.getGenAddApi());
        boolean genEditApi = GenTemplateModel.on(table.getGenEditApi());
        boolean genDeleteApi = GenTemplateModel.on(table.getGenDeleteApi());
        boolean genFrontend = GenTemplateModel.on(table.getGenFrontend());
        boolean needDto = genPageApi || genDetailApi || genAddApi || genEditApi || genDeleteApi;
        // 从表不单独出 Controller / api.js / 页面：它的增删改由主表的 page/with-children/save-with-children 承载，
        // 否则同模块下从表会覆盖主表的 index.vue（主子表默认同一 moduleName）
        boolean isChild = table.getMasterTableId() != null;

        Map<String, String> files = new LinkedHashMap<>();
        if (GenTemplateModel.on(table.getGenEntity())) {
            put(files, "entity", resolver, model, project);
        }
        // ServiceImpl 继承 ServiceImpl<XxxMapper, Xxx>，Mapper 是硬依赖：
        // 关了 mapper 开关也要生成，否则生成的 ServiceImpl 必然编译失败（自检会报 cannot find symbol）
        if (GenTemplateModel.on(table.getGenMapper()) || genService) {
            put(files, "mapper", resolver, model, project);
        }
        if (genService) {
            put(files, "service", resolver, model, project);
            // V4.4 模板分道：按表形态派发（判据唯一实现在 GenTemplateModel.serviceImplTplCode）
            put(files, GenTemplateModel.serviceImplTplCode(model), resolver, model, project);
        }
        if (genPageApi && needDto) {
            put(files, "pageDto", resolver, model, project);
        }
        if ((genAddApi || genEditApi) && needDto) {
            put(files, "saveDto", resolver, model, project);
        }
        // VO 除了 Controller 自己用，主表的 with-children 也会引用从表 VO
        if (GenTemplateModel.on(table.getGenController()) || isChild) {
            put(files, "vo", resolver, model, project);
        }
        if (GenTemplateModel.on(table.getGenController()) && !isChild) {
            put(files, "controller", resolver, model, project);
            // V6.4 P0-3：接口-perms 映射清单随 Controller 同源生成（PermissionChecker 集中校验）。
            // 产物为超集映射：未启用的端点不会命中，无须按 gen 开关裁剪；
            // 多模块归属 service（与 controller 同层，service 模块依赖 common 可见 ApiPermContributor）
            String permsPrefix = GenPathResolver.moduleRelPrefix("controller",
                    project == null ? null : project.projectStructure(),
                    project == null ? null : project.rootPackage());
            files.put(permsPrefix + apiPermsPath(model), apiPermsJava(model));
        }
        if (genFrontend && GenTemplateModel.on(table.getGenApiJs()) && !isChild) {
            put(files, "apiJs", resolver, model, project);
        }
        if (genFrontend && GenTemplateModel.on(table.getGenVuePage()) && !isChild) {
            put(files, "vuePage", resolver, model, project);
        }
        // V6.5 移动端：系统 mobile_enabled=1 且该表出前端页面的非从表/非树表 → 动态页配置文件。
        // 走 Java 方法而不是 gen_template（V6.4 ApiPerms 同款理由：库模板优先于 classpath，
        // 新模板还得过 Seeder 同步；纯 Java 生成零模板依赖）。relPath 前缀 mobile/ = 第三侧 MOBILE。
        if (genFrontend && GenTemplateModel.on(table.getGenVuePage()) && !isChild
                && !Boolean.TRUE.equals(model.get("genTree"))
                && GenTemplateModel.resolveMobile(project == null ? null : project.mobileEnabled())) {
            files.put(mobileConfigRelPath(model), mobileTableConfigJs(model));
        }
        // V4.5 打印单据：开关与前置条件都在 genPrint 键里算好（resolveGenPrint 唯一实现），
        // 引擎只认结果，不再重复判定
        if (Boolean.TRUE.equals(model.get("genPrint"))) {
            put(files, "printPage", resolver, model, project);
        }
        // V6.7 产物测试出厂：hasControllerTest（开关+出Controller的非从表）与 testEnabled&&genEntity
        // 都在模型里算好，引擎只认结果。测试文件只调 generated 端点，从表随主表一起被测
        if (Boolean.TRUE.equals(model.get("hasControllerTest"))) {
            put(files, "crudControllerTest", resolver, model, project);
        }
        if (Boolean.TRUE.equals(model.get("testEnabled")) && GenTemplateModel.on(table.getGenEntity()) && !isChild) {
            put(files, "testSchemaTable", resolver, model, project);
        }
        return files;
    }

    /**
     * 渲染共享说明文件（readme 同样走表级覆盖优先：以 ZIP 首表解析，回落全局）。
     *
     * <p>V4.5 起传首表的渲染模型：readme 要说明「本 ZIP 是否含打印页」，
     * 空模型说不了这句话。表级覆盖命中时配置以覆盖行为准的表为准。
     */
    public Map<String, String> renderShared(GenTable table, GenTemplateModel.ProjectConfig project) {
        ResolvedTemplate tpl = resolveFromDb("readme", table == null || table.getId() == null ? 0L : table.getId());
        Map<String, Object> model = table == null
                ? Map.of()
                : GenTemplateModel.build(table, List.of(), (List<GenTemplateModel.ChildInfo>) null, project);
        Map<String, String> shared = new LinkedHashMap<>();
        shared.put(renderString(tpl.pathPattern(), Map.of()), renderString(tpl.content(), model));
        // V6.1：项目内任一表绑定 flow_code → 注入待办中心页 + 全局 flow api + 路由说明
        shared.putAll(renderFlowShared(table));
        // V6.3：系统 pay_enabled=1 → 注入收银台 + 支付记录页 + 全局 pay api + 路由说明
        shared.putAll(renderPayShared(table));
        return shared;
    }

    /**
     * V6.1 待办中心共享文件：首表所属系统内有任何绑定流程的表才产出（否则返回空 Map，零侵入）。
     */
    public Map<String, String> renderFlowShared(GenTable table) {
        if (table == null || table.getProjectId() == null) {
            return Map.of();
        }
        Long flowBound = genTableMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenTable>()
                .eq(GenTable::getProjectId, table.getProjectId())
                .isNotNull(GenTable::getFlowCode)
                .ne(GenTable::getFlowCode, ""));
        if (flowBound == null || flowBound == 0) {
            return Map.of();
        }
        try {
            ResolvedTemplate api = resolveFromDb("flowApi", 0L);
            ResolvedTemplate page = resolveFromDb("flowTodo", 0L);
            Map<String, String> shared = new LinkedHashMap<>();
            shared.put(renderString(api.pathPattern(), Map.of()), renderString(api.content(), Map.of()));
            shared.put(renderString(page.pathPattern(), Map.of()), renderString(page.content(), Map.of()));
            shared.put("flow-router-snippet.txt",
                    "// V6.1 待办中心路由：追加到 src/router/index.js 主布局 children\n"
                            + "{ path: 'flow/todo', name: 'FlowTodo', component: () => import('@/views/flow/todo.vue'), meta: { title: '待办中心' } }\n");
            return shared;
        } catch (Exception e) {
            // 模板缺失（老库未跑 Seeder）不阻断主产物；但要把原因吐进日志，静默吞掉排查不动
            log.warn("renderFlowShared 注入失败（projectId={}）: {}", table.getProjectId(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * V6.3 支付能力包共享文件：首表所属系统 pay_enabled=1 才产出（否则返回空 Map，零侵入）。
     * 收银台（渠道选择/出码/轮询）+ 支付记录页 + 全局 pay api + 路由说明。
     */
    public Map<String, String> renderPayShared(GenTable table) {
        if (table == null || table.getProjectId() == null) {
            return Map.of();
        }
        com.codegen.entity.SysProject project = sysProjectMapper.selectById(table.getProjectId());
        if (project == null || !GenTemplateModel.resolvePay(project.getPayEnabled())) {
            log.info("renderPayShared 跳过（projectId={}，project={}，payEnabled={}）",
                    table.getProjectId(), project == null ? "null" : project.getProjectCode(),
                    project == null ? null : project.getPayEnabled());
            return Map.of();
        }
        try {
            ResolvedTemplate api = resolveFromDb("payApi", 0L);
            ResolvedTemplate cashier = resolveFromDb("payCashier", 0L);
            ResolvedTemplate orderPage = resolveFromDb("payOrderPage", 0L);
            Map<String, String> shared = new LinkedHashMap<>();
            shared.put(renderString(api.pathPattern(), Map.of()), renderString(api.content(), Map.of()));
            shared.put(renderString(cashier.pathPattern(), Map.of()), renderString(cashier.content(), Map.of()));
            shared.put(renderString(orderPage.pathPattern(), Map.of()), renderString(orderPage.content(), Map.of()));
            shared.put("pay-router-snippet.txt",
                    "// V6.3 收银台与支付记录路由：追加到 src/router/index.js 主布局 children\n"
                            + "{ path: 'pay/cashier', name: 'PayCashier', component: () => import('@/views/pay/cashier.vue'), meta: { title: '收银台' } }\n"
                            + "{ path: 'pay/order', name: 'PayOrderPage', component: () => import('@/views/pay/order.vue'), meta: { title: '支付记录' } }\n");
            return shared;
        } catch (Exception e) {
            // 模板缺失（老库未跑 Seeder）不阻断主产物；但要把原因吐进日志，静默吞掉排查不动
            log.warn("renderPayShared 注入失败（projectId={}）: {}", table.getProjectId(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * V6.4 P0-3：接口-perms 映射清单落点（{backendBase}/common/auth/{Class}ApiPerms.java，
     * 与其他后端产物同口径走 ${backendBase} 前缀，写盘/预览按同一条路径判定链处理）。
     */
    private static String apiPermsPath(Map<String, Object> model) {
        String className = String.valueOf(model.get("className"));
        String backendBase = String.valueOf(model.get("backendBase"));
        return backendBase + "/common/auth/" + className + "ApiPerms.java";
    }

    /**
     * V6.5 移动端：动态页配置文件的落位路径（relPath 前缀 mobile/ = 第三侧 MOBILE）。
     * 写盘/ZIP 侧按前缀识别归属，mobileRoot 由 GenCodeServiceImpl.mobileRootOf 解析。
     */
    private static String mobileConfigRelPath(Map<String, Object> model) {
        return "mobile/src/config/tables/" + model.get("moduleName") + "/" + model.get("tableName") + ".js";
    }

    /**
     * V6.5 移动端表配置 JS：声明式数据源，scaffold-mobile 的动态页三件（list/detail/form）
     * 按 import.meta.glob 聚合消费——新增表零注册，pages.json 恒定不重建。
     * 列分类完全复用 {@link GenTemplateModel} 现有标记（htmlType/commentLabel/enumJs/isRequired），
     * 不新增模型；hasEnum 优先于 hasDict（与 PC vuePage 同一回落链）。
     */
    private static String mobileTableConfigJs(Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("// V6.5 移动端动态页配置：自动生成，请勿手工编辑（重新生成会覆盖）\n");
        sb.append("// 消费方：{frontendModule}-mobile 的 pages/dynamic/list|detail|form.vue\n");
        sb.append("export default {\n");
        sb.append("  table: '").append(model.get("tableName")).append("',\n");
        sb.append("  title: '").append(escJs(String.valueOf(model.get("comment")))).append("',\n");
        sb.append("  module: '").append(model.get("moduleName")).append("',\n");
        sb.append("  api: '").append(model.get("apiBase")).append("',\n");
        appendColGroup(sb, "search", model.get("vueQueryColumns"));
        appendColGroup(sb, "list", model.get("listColumns"));
        appendColGroup(sb, "form", model.get("formColumns"));
        sb.append("}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendColGroup(StringBuilder sb, String key, Object colsObj) {
        List<Map<String, Object>> cols = colsObj == null ? List.of() : (List<Map<String, Object>>) colsObj;
        sb.append("  ").append(key).append(": [\n");
        for (Map<String, Object> c : cols) {
            String htmlType = String.valueOf(c.get("htmlType"));
            // 移动端控件映射：select/radio → picker（uni-ui 无 el-select 形态）；
            // switch/number/date/datetime/textarea 原样可用；其余回落 input
            String ctrl = switch (htmlType) {
                case "select", "radio" -> "picker";
                case "switch", "number", "date", "datetime", "textarea" -> htmlType;
                default -> "input";
            };
            boolean hasEnum = Boolean.TRUE.equals(c.get("hasEnum"));
            boolean hasDict = Boolean.TRUE.equals(c.get("hasDict"));
            sb.append("    { field: '").append(c.get("javaField"))
                    .append("', label: '").append(escJs(String.valueOf(c.get("commentLabel"))))
                    .append("', ctrl: '").append(ctrl)
                    .append("', required: ").append(Boolean.TRUE.equals(c.get("isRequired")));
            if (hasEnum) {
                sb.append(", enum: ").append(c.get("enumJs"));
            }
            if (hasDict) {
                sb.append(", dict: '").append(c.get("dictType")).append("'");
            }
            sb.append(" },\n");
        }
        sb.append("  ],\n");
    }

    /** JS 单引号字符串转义（反斜杠/单引号/换行） */
    private static String escJs(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
    }

    /**
     * V6.4 P0-3：生成 {Class}ApiPerms —— 端点→perms 映射贡献者。
     * perms 值与 V3.0 五级回落/前端 v-hasPermi 同源：{permsPrefix}:{list|add|edit|remove}。
     * 读类端点统一挂 :list（菜单授权即含），写类端点按操作分派；未注册端点由 PermissionChecker
     * fail-closed 缺省兜底（GET 放行、写拒绝）。
     */
    private static String apiPermsJava(Map<String, Object> model) {
        String pkg = String.valueOf(model.get("pkg"));
        String className = String.valueOf(model.get("className"));
        String prefix = String.valueOf(model.get("permsPrefix"));
        String module = String.valueOf(model.get("moduleName"));
        String base = module == null || module.isBlank() ? "" : "/" + module;
        boolean hasFlow = Boolean.TRUE.equals(model.get("hasFlow"));
        StringBuilder m = new StringBuilder();
        // 读类：page/tree/options/详情/导出/导入模板
        m.append("        m.put(\"POST ").append(base).append("/page\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/tree\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/options\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/bind-values\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/{id}\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/{id}/with-children\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"POST ").append(base).append("/export\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/import-template\", \"").append(prefix).append(":list\");\n");
        m.append("        m.put(\"GET ").append(base).append("/import/failed\", \"").append(prefix).append(":list\");\n");
        // 写类：新增/编辑/删除/导入/主子保存
        m.append("        m.put(\"POST ").append(base).append("\", \"").append(prefix).append(":add\");\n");
        m.append("        m.put(\"PUT ").append(base).append("\", \"").append(prefix).append(":edit\");\n");
        m.append("        m.put(\"POST ").append(base).append("/import\", \"").append(prefix).append(":add\");\n");
        m.append("        m.put(\"POST ").append(base).append("/save-with-children\", \"").append(prefix).append(":edit\");\n");
        m.append("        m.put(\"DELETE ").append(base).append("/{id}\", \"").append(prefix).append(":remove\");\n");
        if (hasFlow) {
            m.append("        m.put(\"POST ").append(base).append("/flow/submit/{id}\", \"")
                    .append(prefix).append(":edit\");\n");
        }
        return "package " + pkg + ".common.auth;\n\n"
                + "import org.springframework.stereotype.Component;\n\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n\n"
                + "/**\n"
                + " * " + className + " 端点-权限映射（代码生成器随 Controller 同源生成）。\n"
                + " * perms 值与菜单注册/前端 v-hasPermi 同源（" + prefix + ":*）。\n"
                + " */\n"
                + "@Component\n"
                + "public class " + className + "ApiPerms implements ApiPermContributor {\n\n"
                + "    @Override\n"
                + "    public Map<String, String> endpointPerms() {\n"
                + "        Map<String, String> m = new HashMap<>();\n"
                + m
                + "        return m;\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 渲染路由注册片段（每表一段，兼容旧签名：不带系统配置）
     */
    public String renderRouterSnippet(GenTable table) {
        return renderRouterSnippet(table, null);
    }

    /**
     * 渲染路由注册片段（每表一段）。
     *
     * <p>V4.5 起带系统配置：打印路由挂载条件走 genPrint 键，
     * 若系统默认开而表级未配置，不带配置渲染会丢打印路由（产物有页面没入口）。
     */
    public String renderRouterSnippet(GenTable table, GenTemplateModel.ProjectConfig project) {
        ResolvedTemplate tpl = resolveFromDb("routerSnippet", table.getId() == null ? 0L : table.getId());
        return renderString(tpl.content(),
                GenTemplateModel.build(table, List.of(), (List<GenTemplateModel.ChildInfo>) null, project));
    }

    /**
     * 渲染单个模板内容（校验/对拍共用）
     */
    public static String renderString(String content, Map<String, Object> model) {
        try {
            Template template = new Template("inline", new StringReader(content), CFG);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new BusinessException(400, "模板渲染失败：" + e.getMessage());
        }
    }

    /**
     * 渲染单个模板并入结果表。V5.5：多模块系统在这里按模板归属插入模块目录前缀
     * （{@code <stem>-domain/service/...}）——预览、写盘、ZIP 全部消费这把 relPath，
     * 在源头插一次即可三处一致（模块归属唯一实现在 GenPathResolver.moduleRelPrefix）。
     */
    private static void put(Map<String, String> files, String tplCode, TemplateResolver resolver,
                            Map<String, Object> model, GenTemplateModel.ProjectConfig project) {
        ResolvedTemplate tpl = resolver.resolve(tplCode);
        if (tpl == null) {
            throw new BusinessException(500, "模板缺失：" + tplCode);
        }
        String path = renderString(tpl.pathPattern(), model);
        String prefix = GenPathResolver.moduleRelPrefix(tplCode,
                project == null ? null : project.projectStructure(),
                project == null ? null : project.rootPackage());
        files.put(prefix.isEmpty() ? path : prefix + path, renderString(tpl.content(), model));
    }

    /**
     * 库取模板：表级覆盖优先，回落全局；仅取启用行。
     * 全局模板走内存缓存（1 条 SQL 全量载入），表级覆盖命中后也缓存。
     */
    private ResolvedTemplate resolveFromDb(String tplCode, Long tableId) {
        if (tableId != null && tableId > 0) {
            String key = tableId + "|" + tplCode;
            ResolvedTemplate cached = tableCache.get(key);
            if (cached != null) {
                return cached;
            }
            GenTemplate override = selectActive(tplCode, tableId);
            if (override != null) {
                ResolvedTemplate resolved = new ResolvedTemplate(override.getContent(), override.getPathPattern());
                tableCache.put(key, resolved);
                return resolved;
            }
        }
        ResolvedTemplate global = globalMap().get(tplCode);
        if (global == null) {
            throw new BusinessException(500, "模板缺失：" + tplCode + "（无全局模板）");
        }
        return global;
    }

    /**
     * 全局模板全量载入（双重检查，避免并发重复加载）
     */
    private Map<String, ResolvedTemplate> globalMap() {
        Map<String, ResolvedTemplate> local = globalCache;
        if (local == null) {
            synchronized (this) {
                local = globalCache;
                if (local == null) {
                    LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(GenTemplate::getTableId, 0L);
                    wrapper.eq(GenTemplate::getStatus, 1);
                    List<GenTemplate> rows = genTemplateMapper.selectList(wrapper);
                    Map<String, ResolvedTemplate> loaded = new LinkedHashMap<>();
                    for (GenTemplate row : rows) {
                        loaded.putIfAbsent(row.getTplCode(),
                                new ResolvedTemplate(row.getContent(), row.getPathPattern()));
                    }
                    local = loaded;
                    globalCache = loaded;
                }
            }
        }
        return local;
    }

    private GenTemplate selectActive(String tplCode, Long tableId) {
        LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTemplate::getTplCode, tplCode);
        wrapper.eq(GenTemplate::getTableId, tableId);
        wrapper.eq(GenTemplate::getStatus, 1);
        wrapper.last("LIMIT 1");
        return genTemplateMapper.selectOne(wrapper);
    }
}
