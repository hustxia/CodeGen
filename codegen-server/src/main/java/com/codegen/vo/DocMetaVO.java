package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 文档预览能力探测结果（V6.9）。
 *
 * <p>前端据此决定渲染器；reason 只在 unsupported 时非空，
 * 用来告诉用户「为什么不能预览」而不是给一个空白弹窗。
 */
@Data
public class DocMetaVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** pdf / browser-docx / unsupported */
    private String previewMode;

    /** 当前生效引擎：libreoffice / browser */
    private String engine;

    private boolean convertible;

    private String reason;
}
