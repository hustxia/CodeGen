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

    @Data
    @Schema(description = "业务模块")
    public static class Module {

        @Schema(description = "模块名（中文短词）", example = "住院管理")
        private String name;

        @Schema(description = "模块职责一句话说明", example = "入院登记、在院床位与出院结算")
        private String desc;
    }
}
