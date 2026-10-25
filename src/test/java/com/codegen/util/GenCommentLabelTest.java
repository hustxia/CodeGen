package com.codegen.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * commentLabel 显示名推导单测（V5.1.1：纯枚举映射注释回落字段名）。
 *
 * <p>背景：hr_system 这类用户手贴 DDL，注释常写成 {@code 1男 2女 0未知}——
 * 没有标题前缀，旧逻辑整段映射串上屏当 label。现在纯映射注释回落列名。
 * isPureEnumMapping / commentLabel 是私有静态方法，用反射调用保持零侵入。
 */
class GenCommentLabelTest {

    private String label(String comment, String fallback) throws Exception {
        Method m = GenTemplateModel.class.getDeclaredMethod("commentLabel", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, comment, fallback);
    }

    private boolean pure(String text) throws Exception {
        Method m = GenTemplateModel.class.getDeclaredMethod("isPureEnumMapping", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, text);
    }

    @Test
    @DisplayName("纯枚举映射注释：显示名回落字段名，映射串不上屏")
    void pureEnumFallsBackToField() throws Exception {
        assertEquals("gender", label("1男 2女 0未知", "gender"));
        assertEquals("status", label("1在职 2试用 3离职 4停薪留职", "status"));
        assertEquals("clock_in_source", label("1APP-GPS 2WiFi 3考勤机 4补卡", "clock_in_source"));
        assertEquals("change_type", label("1入职 2转正 3调动 4晋升 5降级 6离职 7返聘", "change_type"));
        // 紧凑写法（无空格）同样整体是映射
        assertEquals("status", label("1启用0停用", "status"));
    }

    @Test
    @DisplayName("带标题的枚举注释：只取标题，行为不变")
    void titledEnumKeepsTitle() throws Exception {
        assertEquals("性别", label("性别：1男 2女 0未知", "gender"));
        assertEquals("状态", label("状态:1启用0停用", "status"));
        assertEquals("分类", label("分类ID，0=顶级 1=一级", "categoryId"));
    }

    @Test
    @DisplayName("普通注释与空注释：行为不变")
    void normalCommentUnchanged() throws Exception {
        assertEquals("生日", label("生日", "birthday"));
        assertEquals("客户", label("客户编号", "customerNo"));
        assertEquals("", label(null, "x"));
        assertEquals("", label("  ", "x"));
    }

    @Test
    @DisplayName("isPureEnumMapping：映射判定边界")
    void pureEnumBoundary() throws Exception {
        // 是映射
        assertTrue(pure("1男 2女 0未知"));
        assertTrue(pure("1APP-GPS 2WiFi 3考勤机 4补卡"));
        assertTrue(pure("1启用0停用"));
        assertTrue(pure("0-待付款 1-已付款 2-已发货"));
        // 不是映射
        assertFalse(pure("性别：1男 2女 0未知"));
        assertFalse(pure("3个工作日内完成"));
        assertFalse(pure("订单金额100元"));
        assertFalse(pure("1号线")); // 单词，无对照关系
        // 「2室1厅」结构上就是「2室+1厅」两个映射对，与「1启用0停用」同构，无法仅凭文本区分；
        // 平台的 parseEnumOptions 本来就把它当枚举下拉，label 回落字段名行为一致
        assertTrue(pure("2室1厅"));
        assertFalse(pure(null));
        assertFalse(pure(""));
    }
}
