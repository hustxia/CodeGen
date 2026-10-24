package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动 DDL 建库时的审计列补齐报告（V5.1 P0-1）。
 */
@Data
@Schema(description = "审计列补齐报告")
public class GenPipelineAuditReportVO {

    @Data
    @Schema(description = "单表补齐明细")
    public static class Item {

        @Schema(description = "表名")
        private String table;

        @Schema(description = "动作：PATCHED(补了列)/SKIPPED(六列全齐)")
        private String action;

        @Schema(description = "本次补上的列（PATCHED 时有值）")
        private List<String> patchedCols = new ArrayList<>();
    }
}
