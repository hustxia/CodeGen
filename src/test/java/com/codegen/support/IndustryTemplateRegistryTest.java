package com.codegen.support;

import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenPipelineAiVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.11 行业模板注册表：250+ 套预置包必须全部加载、key 唯一、表结构与 AI 建模产物同形；
 * 每套模板必须是完整系统（≥25 表 / 每表 ≥8 列），不能是 8 表玩具；
 * 克隆必须深拷贝（模板常驻内存，编辑器改表不能污染模板资产）。
 */
class IndustryTemplateRegistryTest {

    private final IndustryTemplateRegistry registry = new IndustryTemplateRegistry();

    IndustryTemplateRegistryTest() throws Exception {
        registry.load();
    }

    @Test
    void loadsFullCatalog() {
        List<GenAiTemplateVO> all = registry.list();
        assertTrue(all.size() >= 200 && all.size() <= 300,
                "模板库规模应落在 200~300，实际 " + all.size());
        Set<String> keys = all.stream().map(GenAiTemplateVO::getKey).collect(Collectors.toSet());
        assertEquals(all.size(), keys.size(), "key 必须唯一");
        // 历史 4 套基线仍在
        assertTrue(keys.containsAll(Set.of("ecommerce", "erp", "crm", "cms")),
                "历史 4 套基线模板不能丢");
        // 新分类模板抽样
        assertTrue(keys.containsAll(Set.of("med_hospital", "retail_b2c", "mfg_discrete")),
                "V9.9 新增行业模板应存在");
    }

    /** V9.12：智慧村务专用模板——70 张村务表 + 15 张平台通用表，字段完整、无冲突 */
    @Test
    void villageAffairsTemplateIsFullSystem() {
        GenAiTemplateVO v = registry.get("village_affairs");
        assertNotNull(v, "智慧村务模板应存在");
        assertEquals("智慧村务管理", v.getName());
        assertEquals("政务民生", v.getCategory());
        assertTrue(v.getTableCount() >= 70, "村务模板表数应 ≥70，实际 " + v.getTableCount());

        Set<String> tables = v.getModules().stream()
                .flatMap(m -> m.getTables().stream())
                .map(GenPipelineAiVO.TableModel::getTableName)
                .collect(Collectors.toSet());
        assertEquals(v.getTableCount(), tables.size(), "村务模板不能有重复表");
        // 四大业务域核心表必须在
        assertTrue(tables.containsAll(Set.of(
                "village_population_change", "village_land_contract",  // 基础数据底座
                "village_party_member", "village_disclosure", "village_dispute",  // 党建治理
                "village_fund_transaction", "village_service_apply", "village_env_issue",  // 业务监管
                "village_dashboard_metric", "village_mood_log",  // 决策服务
                "approval", "attachment", "operation_log")),  // 平台通用能力
                "村务模板核心表缺失");

        // 关键表字段完整性（按列名校验，而非硬编码数量）
        GenPipelineAiVO.TableModel fund = v.getModules().stream()
                .flatMap(m -> m.getTables().stream())
                .filter(t -> "village_fund_transaction".equals(t.getTableName()))
                .findFirst().orElseThrow();
        Set<String> fundCols = fund.getColumns().stream()
                .map(GenPipelineAiVO.Column::getName).collect(Collectors.toSet());
        assertTrue(fundCols.containsAll(Set.of("account_id", "direction", "amount",
                "before_balance", "after_balance", "status")),
                "资金流水表缺关键字段");

        GenPipelineAiVO.TableModel resident = v.getModules().stream()
                .flatMap(m -> m.getTables().stream())
                .filter(t -> "village_resident".equals(t.getTableName()))
                .findFirst().orElseThrow();
        Set<String> residentCols = resident.getColumns().stream()
                .map(GenPipelineAiVO.Column::getName).collect(Collectors.toSet());
        assertTrue(residentCols.containsAll(Set.of("id_card", "household_id",
                "grid_id", "phone", "status")), "村民档案缺关键字段");

        // 每表 ≥8 列、首列主键、业务列不撞审计列
        Set<String> audit = Set.of("create_time", "update_time", "create_by", "update_by", "deleted");
        for (GenAiTemplateVO.Module m : v.getModules()) {
            for (GenPipelineAiVO.TableModel t : m.getTables()) {
                assertTrue(t.getColumns().size() >= 8, t.getTableName() + " 字段不足");
                assertTrue(t.getColumns().get(0).isPk(), t.getTableName() + " 首列非主键");
                for (GenPipelineAiVO.Column c : t.getColumns()) {
                    assertFalse(audit.contains(c.getName()), t.getTableName() + " 撞审计列");
                    assertNotNull(c.getComment(), t.getTableName() + "." + c.getName() + " 缺注释");
                }
            }
        }
    }

