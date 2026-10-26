package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单注册汇总结果
 */
@Data
public class GenMenuRegisterVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<GenMenuVO> menus = new ArrayList<>();

    /**
     * 当前菜单出口：artifact（产出 SQL + 路由片段）/ internal（写入平台自身菜单表）
     */
    private String outlet;

    /**
     * 授权使用的角色 ID
     */
    private Long roleId;

    /**
     * 路由文件是否已同步
     */
    private boolean routeSynced;

    /**
     * 路由文件绝对路径
     */
    private String routeFile;

    /**
     * 路由文件内容（dryRun 时可预览）
     */
    private String routeContent;

    /**
     * 菜单 SQL 内容（artifact 出口产出，可在目标工程库直接执行）
     */
    private String menuSql;

    /**
     * 菜单 SQL 落盘路径
     */
    private String menuSqlFile;

    private String message;
}
