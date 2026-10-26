package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一键生成的单个阶段结果
 */
@Data
public class GenApplyStageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * CHECK / BACKUP / WRITE / COMPILE / MENU / ROUTE
     */
    private String stage;

    /**
     * PASS / SKIPPED / FAILED
     */
    private String status;

    private String message;

    private Long elapsedMs;

    private List<String> details = new ArrayList<>();

    public GenApplyStageVO() {
    }

    public GenApplyStageVO(String stage, String status, String message, Long elapsedMs) {
        this.stage = stage;
        this.status = status;
        this.message = message;
        this.elapsedMs = elapsedMs;
    }

    public GenApplyStageVO detail(String line) {
        if (line != null) {
            this.details.add(line);
        }
        return this;
    }
}
