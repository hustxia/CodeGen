package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构漂移同步结果
 */
@Data
public class GenColumnSyncVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;

    /**
     * 本次新增的字段配置（库里有、配置没有）
     */
    private List<String> added = new ArrayList<>();

    /**
     * 冲突：库里有、配置也有，但类型不一致（不自动改，提示人工确认）
     */
    private List<String> changed = new ArrayList<>();

    /**
     * 库里已不存在、配置仍保留的列；仅在 removeMissing=true 时才真的删除
     */
    private List<String> removed = new ArrayList<>();

    /**
     * 库表不存在等导致的跳过原因
     */
    private String skipped;

    /**
     * 实际写入的字段配置条数
     */
    private int affected;
}
