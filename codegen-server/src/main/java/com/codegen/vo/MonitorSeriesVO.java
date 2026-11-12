package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * V6.10 监控趋势序列。
 */
@Data
@Schema(description = "指标趋势序列")
public class MonitorSeriesVO {

    private String metric;
    private String unit;
    private int minutes;

    @Schema(description = "窗口还没攒满时为 true，前端显示「数据积累中」")
    private boolean warmup;

    private List<Point> points = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "一个采样点")
    public static class Point {
        @Schema(description = "采样时间 epoch ms")
        private long t;
        private double v;
    }
}
