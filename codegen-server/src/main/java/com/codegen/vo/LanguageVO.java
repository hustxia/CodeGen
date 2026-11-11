package com.codegen.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 语言配置项。字段与 {@code classpath:i18n/languages.json} 一一对应。
 *
 * <p>数据由 JDK 21 CLDR 生成（ISO 639-1 全集 + 常用 BCP47 变体），
 * 中文名 / 本地自称均取自 CLDR，不是人工编造的译名。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LanguageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 语言码：ISO 639-1 两字母，或 BCP47 变体（如 {@code zh-TW} / {@code pt-BR}） */
    private String code;

    /** ISO 639-2 三字母码 */
    private String iso6392;

    /** 默认 BCP47 标签，如 {@code zh-CN} / {@code ar-SA}，用于 {@code <html lang>} */
    private String locale;

    private String nameEn;

    private String nameZh;

    /** 本地语言自称，如 {@code 日本語} / {@code العربية} / {@code Português (Brasil)} */
    private String nameNative;

    /** 默认地区码，如 {@code CN} / {@code BR} */
    private String region;

    /** ISO 15924 文字体系，如 {@code Hans} / {@code Arab} / {@code Cyrl} */
    private String script;

    /** 书写方向：{@code ltr} / {@code rtl} */
    private String dir;

    /** 对应仓库里的 README 译本文件名，空表示暂无译本 */
    private String readme;

    private Boolean enabled;

    private Integer sortNo;

    public LanguageVO() {
    }

    /** 最小降级清单用（JSON 缺失/损坏时仍能保证服务可用） */
    public LanguageVO(String code, String locale, String nameEn, String nameZh,
                      String nameNative, String script, String dir) {
        this.code = code;
        this.locale = locale;
        this.nameEn = nameEn;
        this.nameZh = nameZh;
        this.nameNative = nameNative;
        this.script = script;
        this.dir = dir;
        this.enabled = true;
        this.sortNo = 0;
    }
}
