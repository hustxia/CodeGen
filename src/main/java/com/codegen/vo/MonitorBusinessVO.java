package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.10 业务指标窗口聚合。
 */
@Data
@Schema(description = "业务指标聚合")
public class MonitorBusinessVO {

    private int minutes;

    @Schema(description = "窗口还没攒满时为 true")
    private boolean warmup;

    private Gen gen = new Gen();
    private Pipeline pipeline = new Pipeline();
    private Doc doc = new Doc();

    @Data
    @Schema(description = "代码生成")
    public static class Gen {
        private long batches;
        private long success;
        private long failed;
        private double successRate;
        private double avgMs;
        private long writeFileTotal;
        @Schema(description = "状态 -> 文件数（WRITTEN / MERGED / SKIPPED / ERROR）")
        private Map<String, Long> writeFiles = new LinkedHashMap<>();
    }

    @Data
    @Schema(description = "一句话建系统流水线")
    public static class Pipeline {
        private long runs;
        private long success;
        private long failed;
        private long stopped;
        private List<Step> steps = new ArrayList<>();

        @Data
        @Schema(description = "流水线步骤耗时")
        public static class Step {
            private String step;
            private long count;
            private double avgMs;
        }
    }

    @Data
    @Schema(description = "文档预览转换")
    public static class Doc {
        private long hit;
        private long converted;
        private long failed;
        private double avgMs;
    }
}
