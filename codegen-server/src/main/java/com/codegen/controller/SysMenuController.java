package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.SysMenuPageDTO;
import com.codegen.dto.SysMenuSaveDTO;
import com.codegen.entity.SysMenu;
import com.codegen.service.SysMenuService;
import com.codegen.vo.SysMenuVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单 Controller
 */
@Tag(name = "菜单", description = "菜单管理")
@RestController
@RequestMapping("/menu")
public class SysMenuController {

    @Autowired
    private SysMenuService menuService;

    @Operation(summary = "分页查询菜单")
    @PostMapping("/page")
    public Result<Page<SysMenuVO>> page(@RequestBody SysMenuPageDTO dto) {
        Page<SysMenu> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getName()), SysMenu::getName, dto.getName());
        wrapper.eq(dto.getParentId() != null, SysMenu::getParentId, dto.getParentId());
        wrapper.eq(dto.getType() != null, SysMenu::getType, dto.getType());
        wrapper.eq(dto.getStatus() != null, SysMenu::getStatus, dto.getStatus());
        wrapper.orderByAsc(SysMenu::getSort);
        menuService.page(page, wrapper);
        Page<SysMenuVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        List<SysMenuVO> records = page.getRecords().stream()
                .map(e -> {
                    SysMenuVO vo = new SysMenuVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "查询菜单树")
    @GetMapping("/tree")
    public Result<List<SysMenuVO>> tree(@RequestParam(required = false) Integer type,
                                        @RequestParam(required = false) Integer status) {
        return Result.ok(menuService.tree(type, status));
    }

    @Operation(summary = "根据ID查询菜单")
    @GetMapping("/{id}")
    public Result<SysMenuVO> getById(@PathVariable Long id) {
        SysMenu entity = menuService.getById(id);
        if (entity == null) {
            return Result.ok(null);
        }
        SysMenuVO vo = new SysMenuVO();
        BeanUtils.copyProperties(entity, vo);
        return Result.ok(vo);
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    public Result<Void> add(@RequestBody SysMenuSaveDTO dto) {
        SysMenu entity = new SysMenu();
        BeanUtils.copyProperties(dto, entity);
        menuService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改菜单")
    @PutMapping
    public Result<Void> update(@RequestBody SysMenuSaveDTO dto) {
        SysMenu entity = new SysMenu();
        BeanUtils.copyProperties(dto, entity);
        menuService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.removeById(id);
        return Result.ok();
    }
}
