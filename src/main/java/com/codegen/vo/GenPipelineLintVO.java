package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * lint 预检结果：缺审计列的表 + 补齐 SQL。
 */
@Data
@Schema(description = "流水线 lint 预检结果")
public class GenPipelineLintVO {

    @Schema(description = "是否全部通过")
    private boolean ok = true;

    @Schema(description = "检查的表数量")
    private int tableCount;

    @Schema(description = "缺审计列的表明细")
    private List<Item> items = new ArrayList<>();

    @Data
    @Schema(description = "单表 lint 结果")
    public static class Item {

        @Schema(description = "表名")
        private String table;

        @Schema(description = "缺失的列")
        private List<String> missingCols = new ArrayList<>();

        @Schema(description = "补齐 SQL（ALTER TABLE ...）")
        private String fixSql;
    }
}
