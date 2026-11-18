package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 数据库表批量导入请求 DTO
 */
@Data
public class GenDbImportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据库名
     */
    private String schema;

    /**
     * 选中的表名列表
     */
    private List<String> tableNames;

    /**
     * 导入后归属的系统（sys_project.id）。
     *
     * <p>为空则落成「未绑定」，生成时回落全局配置。批量导入一批表通常属于同一个系统，
     * 所以放在这里让调用方一次指定，而不是导入后再逐张表去改。
     */
    private Long projectId;
}
