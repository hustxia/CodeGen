package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * lint 一键补齐结果（V5.1 P0-2）：逐表执行 ALTER 后的明细 + 复检结论。
 */
@Data
@Schema(description = "lint 一键补齐结果")
public class GenPipelineLintFixVO {

    @Schema(description = "补齐后复检是否全部合规")
    private boolean ok;

    @Schema(description = "检查的表数量")
    private int tableCount;

    @Schema(description = "逐表补齐明细")
    private List<Item> items = new ArrayList<>();

    @Data
    @Schema(description = "单表补齐结果")
    public static class Item {

        @Schema(description = "表名")
        private String table;

        @Schema(description = "动作：PATCHED(补了列)/SKIPPED(本就合规或并发重复执行)/FAILED(执行失败)")
        private String action;

        @Schema(description = "本次补上的列（PATCHED 时有值）")
        private List<String> patchedCols = new ArrayList<>();

        @Schema(description = "缺失的列（进入补齐流程的表有值）")
        private List<String> missingCols = new ArrayList<>();

        @Schema(description = "失败原因（FAILED 时有值）")
        private String error;
    }
}
