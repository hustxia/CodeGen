package com.codegen.support;

import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenPipelineAiVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.7 行业模板注册表：4 个预置包必须全部加载、key 唯一、表结构与 AI 建模产物同形；
 * 克隆必须深拷贝（模板常驻内存，编辑器改表不能污染模板资产）。
 */
class IndustryTemplateRegistryTest {

    private final IndustryTemplateRegistry registry = new IndustryTemplateRegistry();

    IndustryTemplateRegistryTest() throws Exception {
        registry.load();
    }

    @Test
    void loadsFourPacks() {
        List<GenAiTemplateVO> all = registry.list();
        assertEquals(4, all.size(), "电商/ERP/CRM/CMS 四个预置包都要加载成功");
        assertEquals(Set.of("ecommerce", "erp", "crm", "cms"),
                all.stream().map(GenAiTemplateVO::getKey).collect(Collectors.toSet()));
    }

    @Test
    void countsAreFilledAndTablesHaveColumns() {
        for (GenAiTemplateVO t : registry.list()) {
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
