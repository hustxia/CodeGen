package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 单文件差异预览 VO
 */
@Data
public class GenDiffVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;

    /**
     * 仓库内相对路径
     */
    private String path;

    /**
     * 磁盘绝对路径（前端只做展示，不参与请求）
     */
    private String absPath;

    /**
     * NEW 新文件 / MODIFIED 有改动 / UNCHANGED 无改动
     */
    private String status;

    private int additions;

    private int deletions;

    private List<Line> lines;

    /**
     * 单行差异
     */
    @Data
    public static class Line implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * EQUAL / ADD / DEL
         */
        private String type;

        private Integer oldLine;

        private Integer newLine;

        private String text;

        public Line() {
        }

        public Line(String type, Integer oldLine, Integer newLine, String text) {
            this.type = type;
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.text = text;
        }
    }
}
