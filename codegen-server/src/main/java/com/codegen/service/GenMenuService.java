package com.codegen.service;

import com.codegen.dto.GenMenuRegisterDTO;
import com.codegen.vo.GenMenuRegisterVO;

/**
 * 菜单自动注册：把表配置落成 sys_menu + sys_role_menu，并同步前端路由文件
 */
public interface GenMenuService {

    /**
     * 注册菜单（幂等：已注册的表跳过）
     */
    GenMenuRegisterVO register(GenMenuRegisterDTO dto);

    /**
     * 仅按当前库内数据重建前端路由文件（不动菜单）
     */
    GenMenuRegisterVO syncRoute(boolean dryRun);
}
