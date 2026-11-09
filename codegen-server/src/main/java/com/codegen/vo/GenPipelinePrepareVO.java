package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * prepare-db 结果：库已建、表已导入台账。
 */
@Data
@Schema(description = "建库建表结果")
public class GenPipelinePrepareVO {

    @Schema(description = "关联的系统 ID（prepare-db 阶段新建 sys_project 后回填）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    @Schema(description = "实际执行的库名")
    private String dbName;

    @Schema(description = "建表数量")
    private int tableCount;

    @Schema(description = "lint 命中的缺列明细（空 = 全部合规）")
    private List<GenPipelineLintVO.Item> lintIssues = new ArrayList<>();

    @Schema(description = "审计列补齐报告（V5.1，手动 DDL 模式：每表 SKIPPED=全齐 / PATCHED+patchedCols=已补）")
    private List<GenPipelineAuditReportVO.Item> auditReport = new ArrayList<>();
}
