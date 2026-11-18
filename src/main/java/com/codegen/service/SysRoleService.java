package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.entity.SysRole;

import java.util.List;

/**
 * 角色 Service
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分配菜单（先删后插）
     */
    void assignMenus(Long roleId, List<Long> menuIds);
}
