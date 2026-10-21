package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户角色关联 VO
 */
@Data
public class SysUserRoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
