package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 代码生成器-字段配置 DTO
 */
@Data
public class GenColumnDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据库列名
     */
    private String columnName;

    /**
     * 列注释
     */
    private String columnComment;

    /**
     * 数据库类型
     */
    private String columnType;

    /**
     * Java属性名（小驼峰）
     */
    private String javaField;

    /**
     * Java类型
     */
    private String javaType;

    /**
     * 是否主键:0否1是
     */
    private Integer isPk;

    /**
     * 是否必填:0否1是
     */
    private Integer isRequired;

    /**
     * 是否查询条件:0否1是
     */
    private Integer isQuery;

    /**
     * 查询方式:EQ/LIKE/GE/LE
     */
    private String queryType;

    /**
     * 列表是否显示:0否1是
     */
    private Integer isList;

    /**
     * 表单是否录入:0否1是
     */
    private Integer isForm;

    /**
     * 表单控件:input/textarea/number/select/radio/switch/date/datetime
     */
    private String htmlType;

    /**
     * 字典编码（对应 sys_dict_type.dict_code），select/radio 用
     */
    private String dictType;

    /**
     * 校验规则:required,maxlen=50,min=1,max=9999,email,phone,url,regex=...
     */
    private String validateRule;

    /**
     * 关联表（V1.4-4）：下拉选项来源表，如 v14_category
     */
    private String refTable;

    /**
     * 关联展示字段，默认 name
     */
    private String refLabel;

    /**
     * Excel 导出：0否1是（V1.4-5）
     */
    private Integer excelExport;

    /**
     * Excel 导入：0否1是
     */
    private Integer excelImport;

    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * P1-1 脱敏类型：phone/idcard/email/name/bankcard，空为不脱敏
     */
    private String maskType;

    /**
     * P1-3 是否参与唯一性校验:0否1是（多列同置1则联合唯一）
     */
    private Integer uniqueFlag;

    /**
     * P1-4 表单联动规则 JSON，格式见 GenTableColumn#linkRule
     */
    private String linkRule;

    /**
     * P2-1 数据权限标记：dept 按部门 / self 按本人
     */
    private String dataScope;

    /**
     * P3-2 中间表外键列标记:0否1是
     */
    private Integer relationKey;
}
