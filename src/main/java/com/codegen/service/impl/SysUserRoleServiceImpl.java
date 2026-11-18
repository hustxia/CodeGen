package com.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.entity.SysUserRole;
import com.codegen.mapper.SysUserRoleMapper;
import com.codegen.service.SysUserRoleService;
import org.springframework.stereotype.Service;

/**
 * 用户角色关联 Service 实现
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {
}
