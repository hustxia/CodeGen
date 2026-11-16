package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 模式 DDL 预览结果，ddl 与 prepare-db 使用同一组装方法生成。
 */
@Data
@Schema(description = "AI 模式 DDL 预览结果")
public class GenPipelineDdlPreviewVO {

    @Schema(description = "目标数据库名")
    private String dbName;

    @Schema(description = "完整 MySQL DDL，仅供预览，尚未执行")
    private String ddl;

    @Schema(description = "候选业务表数量")
    private int tableCount;

    @Schema(description = "DDL 语句数量：1 条建库语句 + N 条建表语句")
    private int statementCount;
}
