package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * P3-2 多对多关系识别结果。
 *
 * <p>典型场景：{@code product} ←→ {@code tag}，中间表 {@code product_tag(product_id, tag_id)}。
 * 识别出来之后，两侧的页面都能拿到「多选下拉」，不再是两个孤立的 CRUD 页面。
 */
@Data
public class RelationDetectVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 中间表名
     */
    private String middleTable;

    /**
     * 中间表注释
     */
    private String middleComment;

    /**
     * 左表（通常是被关联的主实体，如 product）
     */
    private String leftTable;

    private String leftComment;

    /**
     * 左外键列
     */
    private String leftKey;

    /**
     * 右表（如 tag）
     */
    private String rightTable;

    private String rightComment;

    /**
     * 右外键列
     */
    private String rightKey;

    /**
     * 置信度说明，前端直接展示「为什么判定为中间表」
     */
    private String reason;

    /**
     * 是否已经在 gen_table 里配置过（配置过就只做提示，不重复建）
     */
    private boolean configured;

    /**
     * 中间表除两个外键外还带了哪些业务列（如排序、创建时间之外的自定义列）
     */
    private List<String> extraColumns = new ArrayList<>();
}
