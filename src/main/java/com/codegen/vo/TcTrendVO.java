package com.codegen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 趋势点：通过率与耗时，按运行时间正序返回，前端直接画折线。 */
@Data
public class TcTrendVO {

    private Long runId;
    private String runNo;
    private String status;
    private double passRate;
    private Long durationMs;
    private LocalDateTime createTime;
    private String gitCommit;
}
