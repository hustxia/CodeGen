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

    /**
     * 漂移状态（V4.2）：SAME 与台账一致 / DRIFTED 生成后被手改 / MISSING 文件不存在 /
     * UNKNOWN 无台账记录（从未生成过或台账已清）。
     *
     * <p>与 {@link #status} 正交：status 是「渲染结果 vs 磁盘」的差异，
     * drift 是「磁盘 vs 上次生成」的差异。一个 UNCHANGED 的文件也可能 DRIFTED——
     * 那说明手改后的内容恰好又和模板一致了。
     */
    private String drift;

    /**
     * V6.8 受保护标记：文件已存在但台账里没有它的快照（不是本平台写过的，或台账丢失）。
     * 这种文件「是否被手改过」无从判断，重生成默认按受保护跳过，需显式 force 才覆盖。
     */
    private Boolean protectedFile = Boolean.FALSE;

    /**
     * 磁盘最后修改时间（漂移为 DRIFTED 时给用户做参照）
     */
    private String lastModified;

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
