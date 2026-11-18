package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成预览文件 VO
 */
@Data
public class GenFileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属表名
     */
    private String tableName;

    /**
     * 相对路径
     */
    private String path;

    /**
     * 文件内容
     */
    private String content;

    public GenFileVO() {
    }

    public GenFileVO(String tableName, String path, String content) {
        this.tableName = tableName;
        this.path = path;
        this.content = content;
    }
}
