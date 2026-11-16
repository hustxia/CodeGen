package com.codegen.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V9.6 分批建模任务进度（前端轮询用，不含表结构本体——结果在 result 接口取）。
 */
@Data
public class GenAiBuildJobVO {

    private Long jobId;

    private String jobNo;

    /** pending / running / success / partial / failed / canceled */
    private String status;

    private String systemName;

    private String scale;

    private String deploy;

    private Integer totalBatch;

    private Integer doneBatch;

    private Integer failedBatch;

    private Integer runningBatch;

    /** 已产出表数（滚动累计） */
    private Integer totalTable;

    /** 0-100，按完成批次算 */
    private Integer progress;

    private String error;

    private Boolean resultReady;

    private List<Batch> batches = new ArrayList<>();

    @Data
    public static class Batch {
        private Long id;
        private Integer seq;
        /** split / module / confirm */
        private String kind;
        private String moduleName;
        private String status;
        private Integer tableCount;
        private Integer retryCount;
        private String error;
    }
}
