package com.codegen.vo;

import lombok.Data;

import java.util.List;

/** 套件注册表视图（含最近一次结果，供列表直接显示，不另发 N 个请求）。 */
@Data
public class TcSuiteVO {

    private String id;
    private String name;
    private String baseline;
    private String level;
    private int timeoutMinutes;
    private boolean defaultSelected;
    private List<String> dependsOn;
    private String entryHint;
    private String requires;
    private boolean genProduct;
    private String lastStatus;
    private Long lastDurationMs;
}
