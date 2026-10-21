package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码生成器-字段配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table_column")
public class GenTableColumn extends BaseEntity {

    /**
     * 表配置ID（gen_table.id）
     */
    private Long tableId;

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
     * 关联下拉来源表（物理表名），非空时该字段渲染为远程搜索 el-select
     */
    private String refTable;

    /**
     * 关联下拉显示列
     */
    private String refLabel;

    /**
     * 校验规则:required,maxlen=50,min=1,max=9999,email,phone,url,regex=...
     */
    private String validateRule;

    /**
     * 是否导出 Excel:0否1是
     */
    private Integer excelExport;

    /**
     * 是否允许从 Excel 导入:0否1是
     */
    private Integer excelImport;

    /**
     * 显示顺序
     */
    private Integer sort;
}
