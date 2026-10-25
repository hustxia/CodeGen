package com.codegen.support;

import com.codegen.config.GenOutputProperties;
import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 落盘路径回落链测试。
 *
 * <p>这条链出过真实事故：模板层接了系统配置、写盘层没接，
 * 表现是「预览路径对、文件写进别的仓库」，而且不报错。
 * 这里把四种来源组合全部钉死，谁再改乱顺序，测试直接红。
 *
 * <p>四个字段（backendRoot / frontendRoot / backendBase / frontendPath）×
 * 四种组合（表级有 / 系统有 / 模块有 / 全空）= 主要用例，全部参数化。
 */
@DisplayName("GenPathResolver 四级回落")
class GenPathResolverTest {

    private static final String GLOBAL_BACKEND_ROOT = "/repos/global-server";
    private static final String GLOBAL_FRONTEND_ROOT = "/repos/global-web";
    private static final String GLOBAL_BACKEND_PATH = "global-server/src/main/java/com/global";
    private static final String GLOBAL_FRONTEND_PATH = "global-web";
    private static final String GLOBAL_PKG = "com.global";
    private static final String GLOBAL_GATEWAY = "global";

    /**
     * 全局配置 + 一个 demo 模块，模块是「表级选了 target_module」时才参与的那一层
     */
    private static GenOutputProperties globalOutput() {
        GenOutputProperties out = new GenOutputProperties();
        out.setBackendRoot(GLOBAL_BACKEND_ROOT);
        out.setFrontendRoot(GLOBAL_FRONTEND_ROOT);
        out.setBackendPath(GLOBAL_BACKEND_PATH);
        out.setFrontendPath(GLOBAL_FRONTEND_PATH);
        out.setPackageName(GLOBAL_PKG);
        out.setGatewayPrefix(GLOBAL_GATEWAY);

        GenOutputProperties.ModuleConfig module = new GenOutputProperties.ModuleConfig();
        module.setBackendRoot("/repos/module-server");
        module.setBackendPath("module-server/src/main/java/com/module");
        module.setPackageName("com.module");
        module.setGatewayPrefix("module");
        module.setFrontendPath("module-web");
        out.setModules(java.util.Map.of("demo", module));
        return out;
    }

    private static GenTable table() {
        GenTable table = new GenTable();
        table.setTableName("demo_goods");
        table.setClassName("DemoGoods");
        return table;
    }

    private static SysProject project() {
        SysProject project = new SysProject();
        project.setProjectCode("mkt");
        project.setBackendRoot("/repos/mkt-server");
        project.setFrontendRoot("/repos/mkt-web");
        project.setBackendPath("mkt-server/src/main/java/com/mkt");
        project.setFrontendPath("mkt-web");
        project.setRootPackage("com.mkt");
        project.setGatewayPrefix("mkt");
        return project;
    }

    @Nested
    @DisplayName("后端仓库根 backendRoot")
    class BackendRoot {

        @Test
        @DisplayName("表级有值 → 表级优先，系统配置不生效")
        void tableWins() {
            GenTable t = table();
            t.setBackendRoot("/repos/table-server");
            assertEquals("/repos/table-server",
                    GenPathResolver.backendRoot(t, project(), globalOutput()));
        }

        @Test
        @DisplayName("表级为空 → 系统层生效（V3.0 事故点）")
        void projectWins() {
            assertEquals("/repos/mkt-server",
                    GenPathResolver.backendRoot(table(), project(), globalOutput()));
        }

        @Test
        @DisplayName("表级/系统都为空 → 目标模块生效")
        void moduleWins() {
            GenTable t = table();
            t.setTargetModule("demo");
            assertEquals("/repos/module-server",
                    GenPathResolver.backendRoot(t, null, globalOutput()));
        }

        @Test
        @DisplayName("全空 → 全局兜底")
        void globalFallback() {
            assertEquals(GLOBAL_BACKEND_ROOT,
                    GenPathResolver.backendRoot(table(), null, globalOutput()));
        }

        @Test
        @DisplayName("表级为空且未绑系统 → 不允许回落到空串（空串会把文件写到盘根）")
        void neverBlank() {
            GenOutputProperties empty = new GenOutputProperties();
            assertEquals("", GenPathResolver.backendRoot(table(), null, empty));
        }
    }

    @Nested
    @DisplayName("前端仓库根 frontendRoot")
    class FrontendRoot {

        @Test
        @DisplayName("表级 > 系统 > 全局")
        void chain() {
            GenTable t = table();
            assertEquals(GLOBAL_FRONTEND_ROOT, GenPathResolver.frontendRoot(t, null, globalOutput()));
            assertEquals("/repos/mkt-web", GenPathResolver.frontendRoot(t, project(), globalOutput()));
            t.setFrontendRoot("/repos/table-web");
            assertEquals("/repos/table-web", GenPathResolver.frontendRoot(t, project(), globalOutput()));
        }
    }

