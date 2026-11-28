package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Converge 对账明细（V1.30 第一期）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_converge_item")
public class GenConvergeItem extends BaseEntity {

    public static final String TYPE_TABLE = "TABLE";
    public static final String TYPE_FIELD = "FIELD";
    public static final String TYPE_ENUM = "ENUM";

    public static final String KIND_OK = "OK";
    public static final String KIND_MISSING = "MISSING";
    public static final String KIND_EXTRA = "EXTRA";

    /**
     * 报告ID（gen_converge_report.id）
     */
    private Long reportId;

    /**
     * 项类型:TABLE/FIELD/ENUM
     */
    private String itemType;

    /**
     * 项键:表名 / 表名.列名 / 字典编码
     */
    private String itemKey;

    /**
     * 结论:OK/MISSING/EXTRA
     */
    private String kind;

    /**
     * 规格来源（如 spec.md §二）
     */
    private String sourceRef;

    /**
     * 处理建议
     */
    private String suggestion;
}
