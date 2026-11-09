package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单 VO
 */
@Data
public class SysMenuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private Integer type;
    private Integer sort;
    private String icon;
    private Integer status;
    private String perms;
    private List<SysMenuVO> children;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
