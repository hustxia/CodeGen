package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.dto.SysUserPageDTO;
import com.codegen.dto.SysUserSaveDTO;
import com.codegen.dto.ThemeSaveDTO;
import com.codegen.entity.SysUser;
import com.codegen.service.SysUserService;
import com.codegen.vo.SysUserVO;
import com.codegen.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台用户 Controller
 */
@Tag(name = "平台用户", description = "平台用户管理")
@RestController
@RequestMapping("/user")
public class SysUserController {

    @Autowired
    private SysUserService userService;

    @Operation(summary = "分页查询平台用户")
    @PostMapping("/page")
    public Result<Page<SysUserVO>> page(@RequestBody SysUserPageDTO dto) {
        Page<SysUser> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getUsername()), SysUser::getUsername, dto.getUsername());
        wrapper.like(StringUtils.hasText(dto.getNickname()), SysUser::getNickname, dto.getNickname());
        wrapper.eq(dto.getStatus() != null, SysUser::getStatus, dto.getStatus());
        wrapper.orderByDesc(SysUser::getId);
        userService.page(page, wrapper);
        Page<SysUserVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<SysUserVO> records = page.getRecords().stream()
                .map(e -> {
                    SysUserVO vo = new SysUserVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询平台用户")
    @GetMapping("/{id}")
    public Result<SysUserVO> getById(@PathVariable Long id) {
        SysUser entity = userService.getById(id);
        if (entity == null) {
            return Result.ok(null);
        }
        SysUserVO vo = new SysUserVO();
        BeanUtils.copyProperties(entity, vo);
        return Result.ok(vo);
    }

    @Operation(summary = "新增平台用户")
    @PostMapping
    public Result<Void> add(@RequestBody SysUserSaveDTO dto) {
        userService.saveUser(dto);
        return Result.ok();
    }

    @Operation(summary = "修改平台用户")
    @PutMapping
    public Result<Void> update(@RequestBody SysUserSaveDTO dto) {
        userService.updateUser(dto);
        return Result.ok();
    }

    @Operation(summary = "删除平台用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "分配角色")
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return Result.ok();
    }

    /**
     * 保存当前登录用户的界面主题偏好。
     *
     * <p>只改「自己」的主题：userId 取自登录上下文，不接受前端传入，
     * 否则任何登录用户都能改别人的皮肤。
     *
     * <p>保存后不会立即改变当前页面 —— 前端按产品约定提示「重新登录生效」，
     * 由下次登录时 /auth/login 下发的 themeId 驱动首屏渲染。
     */
    @Operation(summary = "保存当前用户界面主题偏好")
    @PutMapping("/theme")
    public Result<String> saveTheme(@RequestBody ThemeSaveDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录或登录状态已失效");
        }
        return Result.ok(userService.saveTheme(userId, dto.getThemeId()));
    }
}
