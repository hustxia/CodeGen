package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * V9.22 安装结果：台账ID + plan 摘要（不返回完整表结构，前端列表页可直接用）。
 */
@Data
@Schema(description = "范式包安装结果")
public class IndustryInstallVO {

    @Schema(description = "安装台账ID（字符串下发，防雪花精度丢失）")
    private String installId;

    @Schema(description = "范式包key")
    private String packKey;

    @Schema(description = "范式版本")
    private String packVersion;

    @Schema(description = "plan来源（恒为template）")
    private String planSource;

    @Schema(description = "模块数")
    private int moduleCount;

    @Schema(description = "表数")
    private int tableCount;

    @Schema(description = "排除数")
    private int excludedCount;

    @Schema(description = "是否可继续建库（本版本恒false：只生成plan）")
    private boolean ddlReady;
}
