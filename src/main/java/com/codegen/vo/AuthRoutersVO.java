package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 动态路由响应：菜单树 + 全量菜单路径 + 权限码。
 *
 * <p>{@code allPaths} 用于前端区分「404 不存在」与「403 无权访问」：
 * 请求的路径如果出现在 allPaths 里但不在本次下发的 routers 里，就是越权，落 403 而不是 404。
 */
@Data
public class AuthRoutersVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前用户可见的菜单树（type 1/2）
     */
    private List<RouterVO> routers = new ArrayList<>();

    /**
     * 系统内全部启用菜单路径（不含按钮），越权判定用
     */
    private List<String> allPaths = new ArrayList<>();

    /**
     * 当前用户权限码（按钮级），与 /auth/permissions 同源，顺带下发省一次请求
     */
    private Set<String> permissions;

    /**
     * 是否超管（前端据此显示全量视图 / 禁止改自己的权限）
     */
    private boolean superAdmin;
}
