package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 菜单注册结果（单表）
 */
@Data
public class GenMenuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    private String name;

    private String path;

    /**
     * 菜单类型：1 目录 / 2 菜单 / 3 按钮
     */
    private Integer type;

    /**
     * REGISTERED / SKIPPED / ERROR / PLANNED
     */
    private String status;

    private String message;

    public GenMenuVO() {
    }

    public GenMenuVO(String tableName, Long menuId, String name, String path, Integer type,
                     String status, String message) {
        this.tableName = tableName;
        this.menuId = menuId;
        this.name = name;
        this.path = path;
        this.type = type;
        this.status = status;
        this.message = message;
    }
}
