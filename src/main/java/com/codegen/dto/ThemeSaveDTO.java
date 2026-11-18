package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存界面主题偏好 DTO
 */
@Data
public class ThemeSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主题 ID，形如 blue-light-rounded（色相-明暗-形态）。
     */
    private String themeId;
}
