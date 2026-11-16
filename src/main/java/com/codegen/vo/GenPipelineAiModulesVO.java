package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 模块拆分结果：系统 → 业务模块清单。
 *
 * <p>「完整系统」模式的第一步：先拆模块，再逐模块生成表结构，
 * 避免小模型一次性输出几百 token 的 JSON 导致质量崩塌/超时。
 */
@Data
@Schema(description = "AI 模块拆分结果")
public class GenPipelineAiModulesVO {

    @Schema(description = "业务模块清单")
    private List<Module> modules = new ArrayList<>();

    @Schema(description = "模块与所输入系统的关联度偏低告警（true 时前端提示补充描述/重新生成）")
    private boolean relevanceWarning = false;

    @Schema(description = "关联度告警说明，relevanceWarning=false 时为空")
    private String relevanceTip = "";

    @Data
    @Schema(description = "业务模块")
    public static class Module {

        @Schema(description = "模块名（中文短词）", example = "住院管理")
        private String name;

        @Schema(description = "模块职责一句话说明", example = "入院登记、在院床位与出院结算")
        private String desc;

        @Schema(description = "功能点清单（3-6 条）", example = "[\"入院登记\",\"床位管理\",\"出院结算\"]")
        private List<String> functions = new ArrayList<>();
    }
}
