package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.dto.LoginDTO;
import com.codegen.dto.SysUserSaveDTO;
import com.codegen.entity.SysUser;
import com.codegen.vo.LoginVO;
import com.codegen.vo.SysUserVO;

import java.util.List;

/**
 * 平台用户 Service
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 登录
     */
    LoginVO login(LoginDTO dto);

    /**
     * 根据 token 获取用户信息
     */
    SysUserVO getByToken(String token);

    /**
     * 新增用户（密码加密）
     */
    void saveUser(SysUserSaveDTO dto);

    /**
     * 修改用户（密码为空则不更新）
     */
    void updateUser(SysUserSaveDTO dto);

    /**
     * 分配角色（先删后插）
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 保存界面主题偏好，返回落库后的主题 ID。
     *
     * <p>只校验格式，不校验色相是否在白名单内 —— 主题体系由前端维护，
     * 后端硬编码枚举会变成「前端加了新主题后端不认」的耦合。
     */
    String saveTheme(Long userId, String themeId);
}
