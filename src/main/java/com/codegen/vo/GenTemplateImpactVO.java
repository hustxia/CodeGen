package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板影响面预览结果（只读）
 */
@Data
public class GenTemplateImpactVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板相对路径；选择「全部模板」时为 ALL
     */
    private String template;

    /**
     * 受影响的表
     */
    private List<AffectedTable> affectedTables = new ArrayList<>();

    /**
     * 产物文件总数（所有表 outputs 之和）
     */
    private int outputCount;

    /**
     * 单表影响面
     */
    @Data
    public static class AffectedTable implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * gen_table.id（雪花 ID 以字符串下发，避免前端精度丢失）
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long tableId;

        /**
         * 表名
         */
        private String tableName;

        /**
         * 表注释
         */
        private String tableComment;

        /**
         * 该模板对这张表会产出的相对路径
         */
        private List<String> outputs = new ArrayList<>();

        /**
         * 表形态提示：单表 / 树形 / 主子表 / 多对多
         */
        private List<String> modeHints = new ArrayList<>();

        /**
         * 渲染失败原因（正常为 null）
         */
        private String error;
    }
}