    @Test
    void listIsLightweightAndCategoriesPresent() {
        for (GenAiTemplateVO t : registry.list()) {
            assertTrue(t.getModules().isEmpty(), "list() 不能带表明细，避免大 payload");
            assertTrue(t.getCategory() != null && !t.getCategory().isBlank(),
                    t.getKey() + " 应有分类");
            assertTrue(t.getTableCount() > 0, t.getKey() + " 表数计数应填充");
        }
        assertTrue(registry.categories().size() >= 10, "分类数应覆盖多个行业大类");
    }

    @Test
    void detailCarriesFullModules() {
        GenAiTemplateVO detail = registry.get("med_hospital");
        assertNotNull(detail);
        assertTrue(detail.getModules().size() >= 3, "明细接口应返回完整模块");
        assertEquals(detail.getTableCount(), detail.getModules().stream()
                .mapToInt(m -> m.getTables().size()).sum());
    }

    @Test
    void countsAreFilledAndTablesHaveColumns() {
        // 逐个取明细校验（list() 已裁剪掉 modules）
        for (GenAiTemplateVO light : registry.list()) {
            GenAiTemplateVO t = registry.get(light.getKey());
            assertTrue(t.getModuleCount() > 0, t.getKey() + " 应有模块");
            assertTrue(t.getTableCount() > 0, t.getKey() + " 应有表");
            assertEquals(t.getTableCount(), t.getModules().stream()
                    .mapToInt(m -> m.getTables().size()).sum(), t.getKey() + " 计数与明细一致");
            for (GenAiTemplateVO.Module m : t.getModules()) {
                for (GenPipelineAiVO.TableModel table : m.getTables()) {
                    assertNotNull(table.getTableName());
                    assertNotNull(table.getTableComment());
                    assertTrue(table.getColumns().size() >= 2, table.getTableName() + " 至少 id + 1 业务列");
                    assertTrue(table.getColumns().get(0).isPk(), table.getTableName() + " 首列应是主键 id");
                }
            }
        }
    }

    /** V9.10：字段展开后，每张表业务列更完整，且业务列不得与 BaseEntity 审计列撞名 */
    @Test
    void expandedColumnsAreCompleteAndAuditSafe() {
        Set<String> audit = Set.of("create_time", "update_time", "create_by", "update_by", "deleted");
        int totalTables = 0;
        int totalCols = 0;
        int withStatus = 0;
        int withRemark = 0;
        for (GenAiTemplateVO light : registry.list()) {
            GenAiTemplateVO t = registry.get(light.getKey());
            for (GenAiTemplateVO.Module m : t.getModules()) {
                for (GenPipelineAiVO.TableModel table : m.getTables()) {
                    totalTables++;
                    List<GenPipelineAiVO.Column> cols = table.getColumns();
                    totalCols += cols.size();
                    assertTrue(cols.size() >= 5, table.getTableName() + " 至少 5 列（id + 4 业务列）");
                    Set<String> names = cols.stream().map(GenPipelineAiVO.Column::getName)
                            .collect(Collectors.toSet());
                    assertEquals(cols.size(), names.size(), table.getTableName() + " 不能有重复列");
                    for (GenPipelineAiVO.Column c : cols) {
                        assertFalse(audit.contains(c.getName()),
                                table.getTableName() + " 业务列撞审计列：" + c.getName());
                        assertNotNull(c.getComment(), table.getTableName() + "." + c.getName() + " 缺注释");
                    }
                    if (names.stream().anyMatch(n -> n.equals("status") || n.endsWith("_status"))) {
                        withStatus++;
                    }
                    if (names.stream().anyMatch(n -> n.contains("remark"))) {
                        withRemark++;
                    }
                }
            }
        }
        assertTrue(totalCols * 1.0 / totalTables >= 7.0,
                "平均每表业务列应 ≥7（展开前 6.12），实际 " + (totalCols * 1.0 / totalTables));
        // V9.11：日志/明细/关联等辅助表本身不需要 status，覆盖率目标随深化表扩充调整为 85%
        assertTrue(withStatus * 1.0 / totalTables >= 0.85,
                "状态列覆盖率应 ≥85%，实际 " + (withStatus * 1.0 / totalTables));
        assertTrue(withRemark * 1.0 / totalTables >= 0.85,
                "备注列覆盖率应 ≥85%，实际 " + (withRemark * 1.0 / totalTables));
    }

