package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.entity.SysRoleMenu;

import java.util.List;

/**
 * 角色菜单关联 Service
 */
public interface SysRoleMenuService extends IService<SysRoleMenu> {

    /**
     * 某个角色已授权的菜单 ID
     */
    List<Long> menuIdsOf(Long roleId);

    /**
     * 整体覆盖某个角色的菜单集合（先清后插，同一事务）
     *
     * @return 实际写入的条数
     */
    int replaceMenus(Long roleId, List<Long> menuIds);
}
