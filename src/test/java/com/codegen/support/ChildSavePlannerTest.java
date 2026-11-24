package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.18 子表增量保存规划器：纯函数单测（不起 Spring、不连库）。
 *
 * <p>重点锁「幂等」：同一份提交连续保存两次，第二次的新增/删除集合必须为空，
 * 否则生成到用户工程里就会表现为「点一次保存多一行明细」。
 */
@DisplayName("V9.18 ChildSavePlanner：三路合并 / 一对一 upsert / 多对多差集")
class ChildSavePlannerTest {

    /** 测试用行：只有主键，够表达三路合并 */
    private record Row(Long id, String name) {
    }

    @Test
    @DisplayName("三路合并：新增 / 更新 / 删除三个集合互不重叠且完整覆盖")
    void threeWayMerge() {
        List<Row> existing = List.of(new Row(1L, "旧1"), new Row(2L, "旧2"), new Row(3L, "旧3"));
        List<Row> incoming = List.of(new Row(1L, "改1"), new Row(null, "新4"), new Row(3L, "旧3"));

        ChildSavePlanner.Plan<Row, Long> plan = ChildSavePlanner.plan(existing, incoming, Row::id);

        assertEquals(1, plan.inserts().size(), "只有主键为空的算新增");
        assertEquals("新4", plan.inserts().get(0).name());
        assertEquals(2, plan.updates().size(), "主键命中已有行的算更新");
        assertEquals(List.of(2L), plan.deletes(), "库里有、本次没提交的才删");
    }

    @Test
    @DisplayName("幂等：同一份提交保存两次，第二次没有新增也没有删除")
    void idempotentReplay() {
        List<Row> firstRows = List.of(new Row(null, "A"), new Row(null, "B"), new Row(null, "C"));
        ChildSavePlanner.Plan<Row, Long> first = ChildSavePlanner.plan(List.of(), firstRows, Row::id);
        assertEquals(3, first.inserts().size());
        assertTrue(first.updates().isEmpty());
        assertTrue(first.deletes().isEmpty());

        // 第一次保存后：3 行都有了主键，前端再次回传同一份数据（只是带上主键）
        List<Row> saved = List.of(new Row(11L, "A"), new Row(12L, "B"), new Row(13L, "C"));
        ChildSavePlanner.Plan<Row, Long> second = ChildSavePlanner.plan(saved, saved, Row::id);
        assertTrue(second.inserts().isEmpty(), "重复保存不得再插行");
        assertTrue(second.deletes().isEmpty(), "重复保存不得删行");
        assertEquals(3, second.updates().size());
    }

    @Test
    @DisplayName("空列表 = 清空：所有已有行进入 delete，不产生 insert/update")
    void emptyIncomingClearsAll() {
        List<Row> existing = List.of(new Row(1L, "a"), new Row(2L, "b"));
        ChildSavePlanner.Plan<Row, Long> plan = ChildSavePlanner.plan(existing, List.of(), Row::id);
        assertTrue(plan.inserts().isEmpty());
        assertTrue(plan.updates().isEmpty());
        assertEquals(List.of(1L, 2L), plan.deletes());
    }

    @Test
    @DisplayName("null 入参按空集合处理，不抛 NPE")
    void nullSafe() {
        ChildSavePlanner.Plan<Row, Long> plan = ChildSavePlanner.plan(null, null, Row::id);
        assertTrue(plan.inserts().isEmpty());
        assertTrue(plan.updates().isEmpty());
        assertTrue(plan.deletes().isEmpty());
    }

    @Test
    @DisplayName("一对一：已有行 → 只更新；没有行 → 只插入；两者至多一个非空")
    void oneToOneUpsert() {
        Row old = new Row(7L, "旧值");
        ChildSavePlanner.Upsert<Row> update = ChildSavePlanner.planUpsert(List.of(old), new Row(999L, "脏主键"));
        assertNotNull(update.updateTarget());
        assertNull(update.insert());
        assertEquals(old, update.updateTarget(), "按外键锁定既有行，忽略回传主键");

        ChildSavePlanner.Upsert<Row> insert = ChildSavePlanner.planUpsert(List.of(), new Row(null, "新值"));
        assertNull(insert.updateTarget());
        assertNotNull(insert.insert());
    }

    @Test
    @DisplayName("多对多：按关联键差集，重复关联只算一条，重放后 toAdd/toRemove 为空")
    void manyToManyAssociationDiff() {
        List<Long> existing = List.of(100L, 200L, 300L);
        List<Long> incoming = List.of(300L, 300L, 400L, 400L, 500L);

        ChildSavePlanner.AssociationPlan<Long> plan = ChildSavePlanner.planAssociation(existing, incoming);
        assertEquals(List.of(400L, 500L), plan.toAdd(), "去重后只新增真正缺失的关联");
        assertEquals(List.of(100L, 200L), plan.toRemove());
        assertEquals(List.of(300L), plan.toKeep());

        // 第二次保存：库里的关联已经是 {200?} 之前删过的 + 新增后的集合
        List<Long> afterSave = new ArrayList<>(Arrays.asList(300L, 400L, 500L));
        ChildSavePlanner.AssociationPlan<Long> replay = ChildSavePlanner.planAssociation(afterSave, incoming);
        assertTrue(replay.toAdd().isEmpty(), "重放不得重复插入关联行");
        assertTrue(replay.toRemove().isEmpty());
        assertEquals(List.of(300L, 400L, 500L), replay.toKeep());
    }

    @Test
    @DisplayName("关系类型归一：middle/one_to_one/MANY-TO-MANY 等写法都落到三种标准值")
    void relationTypeNormalize() {
        assertEquals("ONE_TO_MANY", ChildSavePlanner.normalizeRelationType(null));
        assertEquals("ONE_TO_MANY", ChildSavePlanner.normalizeRelationType(""));
        assertEquals("ONE_TO_MANY", ChildSavePlanner.normalizeRelationType("something-else"));
        assertEquals("MANY_TO_MANY", ChildSavePlanner.normalizeRelationType("middle"));
        assertEquals("MANY_TO_MANY", ChildSavePlanner.normalizeRelationType("many-to-many"));
        assertEquals("ONE_TO_ONE", ChildSavePlanner.normalizeRelationType("one_to_one"));
        assertEquals("ONE_TO_ONE", ChildSavePlanner.normalizeRelationType("ONE-TO-ONE"));
    }

    @Test
    @DisplayName("模式校验：只认 null/0/1，非法值必须被判定为不合法（后端据此返回中文 400）")
    void childSaveModeValidation() {
        assertTrue(ChildSaveMode.isValid(null), "未提交视为合法");
        assertTrue(ChildSaveMode.isValid(ChildSaveMode.FULL_REPLACE));
        assertTrue(ChildSaveMode.isValid(ChildSaveMode.INCREMENTAL));
        assertFalse(ChildSaveMode.isValid(2));
        assertFalse(ChildSaveMode.isValid(-1));

        assertEquals(ChildSaveMode.FULL_REPLACE, ChildSaveMode.resolve(null));
        assertEquals(ChildSaveMode.FULL_REPLACE, ChildSaveMode.resolve(9));
        assertEquals(ChildSaveMode.INCREMENTAL, ChildSaveMode.resolve(1));
        assertEquals(ChildSaveMode.FULL_REPLACE, ChildSaveMode.sanitize(null));
    }
}
