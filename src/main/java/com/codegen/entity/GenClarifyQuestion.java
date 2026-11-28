package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 澄清向导问题库（V9.25 M2.1）：4 个通用问题 + 4 个行业专属问题。
 * 每次向导取 industry='' 的通用题 + 当前行业题，共 5 问。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_clarify_question")
public class GenClarifyQuestion extends BaseEntity {

    private String questionCode;
    private String industry;
    private Integer sortNo;
    private String question;
    private String optionsJson;
    private String hint;
    private Integer enabled;
}
