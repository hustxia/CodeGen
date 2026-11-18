package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.entity.SysMenu;
import com.codegen.vo.SysMenuVO;

import java.util.List;

/**
 * 菜单 Service
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * 查询菜单树（只含启用菜单）
     */
    List<SysMenuVO> tree();

    /**
     * 查询菜单树
     *
     * @param type   只保留该类型（1目录 2菜单 3按钮），null 表示全部
     * @param status 状态过滤（0禁用 1启用），null 表示全部
     */
    List<SysMenuVO> tree(Integer type, Integer status);
}
