package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码生成器-模板配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_template")
public class GenTemplate extends BaseEntity {

    /**
     * 模板编码:entity/mapper/service/serviceImpl{Simple,Tree,Masterdetail}/pageDto/saveDto/vo/controller/apiJs/vuePage/printPage/routerSnippet/readme
     */
    private String tplCode;

    /**
     * 模板名称
     */
    private String tplName;

    /**
     * 产物分组:backend/frontend/shared
     */
    private String fileGroup;

    /**
     * Freemarker 模板内容
     */
    private String content;

    /**
     * 输出相对路径模式（支持 ${var} 占位）
     */
    private String pathPattern;

    /**
     * 归属表:0全局模板 >0表级覆盖（雪花 ID，序列化为字符串）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tableId;

    /**
     * 状态:0停用 1启用
     */
    private Integer status;

    /**
     * 版本号，每次保存 +1；历史内容存 gen_template_history
     */
    private Integer version;

    /**
     * 备注
     */
    private String remark;
}
