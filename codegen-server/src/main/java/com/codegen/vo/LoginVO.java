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

    /**
     * 界面主题 ID。随登录一起下发，前端拿到后立刻套用并写入本地缓存，
     * 这样下一步刷新页面时首屏就能直接渲染正确皮肤，不会先闪一下默认色。
     */
    private String themeId;
}
