package com.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codegen.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
