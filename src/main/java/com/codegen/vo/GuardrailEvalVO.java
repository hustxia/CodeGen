package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** V9.25 护栏检测结果：BLOCK / WARN 分开计数，每条命中带解释与修正建议 */
@Data
@Schema(description = "护栏检测结果")
public class GuardrailEvalVO {

    private boolean blocked;
    private int blockCount;
    private int warnCount;
    private List<Hit> hits = new ArrayList<>();

    @Data
    @Schema(description = "单条命中")
    public static class Hit {
        private String ruleCode;
        private String category;
        private String severity;
        private String title;
        private String reason;
        private String suggestion;
    }
}
