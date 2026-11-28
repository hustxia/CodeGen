package com.codegen.vo;

import lombok.Data;

/** V9.25 确认结果：SPEC 草案 ID（字符串）+ 版本 + 是否走了高级模式 */
@Data
public class ClarifyConfirmVO {

    private String specId;
    private Integer specVersion;
    private boolean overridden;
}
