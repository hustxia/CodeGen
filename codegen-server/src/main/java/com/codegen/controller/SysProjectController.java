package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.entity.SysProject;
import com.codegen.service.SysProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 系统台账 Controller。
 *
 * <p>不做分页：系统数量天然是个位到几十的量级，一次全返回，
 * 前端下拉、筛选、管理页共用同一份数据。
 */
@Tag(name = "系统台账", description = "多系统输出的根配置")
@RestController
@RequestMapping("/project")
public class SysProjectController {

    @Autowired
    private SysProjectService projectService;

    @Operation(summary = "全部系统（含停用，管理页用）")
    @GetMapping("/list")
    public Result<List<SysProject>> list() {
        return Result.ok(projectService.listAll());
    }

    @Operation(summary = "启用中的系统（新建表的下拉用）")
    @GetMapping("/options")
    public Result<List<SysProject>> options() {
        return Result.ok(projectService.listEnabled());
    }

    @Operation(summary = "默认系统，可能为 null")
    @GetMapping("/default")
    public Result<SysProject> defaultProject() {
        return Result.ok(projectService.getDefault());
    }

    @Operation(summary = "系统详情")
    @GetMapping("/{id}")
    public Result<SysProject> detail(@PathVariable("id") Long id) {
        return Result.ok(projectService.getById(id));
    }

    @Operation(summary = "新增系统（可选从已有系统复制路径配置）")
    @PostMapping
    public Result<Long> create(@RequestBody SysProject project,
                               @RequestParam(value = "copyFrom", required = false) Long copyFrom) {
        // 只复制路径与开关默认值，不复制编码/名称/根包名/网关前缀——见 SysProjectService#create
        return Result.ok(projectService.create(project, copyFrom));
    }

    @Operation(summary = "修改系统（编码在有表挂载后不可改）")
    @PutMapping
    public Result<Void> update(@RequestBody SysProject project) {
        projectService.update(project);
        return Result.ok();
    }

    @Operation(summary = "删除系统（有表挂载或为默认系统时拒绝）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        projectService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "设为默认系统")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable("id") Long id) {
        projectService.setDefault(id);
        return Result.ok();
    }
}
