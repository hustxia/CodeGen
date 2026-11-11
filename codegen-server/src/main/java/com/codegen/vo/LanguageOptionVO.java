package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 语言下拉选项：展示名用「中文名（本地自称）」，值为 BCP47 标签。
 * 附带 {@code dir} / {@code script}，让前端能在选中 RTL 语言时给出提示。
 */
@Data
public class LanguageOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String label;

    private String value;

    private String dir;

    private String script;

    private String code;

    public LanguageOptionVO() {
    }

    public LanguageOptionVO(String label, String value, String dir, String script, String code) {
        this.label = label;
        this.value = value;
        this.dir = dir;
        this.script = script;
        this.code = code;
    }
}
