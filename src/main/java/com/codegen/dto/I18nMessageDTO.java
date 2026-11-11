package com.codegen.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V8.0 译文接口入参。四个形态共用一个文件（静态嵌套类），
 * 与 {@code LanguageController.DefaultLanguageVO} 的 inline record 同款取舍。
 */
public final class I18nMessageDTO {

    private I18nMessageDTO() {
    }

    /** 单条译文 */
    @Data
    public static class Item {
        private String key;
        private String value;
    }

    /** 分页查询 */
    @Data
    public static class Page {
        private Long projectId;
        private String locale;
        private String keyword;
        private Integer pageNum = 1;
        private Integer pageSize = 20;
    }

    /** 批量保存（清空某条译文 = value 传 null 或空串） */
    @Data
    public static class Save {
        private Long projectId;
        private String locale;
        private List<Item> items = new ArrayList<>();
    }

    /** 导入（overwrite=false 时只补空，不覆盖已有译文） */
    @Data
    public static class Import {
        private Long projectId;
        private String locale;
        private Boolean overwrite = true;
        private List<Item> items = new ArrayList<>();
    }
}
