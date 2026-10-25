package com.codegen.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.2 seed 生成器纯函数单测：拓扑排序 / 枚举解析 / 截断 / 唯一后缀 / 精度 / 可填充判定 / 行数策略。
 * 不连库、不起 Spring。
 */
class GenSeedGeneratorTest {

    private final Random rnd = new Random(42);

    private GenSeedGenerator.ColMeta col(String name, String dataType, String columnType, String comment) {
        return new GenSeedGenerator.ColMeta(name, dataType, columnType, true, false,
                false, false, comment, 255, 0, 0);
    }

    // ---------- 拓扑排序 ----------

    @Test
    void topoOrder_parentBeforeChild() {
        List<String> out = GenSeedGenerator.topoOrder(
                List.of("order_item", "customer", "sale_order"),
                Map.of("order_item", List.of("sale_order"), "sale_order", List.of("customer")));
        assertTrue(out.indexOf("customer") < out.indexOf("sale_order"));
        assertTrue(out.indexOf("sale_order") < out.indexOf("order_item"));
        assertEquals(3, out.size());
    }

    @Test
    void topoOrder_cycleDegradesButKeepsAll() {
        List<String> tables = List.of("a", "b");
        List<String> out = GenSeedGenerator.topoOrder(tables, Map.of("a", List.of("b"), "b", List.of("a")));
        assertEquals(tables.size(), out.size());
        assertTrue(out.containsAll(tables));
    }

    @Test
    void topoOrder_fkOutsideSetIgnored() {
        List<String> out = GenSeedGenerator.topoOrder(
                List.of("t1"), Map.of("t1", List.of("not_in_set")));
        assertEquals(List.of("t1"), out);
    }

    // ---------- 枚举解析（复用 GenTemplateModel.parseEnumOptions） ----------

    @Test
    void valueFor_enumFromComment() {
        GenSeedGenerator.ColMeta c = col("status", "tinyint", "tinyint(2)", "状态 1启用 0停用");
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "t", false);
        assertTrue(v.equals("'1'") || v.equals("'0'"), "枚举值必须落在注释映射内，实际 " + v);
    }

    @Test
    void valueFor_enumSlashStyle() {
        GenSeedGenerator.ColMeta c = col("order_status", "int", "int", "订单状态(待付款/已付款/已发货)");
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "t", false);
        assertTrue(v.equals("'0'") || v.equals("'1'") || v.equals("'2'"), "实际 " + v);
    }

    // ---------- 字符串列 ----------

    @Test
    void valueFor_varcharTruncatedToLength() {
        GenSeedGenerator.ColMeta c = new GenSeedGenerator.ColMeta("contact", "varchar", "varchar(5)",
                true, false, false, false, "联系人", 5, 0, 0);
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "t", false);
        String raw = v.substring(1, v.length() - 1);
        assertTrue(raw.length() <= 5, "超长: " + raw);
    }

    @Test
    void valueFor_uniqueGetsSeqSuffix() {
        GenSeedGenerator.ColMeta c = col("cust_name", "varchar", "varchar(64)", "客户名称");
        String v0 = GenSeedGenerator.valueFor(c, 0, rnd, "t", true);
        String v1 = GenSeedGenerator.valueFor(c, 1, rnd, "t", true);
        assertTrue(v0.contains("0001"));
        assertTrue(v1.contains("0002"));
        assertNotEquals(v0, v1);
    }

    @Test
    void valueFor_codeColumnPrefixed() {
        GenSeedGenerator.ColMeta c = col("order_no", "varchar", "varchar(32)", "单号");
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "sale_order", false);
        assertTrue(v.contains("SALE_O202600001".substring(0, 6)));
    }

    // ---------- 数值/时间 ----------

    @Test
    void valueFor_decimalKeepsScale() {
        GenSeedGenerator.ColMeta c = new GenSeedGenerator.ColMeta("total_amount", "decimal", "decimal(10,2)",
                true, false, false, false, "总金额", 0, 10, 2);
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "t", false);
        assertTrue(v.matches("\\d+\\.\\d{2}"), "实际 " + v);
    }

    @Test
    void valueFor_qtyInReasonableRange() {
        GenSeedGenerator.ColMeta c = new GenSeedGenerator.ColMeta("stock_qty", "int", "int",
                true, false, false, false, "库存数量", 0, 0, 0);
        for (int i = 0; i < 20; i++) {
            int v = Integer.parseInt(GenSeedGenerator.valueFor(c, i, rnd, "t", false));
            assertTrue(v >= 1 && v <= 500, "超出数量语义范围: " + v);
        }
    }

    @Test
    void valueFor_datetimeFormat() {
        GenSeedGenerator.ColMeta c = col("deliver_time", "datetime", "datetime", "发货时间");
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "t", false);
        assertTrue(v.matches("'2026-10-\\d{2} \\d{2}:\\d{2}:00'"), "实际 " + v);
    }

    @Test
    void valueFor_commentEscaped() {
        GenSeedGenerator.ColMeta c = new GenSeedGenerator.ColMeta("remark", "varchar", "varchar(64)",
                true, false, false, false, "备注（含'单引号'场景）", 64, 0, 0);
        String v = GenSeedGenerator.valueFor(c, 0, rnd, "t", false);
        // 除首尾引号外不得再出现裸单引号
        String raw = v.substring(1, v.length() - 1);
        assertFalse(raw.matches(".*[^']'[^'].*"), "未转义的单引号: " + v);
    }

    // ---------- 可填充判定 ----------

    @Test
    void fillable_rules() {
        assertTrue(GenSeedGenerator.fillable(col("name", "varchar", "varchar(64)", "名称")));
        assertFalse(GenSeedGenerator.fillable(new GenSeedGenerator.ColMeta("id", "bigint", "bigint",
                true, false, true, true, "主键", 0, 0, 0)));
        assertFalse(GenSeedGenerator.fillable(new GenSeedGenerator.ColMeta("create_time", "datetime", "datetime",
                true, true, false, false, "创建时间", 0, 0, 0)));
    }

    // ---------- 行数策略 ----------

    @Test
    void rowsFor_strategy() {
        assertEquals(12, GenSeedGenerator.rowsFor("mdm_material", List.of(), Map.of(), rnd));
        assertEquals(8, GenSeedGenerator.rowsFor("sale_order", List.of(), Map.of(), rnd));
        int child = GenSeedGenerator.rowsFor("order_item", List.of("sale_order"),
                Map.of("sale_order", 8), rnd);
        assertTrue(child >= 16 && child <= 24, "明细行数应在父行×2~4 且 ≤24: " + child);
        // 父表被跳过（无行数记录）→ 按普通表
        assertEquals(8, GenSeedGenerator.rowsFor("order_item", List.of("sale_order"), Map.of(), rnd));
    }
}
