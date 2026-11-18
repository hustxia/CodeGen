package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * P2-3 字典自动识别结果：一条对应一个待建/可更新的字典。
 *
 * <p>识别来源有两种，{@link #source} 区分：
 * <ul>
 *   <li>{@code enum}：列类型是 enum(...)，选项直接来自 DDL，置信度最高</li>
 *   <li>{@code comment}：列注释里含 {@code 标签:值} / {@code 标签(值)} 列表，靠文本推断</li>
 * </ul>
 */
@Data
public class DictDetectItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 建议的字典编码，格式 {@code 表名_列名}
     */
    private String dictCode;

    /**
     * 字典名称，取列注释的主标题
     */
    private String dictName;

    /**
     * 识别来源：enum / comment
     */
    private String source;

    /**
     * 来源表
     */
    private String tableName;

    /**
     * 来源列
     */
    private String columnName;

    /**
     * 列注释原文，前端展示「为什么识别成这个」
     */
    private String columnComment;

    /**
     * 识别出的选项
     */
    private List<Option> options = new ArrayList<>();

    /**
     * 该字典编码在库里是否已存在
     */
    private boolean exists;

    /**
     * 已存在时，库里现有的选项（用于前端提示「会覆盖/会追加」）
     */
    private List<Option> existingOptions = new ArrayList<>();

    /**
     * 目标列当前已配置的 dict_type（为空说明还没配）
     */
    private String currentDictType;

    /**
     * 是否已被目标列引用（dict_type 等于建议编码）
     */
    private boolean bound;

    /**
     * 单个选项
     */
    @Data
    public static class Option implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String label;

        private String value;

        private Integer sortNo;

        public Option() {
        }

        public Option(String label, String value, Integer sortNo) {
            this.label = label;
            this.value = value;
            this.sortNo = sortNo;
        }
    }
}
