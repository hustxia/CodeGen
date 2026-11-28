package com.codegen.dto;

import lombok.Data;

import java.util.List;

/** V9.25 确认写 SPEC 入参；高级模式显式忽略的拦截码需逐条列出 */
@Data
public class ClarifyConfirmDTO {

    private boolean advancedMode;
    private List<String> overriddenCodes;
}
