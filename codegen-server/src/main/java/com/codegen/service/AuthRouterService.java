package com.codegen.service;

import com.codegen.vo.AuthRoutersVO;

/**
 * 动态路由服务：按角色下发菜单树
 */
public interface AuthRouterService {

    /**
     * 查询某个用户可见的菜单树与权限码
     *
     * @param userId 用户 ID
     * @return 菜单树 + 全部菜单路径（越权判定） + 权限码
     */
    AuthRoutersVO routersOf(Long userId);
}
