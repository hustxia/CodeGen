package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 漂移检测汇总（V4.2）
 */
@Data
public class GenDriftSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int total;

    private int same;

    private int drifted;

    private int missing;

    private int unknown;

    private List<GenDriftVO> items = new ArrayList<>();
}
