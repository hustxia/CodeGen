package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码生成器-写盘文件级台账（四期 F13）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_output")
public class GenOutput extends BaseEntity {

    /**
     * 写盘批次号（GEN+yyyyMMddHHmmss，三入口统一生成）
     */
    private String batchNo;

    /**
     * 表配置ID（gen_table.id）
     */
    private Long tableId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 产物分组:backend/frontend
     */
    private String fileGroup;

    /**
     * 相对路径
     */
    private String relPath;

    /**
     * 写盘绝对路径（SKIPPED/ERROR 可为空）
     */
    private String absPath;

    /**
     * 动作:WRITTEN新建/OVERWRITTEN覆盖/MERGED合并块/SKIPPED跳过/ERROR失败
     */
    private String action;

    /**
     * 文件字节数（未写盘为 0）
     */
    private Long fileSize;

    /**
     * 写盘内容 MD5（未写盘为空串）
     */
    private String md5;

    /**
     * 说明（合并块/跳过/失败原因）
     */
    private String message;

    /**
     * 操作人
     */
    private String operator;
}
