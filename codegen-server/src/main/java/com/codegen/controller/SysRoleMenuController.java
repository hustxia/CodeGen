package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.SysRoleMenuBatchDTO;
import com.codegen.dto.SysRoleMenuPageDTO;
import com.codegen.dto.SysRoleMenuSaveDTO;
import com.codegen.entity.SysRoleMenu;
import com.codegen.service.SysRoleMenuService;
import com.codegen.vo.SysRoleMenuVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色菜单关联 Controller
 */
@Tag(name = "角色菜单", description = "角色菜单关联管理")
@RestController
@RequestMapping("/role-menu")
public class SysRoleMenuController {

    @Autowired
    private SysRoleMenuService roleMenuService;

    @Operation(summary = "分页查询角色菜单")
    @PostMapping("/page")
    public Result<Page<SysRoleMenuVO>> page(@RequestBody SysRoleMenuPageDTO dto) {
        Page<SysRoleMenu> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getRoleId() != null, SysRoleMenu::getRoleId, dto.getRoleId());
        wrapper.eq(dto.getMenuId() != null, SysRoleMenu::getMenuId, dto.getMenuId());
        wrapper.orderByDesc(SysRoleMenu::getId);
        roleMenuService.page(page, wrapper);
        Page<SysRoleMenuVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<SysRoleMenuVO> records = page.getRecords().stream()
                .map(e -> {
                    SysRoleMenuVO vo = new SysRoleMenuVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "根据ID查询角色菜单")
    @GetMapping("/{id}")
    public Result<SysRoleMenuVO> getById(@PathVariable Long id) {
        SysRoleMenu entity = roleMenuService.getById(id);
        if (entity == null) {
            return Result.ok(null);
        }
        SysRoleMenuVO vo = new SysRoleMenuVO();
        BeanUtils.copyProperties(entity, vo);
        return Result.ok(vo);
    }

    @Operation(summary = "新增角色菜单")
    @PostMapping
    public Result<Void> add(@RequestBody SysRoleMenuSaveDTO dto) {
        SysRoleMenu entity = new SysRoleMenu();
        BeanUtils.copyProperties(dto, entity);
        roleMenuService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改角色菜单")
    @PutMapping
    public Result<Void> update(@RequestBody SysRoleMenuSaveDTO dto) {
        SysRoleMenu entity = new SysRoleMenu();
        BeanUtils.copyProperties(dto, entity);
        roleMenuService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "查询角色已授权的菜单ID")
    @GetMapping("/menu-ids")
    public Result<List<Long>> menuIds(@RequestParam Long roleId) {
        return Result.ok(roleMenuService.menuIdsOf(roleId));
    }

    @Operation(summary = "整体覆盖角色菜单（角色授权保存）")
    @PostMapping("/batch")
    public Result<Integer> batch(@RequestBody SysRoleMenuBatchDTO dto) {
        int written = roleMenuService.replaceMenus(dto.getRoleId(), dto.getMenuIds());
        return Result.ok(written);
    }

    @Operation(summary = "删除角色菜单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleMenuService.removeById(id);
        return Result.ok();
    }
}
