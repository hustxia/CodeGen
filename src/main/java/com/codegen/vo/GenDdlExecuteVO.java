package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行 DDL 的结果：逐条语句回报 + 自动导入的表。
 */
@Data
@Schema(description = "建库建表一键导入结果")
public class GenDdlExecuteVO {

    @Schema(description = "实际执行的库名")
    private String schema;

    @Schema(description = "是否为 dryRun（只解析未执行）")
    private boolean dryRun;

    @Schema(description = "成功语句数")
    private int successCount;

    @Schema(description = "失败语句数")
    private int failedCount;

    @Schema(description = "已存在而跳过的语句数（迁移脚本重复执行时为正常）")
    private int skippedCount;

    @Schema(description = "被拦截的危险语句数")
    private int blockedCount;

    @Schema(description = "DDL 中出现且已建好的表")
    private List<String> createdTables = new ArrayList<>();

    @Schema(description = "自动导入生成器配置的表")
    private List<String> importedTables = new ArrayList<>();

    @Schema(description = "逐语句明细")
    private List<Statement> statements = new ArrayList<>();

    @Data
    @Schema(description = "单条语句执行结果")
    public static class Statement {

        @Schema(description = "语句序号，从 1 开始")
        private int index;

        @Schema(description = "语句摘要（截断，避免超长）")
        private String sql;

        @Schema(description = "状态：OK / FAILED / BLOCKED / DRY")
        private String status;

        @Schema(description = "失败原因")
        private String message;

        public Statement() {
        }

        public Statement(int index, String sql, String status, String message) {
            this.index = index;
            this.sql = sql;
            this.status = status;
            this.message = message;
        }
    }
}
