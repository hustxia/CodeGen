package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.SysUserRolePageDTO;
import com.codegen.dto.SysUserRoleSaveDTO;
import com.codegen.entity.SysUserRole;
import com.codegen.service.SysUserRoleService;
import com.codegen.vo.SysUserRoleVO;
import com.codegen.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 用户角色关联 Controller
 */
@Tag(name = "用户角色", description = "用户角色关联管理")
@RestController
@RequestMapping("/user-role")
public class SysUserRoleController {

    @Autowired
    private SysUserRoleService userRoleService;

    @Operation(summary = "分页查询用户角色")
    @PostMapping("/page")
    public Result<Page<SysUserRoleVO>> page(@RequestBody SysUserRolePageDTO dto) {
        Page<SysUserRole> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getUserId() != null, SysUserRole::getUserId, dto.getUserId());
        wrapper.eq(dto.getRoleId() != null, SysUserRole::getRoleId, dto.getRoleId());
        wrapper.orderByDesc(SysUserRole::getId);
        userRoleService.page(page, wrapper);
        Page<SysUserRoleVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<SysUserRoleVO> records = page.getRecords().stream()
                .map(e -> {
                    SysUserRoleVO vo = new SysUserRoleVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询用户角色")
    @GetMapping("/{id}")
    public Result<SysUserRoleVO> getById(@PathVariable Long id) {
        SysUserRole entity = userRoleService.getById(id);
        if (entity == null) {
            return Result.ok(null);
        }
        SysUserRoleVO vo = new SysUserRoleVO();
        BeanUtils.copyProperties(entity, vo);
        return Result.ok(vo);
    }

    @Operation(summary = "新增用户角色")
    @PostMapping
    public Result<Void> add(@RequestBody SysUserRoleSaveDTO dto) {
        SysUserRole entity = new SysUserRole();
        BeanUtils.copyProperties(dto, entity);
        userRoleService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改用户角色")
    @PutMapping
    public Result<Void> update(@RequestBody SysUserRoleSaveDTO dto) {
        SysUserRole entity = new SysUserRole();
        BeanUtils.copyProperties(dto, entity);
        userRoleService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除用户角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userRoleService.removeById(id);
        return Result.ok();
    }
}
