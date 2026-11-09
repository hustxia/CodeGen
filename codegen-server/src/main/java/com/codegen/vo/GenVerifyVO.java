package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 前端产物真校验结果 VO（F14：读磁盘实际文件静态解析，逐项 PASS/WARN/FAIL）
 */
@Data
public class GenVerifyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否通过（无 FAIL 项）
     */
    private Boolean passed;

    private Integer total;

    private Integer pass;

    private Integer warn;

    private Integer fail;

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 校验对象（相对路径 / 路由文件 / 表名）
         */
        private String file;

        /**
         * PASS / WARN / FAIL
         */
        private String severity;

        /**
         * 说明
         */
        private String message;

        public Item() {
        }

        public Item(String file, String severity, String message) {
            this.file = file;
            this.severity = severity;
            this.message = message;
        }
    }
}
