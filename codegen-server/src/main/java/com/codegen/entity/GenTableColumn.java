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

    /**
     * 脱敏类型：phone/idcard/email/name/bankcard，空为不脱敏
     */
    private String maskType;

    /**
     * 是否参与唯一性校验:0否1是（多列 unique_flag=1 组成联合唯一）
     */
    private Integer uniqueFlag;

    /**
     * 表单联动规则 JSON（单条规则，作用于本字段自身）：
     * <pre>
     * {"when":"status","op":"eq","value":"1","effect":"required","else":"optional"}
     * </pre>
     * when 缺省为字段自身；op 取 eq/ne/in/notIn/notEmpty/empty；
     * value 在 in/notIn 时为数组；effect 取 show/hide/enable/disable/required/optional。
     * 该 JSON 在生成期被原样内联进 Vue 页面的 linkRules 数组。
     */
    private String linkRule;

    /**
     * 数据权限标记：dept 按部门 / self 按本人，空为不参与
     */
    private String dataScope;

    /**
     * 是否中间表外键列:0否1是（P3-2 多对多识别）
     */
    private Integer relationKey;
}
