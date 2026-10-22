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
     * 内容事实，与写盘策略无关：NEW 新文件 / MODIFIED 内容有改动 / UNCHANGED 内容完全一致。
     *
     * <p>「这个文件会被写入还是跳过」属于策略推导（见 {@code GenDiffDTO#overwrite}），
     * 不塞进 status——否则同一份差异会因为策略不同而变脸，用户就分不清「内容没变」和「被策略挡住」。
     */
    private String status;

    /**
     * 产物归属：BACKEND / FRONTEND。
     *
     * <p>由后端按写盘同源的路径规则判定，前端不要再用 {@code path.startsWith("src/")} 猜——
     * 前端工程带 frontend-path 子目录时（如 {@code demo-web/src/api/x.js}）按前缀判会分错组。
     */
    private String side;

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
