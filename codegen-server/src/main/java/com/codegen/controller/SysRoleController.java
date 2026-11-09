package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.SysRolePageDTO;
import com.codegen.dto.SysRoleSaveDTO;
import com.codegen.entity.SysRole;
import com.codegen.service.SysRoleService;
import com.codegen.vo.SysRoleVO;
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
 * 角色 Controller
 */
@Tag(name = "角色", description = "角色管理")
@RestController
@RequestMapping("/role")
public class SysRoleController {

    @Autowired
    private SysRoleService roleService;

    @Operation(summary = "分页查询角色")
    @PostMapping("/page")
    public Result<Page<SysRoleVO>> page(@RequestBody SysRolePageDTO dto) {
        Page<SysRole> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getCode()), SysRole::getCode, dto.getCode());
        wrapper.like(StringUtils.hasText(dto.getName()), SysRole::getName, dto.getName());
        wrapper.eq(dto.getStatus() != null, SysRole::getStatus, dto.getStatus());
        wrapper.orderByDesc(SysRole::getId);
        roleService.page(page, wrapper);
        Page<SysRoleVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<SysRoleVO> records = page.getRecords().stream()
                .map(e -> {
                    SysRoleVO vo = new SysRoleVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询角色")
    @GetMapping("/{id}")
    public Result<SysRoleVO> getById(@PathVariable Long id) {
        SysRole entity = roleService.getById(id);
        if (entity == null) {
            return Result.ok(null);
        }
        SysRoleVO vo = new SysRoleVO();
        BeanUtils.copyProperties(entity, vo);
        return Result.ok(vo);
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public Result<Void> add(@RequestBody SysRoleSaveDTO dto) {
        SysRole entity = new SysRole();
        BeanUtils.copyProperties(dto, entity);
        roleService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改角色")
    @PutMapping
    public Result<Void> update(@RequestBody SysRoleSaveDTO dto) {
        SysRole entity = new SysRole();
        BeanUtils.copyProperties(dto, entity);
        roleService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "分配菜单")
    @PostMapping("/{roleId}/menus")
    public Result<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(roleId, menuIds);
        return Result.ok();
    }
}
