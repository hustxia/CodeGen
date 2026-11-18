package com.codegen.support;

import com.codegen.dto.GenTableSaveDTO;
import com.codegen.entity.GenTable;
import com.codegen.vo.GenTableVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V4.4 删除级联模式的三级回落与「配置能真正落地」的契约。
 *
 * <p>第二组断言来自一次真实缺陷：前端表单已经能选「删除级联」，但
 * {@link GenTableSaveDTO} 与 {@link GenTableVO} 都没有这个字段——
 * 保存时被反序列化静默丢弃、详情时不返回，用户选了等于没选，
 * 而且不报任何错，表现为「保存成功但重新打开还是未配置」。
 */
class GenCascadeModeTest {

    @Test
    @DisplayName("三级回落：表级 → 系统默认 → 硬编码级联")
    void resolveFallsBackInOrder() {
        // 表级优先，哪怕系统默认是别的
        assertEquals(GenCascadeMode.BLOCK, GenCascadeMode.resolve(GenCascadeMode.BLOCK, GenCascadeMode.NONE));
        // 表级未配置，取系统默认
        assertEquals(GenCascadeMode.NONE, GenCascadeMode.resolve(null, GenCascadeMode.NONE));
        // 两级都未配置，硬编码级联（2026-10-23 的产品决定，不随开关体系联动）
        assertEquals(GenCascadeMode.CASCADE, GenCascadeMode.resolve(null, null));
    }

    @Test
    @DisplayName("越界值按未配置处理，不会逃进模板")
    void resolveIgnoresOutOfRangeValue() {
        // 表级 9 非法 → 跳过，落到系统默认 2
        assertEquals(GenCascadeMode.NONE, GenCascadeMode.resolve(9, GenCascadeMode.NONE));
        // 表级 9 + 系统 9 全非法 → 硬编码级联
        assertEquals(GenCascadeMode.CASCADE, GenCascadeMode.resolve(9, 9));
    }

    @Test
    @DisplayName("sanitize：0/1/2 原样保留，其余一律 null")
    void sanitizeKeepsOnlyLegalModes() {
        assertEquals(Integer.valueOf(0), GenCascadeMode.sanitize(0));
        assertEquals(Integer.valueOf(1), GenCascadeMode.sanitize(1));
        assertEquals(Integer.valueOf(2), GenCascadeMode.sanitize(2));
        assertNull(GenCascadeMode.sanitize(null));
        assertNull(GenCascadeMode.sanitize(-1));
        assertNull(GenCascadeMode.sanitize(3));
        assertNull(GenCascadeMode.sanitize(999));
    }

    @Test
    @DisplayName("三种模式都有面向用户的文案，不留空")
    void describeCoversEveryMode() {
        for (int mode : new int[]{GenCascadeMode.BLOCK, GenCascadeMode.CASCADE, GenCascadeMode.NONE}) {
            assertNotNull(GenCascadeMode.describe(mode));
            assertEquals(false, GenCascadeMode.describe(mode).isBlank());
        }
    }

    @Test
    @DisplayName("字段契约：entity 有这个字段，DTO 与 VO 也必须都有")
    void cascadeModeFieldMustSurviveTheRoundTrip() throws NoSuchFieldException {
        Field entityField = GenTable.class.getDeclaredField("deleteCascadeMode");
        assertNotNull(entityField, "实体缺 delete_cascade_mode 映射，模板拿不到值");

        // 少了 DTO 字段：请求里的值被静默丢弃，保存永远不生效
        Field dtoField = GenTableSaveDTO.class.getDeclaredField("deleteCascadeMode");
        assertEquals(Integer.class, dtoField.getType());

        // 少了 VO 字段：详情不返回，编辑弹窗只能显示「未配置」
        Field voField = GenTableVO.class.getDeclaredField("deleteCascadeMode");
        assertEquals(Integer.class, voField.getType());
    }
}
