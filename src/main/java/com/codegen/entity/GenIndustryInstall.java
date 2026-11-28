package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * V9.22 行业范式安装台账：只记录"装了哪个包/版本/模块 + 生成的plan摘要"，
 * 不建库、不生成代码——Step2 建库仍需人工二次确认。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_industry_install")
@Schema(description = "行业范式安装台账")
public class GenIndustryInstall extends BaseEntity {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "范式包key")
    private String packKey;

    @Schema(description = "安装时的范式版本")
    private String packVersion;

    @Schema(description = "new新建系统 / supplement补表")
    private String scope;

    @Schema(description = "安装模块名JSON数组")
    private String moduleNames;

    @Schema(description = "plan表数")
    private Integer tableCount;

    @Schema(description = "plan摘要JSON")
    private String planSummary;

    @Schema(description = "INSTALLED / REMOVED")
    private String status;

    @Schema(description = "安装时间")
    private LocalDateTime installTime;
}
