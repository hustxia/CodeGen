package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 交付物落盘结果（V7.3 P0-2）：逐文件状态 + 失败计数。
 */
@Data
public class GenSqlDeliverVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;

    /**
     * 目标工程 db 目录的绝对路径
     */
    private String dbDir;

    private List<Item> items = new ArrayList<>();

    /**
     * 实际写入的文件数
     */
    private int written;

    /**
     * 失败文件数（落盘异常；内容为空只算 SKIPPED，不算失败）
     */
    private int errors;

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String fileName;

        /**
         * WRITTEN / SKIPPED / ERROR
         */
        private String status;

        private String path;

        private long bytes;

        private String message;
    }
}
