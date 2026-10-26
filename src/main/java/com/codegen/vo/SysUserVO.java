package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台用户 VO（不返回密码）
 */
@Data
public class SysUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 界面主题 ID（色相-明暗-形态）。前端据此在登录后立即套用皮肤。
     */
    private String themeId;

    /**
     * 权限码集合（超管为 ["*:*:*"]），前端 v-hasPermi 指令据此判断按钮显示
     */
    private List<String> permissions;
}
