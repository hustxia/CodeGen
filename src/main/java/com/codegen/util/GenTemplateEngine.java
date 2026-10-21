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
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
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
        Long tableId = table.getId() == null ? 0L : table.getId();
        GenTemplateModel.ChildInfo child = loadChild(table);
        return render(table, cols, child, tplCode -> resolveFromDb(tplCode, tableId));
    }

    /**
     * 渲染单表全部生成物（无子表，兼容旧签名）
     */
    public static Map<String, String> render(GenTable table, List<GenTableColumn> cols, TemplateResolver resolver) {
        return render(table, cols, null, resolver);
    }

    /**
     * 加载子表配置：{@code gen_table.master_table_id = 当前表id} 的第一张表
     */
    private GenTemplateModel.ChildInfo loadChild(GenTable table) {
        if (table == null || table.getId() == null) {
            return null;
        }
        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTable::getMasterTableId, table.getId());
        wrapper.last("LIMIT 1");
        GenTable childTable = genTableMapper.selectOne(wrapper);
        if (childTable == null) {
            return null;
        }
        LambdaQueryWrapper<GenTableColumn> colWrapper = new LambdaQueryWrapper<>();
        colWrapper.eq(GenTableColumn::getTableId, childTable.getId());
        colWrapper.orderByAsc(GenTableColumn::getSort);
        return new GenTemplateModel.ChildInfo(childTable, genTableColumnMapper.selectList(colWrapper));
    }

    /**
     * 渲染单表全部生成物（可注入模板解析器与子表信息，便于对拍测试）
     */
    public static Map<String, String> render(GenTable table, List<GenTableColumn> cols,
                                             GenTemplateModel.ChildInfo child, TemplateResolver resolver) {
        Map<String, Object> model = GenTemplateModel.build(table, cols, child);
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
            put(files, "entity", resolver, model);
        }
        // ServiceImpl 继承 ServiceImpl<XxxMapper, Xxx>，Mapper 是硬依赖：
        // 关了 mapper 开关也要生成，否则生成的 ServiceImpl 必然编译失败（自检会报 cannot find symbol）
        if (GenTemplateModel.on(table.getGenMapper()) || genService) {
            put(files, "mapper", resolver, model);
        }
        if (genService) {
            put(files, "service", resolver, model);
            put(files, "serviceImpl", resolver, model);
        }
        if (genPageApi && needDto) {
            put(files, "pageDto", resolver, model);
        }
        if ((genAddApi || genEditApi) && needDto) {
            put(files, "saveDto", resolver, model);
        }
        // VO 除了 Controller 自己用，主表的 with-children 也会引用从表 VO
        if (GenTemplateModel.on(table.getGenController()) || isChild) {
            put(files, "vo", resolver, model);
        }
        if (GenTemplateModel.on(table.getGenController()) && !isChild) {
            put(files, "controller", resolver, model);
        }
        if (genFrontend && GenTemplateModel.on(table.getGenApiJs()) && !isChild) {
            put(files, "apiJs", resolver, model);
        }
        if (genFrontend && GenTemplateModel.on(table.getGenVuePage()) && !isChild) {
            put(files, "vuePage", resolver, model);
        }
        return files;
    }

    /**
     * 渲染 ZIP 附带的共享说明文件（readme 同样走表级覆盖优先：以 ZIP 首表解析，回落全局）
     */
    public Map<String, String> renderShared(Long tableId) {
        ResolvedTemplate tpl = resolveFromDb("readme", tableId == null ? 0L : tableId);
        Map<String, String> shared = new LinkedHashMap<>();
        shared.put(renderString(tpl.pathPattern(), Map.of()), renderString(tpl.content(), Map.of()));
        return shared;
    }

    /**
     * 渲染路由注册片段（每表一段）
     */
    public String renderRouterSnippet(GenTable table) {
        ResolvedTemplate tpl = resolveFromDb("routerSnippet", table.getId() == null ? 0L : table.getId());
        return renderString(tpl.content(), GenTemplateModel.build(table, List.of()));
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

    private static void put(Map<String, String> files, String tplCode, TemplateResolver resolver,
                            Map<String, Object> model) {
        ResolvedTemplate tpl = resolver.resolve(tplCode);
        if (tpl == null) {
            throw new BusinessException(500, "模板缺失：" + tplCode);
        }
        files.put(renderString(tpl.pathPattern(), model), renderString(tpl.content(), model));
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
