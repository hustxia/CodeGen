package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.2 seed 步结果：插入了哪些表多少行、跳过清单、告警。
 */
@Data
@Schema(description = "数据初始化结果")
public class GenSeedVO {

    @Schema(description = "成功插数的表数量")
    private int insertedTables = 0;

    @Schema(description = "插入总行数")
    private int totalRows = 0;

    @Schema(description = "跳过清单（已有数据/超限/无列等）")
    private List<SkipItem> skipped = new ArrayList<>();

    @Schema(description = "告警（如外键成环降级）")
    private List<String> warn = new ArrayList<>();

    @Schema(description = "一句话摘要（用于步骤 message）")
    private String summary;

    @Data
    @Schema(description = "跳过明细")
    public static class SkipItem {

        @Schema(description = "表名")
        private String table;

        @Schema(description = "跳过原因")
        private String reason;
    }
}
