package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 一键生成请求：把自检 → 备份 → 写盘 → 编译 → 注册菜单 → 重建路由 串成一次调用。
 */
@Data
public class GenApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Long> ids;

    /**
     * 菜单父级（0 = 顶级）
     */
    private Long parentId;

    /**
     * 授权角色，留空自动取管理员角色
     */
    private Long roleId;

    /**
     * 覆盖已有文件（默认 true；备份始终开启）
     */
    private Boolean overwrite;

    /**
     * 写盘后执行 mvn compile（默认 false，比较慢）
     */
    private Boolean compile;

    /**
     * 注册菜单与按钮权限（默认 true）
     */
    private Boolean registerMenu;

    /**
     * 重建前端路由文件（默认 true）
     */
    private Boolean syncRoute;

    /**
     * 自检不通过时是否继续（默认 false：直接中止）
     */
    private Boolean skipCheck;

    /**
     * 只演练不落库落盘（默认 false）
     */
    private Boolean dryRun;

    /**
     * V6.8 强制覆盖受保护文件（既有 + 无台账快照）。
     * 默认 false：宁可跳过让人看见，也不静默覆盖手改资产。
     */
    private Boolean force;
}
