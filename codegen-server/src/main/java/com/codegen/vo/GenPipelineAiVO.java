package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 领域建模结果：候选表结构（列级可编辑），前端确认后回传建库。
 */
@Data
@Schema(description = "AI 领域建模结果")
public class GenPipelineAiVO {

    @Schema(description = "候选表结构清单")
    private List<TableModel> tables = new ArrayList<>();

    @Data
    @Schema(description = "候选表")
    public static class TableModel {

        @Schema(description = "表名（snake_case）", example = "inbound_order")
        private String tableName;

        @Schema(description = "表注释", example = "入库单")
        private String tableComment;

        @Schema(description = "列清单（不含审计列，服务端建库时自动补齐）")
        private List<Column> columns = new ArrayList<>();
    }

    @Data
    @Schema(description = "列定义")
    public static class Column {

        @Schema(description = "列名（snake_case）", example = "order_no")
        private String name;

        @Schema(description = "列类型（MySQL）", example = "varchar(64)")
        private String type;

        @Schema(description = "列注释", example = "入库单号")
        private String comment;

        @Schema(description = "是否主键")
        private boolean pk;
    }
}
