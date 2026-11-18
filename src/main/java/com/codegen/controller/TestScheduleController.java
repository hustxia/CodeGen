package com.codegen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.result.Result;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestSchedule;
import com.codegen.service.TestScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V9.2 定时回归任务接口。
 */
@Tag(name = "V9.2 定时回归", description = "任务增删改查、启停")
@RestController
@RequestMapping("/test/schedule")
public class TestScheduleController {

    @Autowired
    private TestScheduleService scheduleService;

    @Operation(summary = "分页查询定时任务")
    @PostMapping("/page")
    public Result<Page<TestSchedule>> page(@RequestBody TestCenterDTO.SchedulePage dto) {
        return Result.ok(scheduleService.page(dto));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<TestSchedule> get(@PathVariable Long id) {
        return Result.ok(scheduleService.getById(id));
    }

    @Operation(summary = "新增/更新任务")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody TestCenterDTO.ScheduleSave dto) {
        return Result.ok(scheduleService.save(dto));
    }

    @Operation(summary = "删除任务")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.remove(id);
        return Result.ok();
    }

    @Operation(summary = "启停任务")
    @PostMapping("/{id}/enabled/{flag}")
    public Result<Void> setEnabled(@PathVariable Long id, @PathVariable Integer flag) {
        scheduleService.setEnabled(id, flag);
        return Result.ok();
    }
}