    @Nested
    @DisplayName("后端相对路径 backendBase")
    class BackendBase {

        @ParameterizedTest(name = "表级={0} 系统={1} 模块={2} → {3}")
        @CsvSource({
                // 表级有值：其余都不看
                "tbl-server/src/main/java/com/tbl, mkt-server/src/main/java/com/mkt, '', tbl-server/src/main/java/com/tbl",
                // 表级空、系统有值：系统生效（这条以前会退到全局，文件写错仓库）
                "'',                                  mkt-server/src/main/java/com/mkt, '', mkt-server/src/main/java/com/mkt",
                // 表级/系统空、模块有值：模块生效
                "'',                                  '',                               demo, module-server/src/main/java/com/module",
                // 全空：全局兜底
                "'',                                  '',                               '', global-server/src/main/java/com/global"
        })
        @DisplayName("四级回落")
        void chain(String tablePath, String projectPath, String module, String expected) {
            GenTable t = table();
            t.setBackendPath(blankToNull(tablePath));
            if (module != null && !module.isBlank()) {
                t.setTargetModule(module);
            }
            SysProject p = project();
            p.setBackendPath(projectPath);
            assertEquals(expected, GenPathResolver.backendBase(t, p, globalOutput()));
        }

        @Test
        @DisplayName("四级都空 → 按包名推导，而不是返回空串")
        void deriveFromPackage() {
            GenTable t = table();
            SysProject p = new SysProject();
            p.setRootPackage("com.acme.shop");
            GenOutputProperties out = new GenOutputProperties();
            out.setBackendPath("");
            assertEquals("src/main/java/com/acme/shop",
                    GenPathResolver.backendBase(t, p, out));
        }
    }

    @Nested
    @DisplayName("前端子目录 frontendPath")
    class FrontendPath {

        @Test
        @DisplayName("系统 > 模块 > 全局；都没有返回空串（前端工程就在 root）")
        void chain() {
            assertEquals("mkt-web", GenPathResolver.frontendPath(table(), project(), globalOutput()));

            GenTable t = table();
            t.setTargetModule("demo");
            assertEquals("module-web", GenPathResolver.frontendPath(t, null, globalOutput()));

            assertEquals(GLOBAL_FRONTEND_PATH,
                    GenPathResolver.frontendPath(table(), null, globalOutput()));

            GenOutputProperties out = new GenOutputProperties();
            out.setFrontendPath("");
            assertEquals("", GenPathResolver.frontendPath(table(), null, out));
        }
    }

    @Nested
    @DisplayName("包名与网关前缀")
    class PackageAndGateway {

        @Test
        @DisplayName("包名：表级 > 系统根包 > 模块 > 全局")
        void packageChain() {
            GenTable t = table();
            assertEquals(GLOBAL_PKG, GenPathResolver.packageName(t, null, globalOutput()));

            t.setTargetModule("demo");
            assertEquals("com.module", GenPathResolver.packageName(t, null, globalOutput()));

            assertEquals("com.mkt", GenPathResolver.packageName(table(), project(), globalOutput()));

            GenTable own = table();
            own.setPackageName("com.demo.order");
            assertEquals("com.demo.order",
                    GenPathResolver.packageName(own, project(), globalOutput()));
        }

        @Test
        @DisplayName("网关前缀：表级 > 系统 > 模块 > 全局")
        void gatewayChain() {
            assertEquals(GLOBAL_GATEWAY,
                    GenPathResolver.gatewayPrefix(table(), null, globalOutput()));
            assertEquals("mkt",
                    GenPathResolver.gatewayPrefix(table(), project(), globalOutput()));
        }
    }

    @Nested
    @DisplayName("V5.5 多模块：模块归属与相对路径前缀")
    class MultiModule {

        @Test
        @DisplayName("moduleOfTpl：entity/mapper→domain，controller/service 系/dto/vo→service，前端产物 null")
        void moduleOfTplMapping() {
            assertEquals("domain", GenPathResolver.moduleOfTpl("entity"));
            assertEquals("domain", GenPathResolver.moduleOfTpl("mapper"));
            assertEquals("service", GenPathResolver.moduleOfTpl("controller"));
            assertEquals("service", GenPathResolver.moduleOfTpl("service"));
            // serviceImpl* 前缀判断必须在 service 之前，三个分道模板都归 service
            assertEquals("service", GenPathResolver.moduleOfTpl("serviceImplSimple"));
            assertEquals("service", GenPathResolver.moduleOfTpl("serviceImplTree"));
            assertEquals("service", GenPathResolver.moduleOfTpl("serviceImplMasterdetail"));
            assertEquals("service", GenPathResolver.moduleOfTpl("pageDto"));
            assertEquals("service", GenPathResolver.moduleOfTpl("saveDto"));
            assertEquals("service", GenPathResolver.moduleOfTpl("vo"));
            // 前端/共享产物不属于后端子模块，多模块时原样落 backendRoot
            assertEquals(null, GenPathResolver.moduleOfTpl("apiJs"));
            assertEquals(null, GenPathResolver.moduleOfTpl("vuePage"));
            assertEquals(null, GenPathResolver.moduleOfTpl("printPage"));
            assertEquals(null, GenPathResolver.moduleOfTpl("routerSnippet"));
            assertEquals(null, GenPathResolver.moduleOfTpl("readme"));
            assertEquals(null, GenPathResolver.moduleOfTpl(null));
        }