    /** V9.11：每套模板必须是"能用的系统"——通用平台域 + 分类专属域，禁止退回 8 表玩具 */
    @Test
    void allTemplatesAreFullSystems() {
        Set<String> audit = Set.of("create_time", "update_time", "create_by", "update_by", "deleted");
        int minTables = Integer.MAX_VALUE;
        int minCols = Integer.MAX_VALUE;
        int totalTables = 0;
        long sumCols = 0;
        for (GenAiTemplateVO light : registry.list()) {
            GenAiTemplateVO t = registry.get(light.getKey());
            assertTrue(t.getTableCount() >= 25,
                    t.getKey() + " 表数不足（" + t.getTableCount() + "），生成的系统会是玩具");
            minTables = Math.min(minTables, t.getTableCount());
            totalTables += t.getTableCount();

            Set<String> tableNames = new java.util.HashSet<>();
            boolean hasCommon = false;
            for (GenAiTemplateVO.Module m : t.getModules()) {
                if ("平台通用能力".equals(m.getName())) {
                    hasCommon = true;
                    assertTrue(m.getTables().size() >= 12, t.getKey() + " 通用平台域缺失");
                }
                for (GenPipelineAiVO.TableModel table : m.getTables()) {
                    assertTrue(tableNames.add(table.getTableName()),
                            t.getKey() + " 重复表：" + table.getTableName());
                    int n = table.getColumns().size();
                    minCols = Math.min(minCols, n);
                    sumCols += n;
                    assertTrue(n >= 8, table.getTableName() + " 字段不足（" + n + "）");
                    for (GenPipelineAiVO.Column c : table.getColumns()) {
                        assertFalse(audit.contains(c.getName()),
                                table.getTableName() + " 业务列撞审计列：" + c.getName());
                    }
                }
            }
            assertTrue(hasCommon, t.getKey() + " 缺少平台通用能力域");
        }
        assertTrue(minTables >= 25, "最小表数应 ≥25，实际 " + minTables);
        assertTrue(minCols >= 8, "最小字段数应 ≥8，实际 " + minCols);
        double avgCols = sumCols * 1.0 / totalTables;
        assertTrue(avgCols >= 12.0, "平均每表字段应 ≥12，实际 " + avgCols);
    }

    @Test
    void cloneTables_allModulesByDefault() {
        GenAiTemplateVO ecommerce = registry.get("ecommerce");
        IndustryTemplateRegistry.CloneResult r = registry.cloneTables(ecommerce, null);
        assertEquals(ecommerce.getTableCount(), r.tables().size());
        assertEquals(ecommerce.getModuleCount(), r.moduleTableNames().size());
    }

    @Test
    void cloneTables_filtersByModuleNames() {
        GenAiTemplateVO ecommerce = registry.get("ecommerce");
        IndustryTemplateRegistry.CloneResult r =
                registry.cloneTables(ecommerce, List.of("购物车"));
        assertEquals(1, r.tables().size());
        assertEquals("cart_item", r.tables().get(0).getTableName());
        assertEquals(Map.of("购物车", List.of("cart_item")).size(), r.moduleTableNames().size());
        assertNull(r.moduleTableNames().get("订单管理"), "未选模块不应出现在映射里");
    }

    @Test
    void cloneTables_deepCopyKeepsTemplateUntouched() {
        GenAiTemplateVO ecommerce = registry.get("ecommerce");
        IndustryTemplateRegistry.CloneResult r = registry.cloneTables(ecommerce, null);
        // 编辑器改克隆出来的表，模板资产不能被污染
        r.tables().get(0).setTableName("hacked");
        r.tables().get(0).getColumns().clear();
        GenAiTemplateVO fresh = registry.get("ecommerce");
        assertEquals(ecommerce.getTableCount(), fresh.getTableCount());
        assertTrue(fresh.getModules().get(0).getTables().get(0).getColumns().size() >= 2,
                "模板表结构不能被克隆侧修改");
    }

    @Test
    void get_isCaseInsensitiveAndNullSafe() {
        assertNotNull(registry.get("ECOMMERCE"));
        assertNotNull(registry.get(" ecommerce "));
        assertNull(registry.get("nope"));
        assertNull(registry.get(null));
    }
}
