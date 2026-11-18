package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * V8.0 译文接口出参。三种视图共用文件（静态嵌套类），字段与后端实现逐字段对齐。
 */
public final class I18nVO {

    private I18nVO() {
    }

    /** 文案键清单 */
    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String key;
        private String category;
        private String sourceText;

        public Key() {
        }

        public Key(String key, String category, String sourceText) {
            this.key = key;
            this.category = category;
            this.sourceText = sourceText;
        }
    }

    /** 可用语言 + 覆盖率 */
    @Data
    public static class Locale implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String locale;
        private String label;
        private String nameNative;
        private String dir;
        private boolean builtin;
        private int translated;
        private int total;

        public Locale() {
        }

        public Locale(String locale, String label, String nameNative, String dir,
                      boolean builtin, int translated, int total) {
            this.locale = locale;
            this.label = label;
            this.nameNative = nameNative;
            this.dir = dir;
            this.builtin = builtin;
            this.translated = translated;
            this.total = total;
        }
    }

    /** 分页行：原文 / 内置译文 / 当前值 / 是否被用户覆盖 */
    @Data
    public static class Message implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String key;
        private String category;
        private String sourceText;
        private String builtinValue;
        private String value;
        private boolean overridden;

        public Message() {
        }

        public Message(String key, String category, String sourceText, String builtinValue,
                       String value, boolean overridden) {
            this.key = key;
            this.category = category;
            this.sourceText = sourceText;
            this.builtinValue = builtinValue;
            this.value = value;
            this.overridden = overridden;
        }
    }
}