        @Test
        @DisplayName("moduleRelPrefix：multi 时 <stem>-<module>/，单模块与前端产物为空串")
        void moduleRelPrefix() {
            // multi：后端产物带模块前缀（stem = rootPackage 最后一段）
            assertEquals("scm-domain/", GenPathResolver.moduleRelPrefix("entity", "multi", "com.demo.scm"));
            assertEquals("scm-service/", GenPathResolver.moduleRelPrefix("controller", "multi", "com.demo.scm"));
            // multi：前端产物仍落 backendRoot，无前缀
            assertEquals("", GenPathResolver.moduleRelPrefix("vuePage", "multi", "com.demo.scm"));
            // 单模块（null / mono）：一律无前缀，行为零变化
            assertEquals("", GenPathResolver.moduleRelPrefix("entity", null, "com.demo.scm"));
            assertEquals("", GenPathResolver.moduleRelPrefix("entity", "mono", "com.demo.scm"));
            // V6.0 起 micro 是分层结构，归属与 multi 一致（V5.5 时它还是未知值走单模块）
            assertEquals("scm-domain/", GenPathResolver.moduleRelPrefix("entity", "micro", "com.demo.scm"));
            // rootPackage 只有一段时 stem 取该段本身
            assertEquals("com-domain/", GenPathResolver.moduleRelPrefix("entity", "multi", "com"));
        }

        @Test
        @DisplayName("isMulti：仅 'multi'（忽略大小写与首尾空格）为真")
        void isMulti() {
            assertEquals(true, GenPathResolver.isMulti("multi"));
            assertEquals(true, GenPathResolver.isMulti(" MULTI "));
            assertEquals(false, GenPathResolver.isMulti(null));
            assertEquals(false, GenPathResolver.isMulti(""));
            assertEquals(false, GenPathResolver.isMulti("mono"));
            assertEquals(false, GenPathResolver.isMulti("micro"));
        }
    }

    @Nested
    @DisplayName("V6.0 微服务设施：micro 第三态")
    class MicroStructure {

        @Test
        @DisplayName("isMicro：仅 'micro'（忽略大小写与首尾空格）为真；multi/mono/null 均假")
        void isMicro() {
            assertEquals(true, GenPathResolver.isMicro("micro"));
            assertEquals(true, GenPathResolver.isMicro(" MICRO "));
            assertEquals(false, GenPathResolver.isMicro(null));
            assertEquals(false, GenPathResolver.isMicro(""));
            assertEquals(false, GenPathResolver.isMicro("multi"));
            assertEquals(false, GenPathResolver.isMicro("mono"));
            assertEquals(false, GenPathResolver.isMicro("Microservice"));
        }

        @Test
        @DisplayName("isMultiLike：multi 与 micro 都要求产物分层；未知值走单模块")
        void isMultiLike() {
            assertEquals(true, GenPathResolver.isMultiLike("multi"));
            assertEquals(true, GenPathResolver.isMultiLike("micro"));
            assertEquals(false, GenPathResolver.isMultiLike(null));
            assertEquals(false, GenPathResolver.isMultiLike("mono"));
            assertEquals(false, GenPathResolver.isMultiLike(""));
        }

        @Test
        @DisplayName("moduleRelPrefix：micro 的产物归属与 multi 完全一致（AC4 前提：multi 快照不动）")
        void microPrefixMatchesMulti() {
            for (String tpl : new String[]{"entity", "mapper", "controller", "service",
                    "serviceImplSimple", "pageDto", "vo", "apiJs", "vuePage", "printPage"}) {
                assertEquals(GenPathResolver.moduleRelPrefix(tpl, "multi", "com.wms"),
                        GenPathResolver.moduleRelPrefix(tpl, "micro", "com.wms"),
                        "micro 与 multi 归属不一致：" + tpl);
            }
            assertEquals("wms-domain/", GenPathResolver.moduleRelPrefix("entity", "micro", "com.wms"));
        }

        @Test
        @DisplayName("moduleOfTpl 不含 gateway：gen 模板产物永不进 gateway 模块（gateway 只有 scaffold 产物）")
        void noGatewayModule() {
            assertEquals(null, GenPathResolver.moduleOfTpl("gateway"));
            assertEquals(null, GenPathResolver.moduleOfTpl("readme"));
            assertEquals(null, GenPathResolver.moduleOfTpl("routerSnippet"));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
