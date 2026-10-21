package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录返回 VO
 */
@Data
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String token;
    private Long userId;
    private String username;
    private String nickname;
}
