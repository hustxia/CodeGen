package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台用户分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserPageDTO extends PageQuery {

    private String username;
    private String nickname;
    private Integer status;
}
