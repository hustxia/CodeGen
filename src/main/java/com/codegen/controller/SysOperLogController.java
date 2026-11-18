package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.result.Result;
import com.codegen.dto.SysOperLogPageDTO;
import com.codegen.entity.SysOperLog;
import com.codegen.mapper.SysOperLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志 Controller。
 *
 * <p>日志只读：提供查询与清理，不提供新增/修改——能改的日志没有审计价值。
 */
@Tag(name = "操作日志", description = "操作日志查询")
@RestController
@RequestMapping("/oper-log")
public class SysOperLogController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SysOperLogMapper operLogMapper;

    @Operation(summary = "分页查询操作日志")
    @PostMapping("/page")
    public Result<Page<SysOperLog>> page(@RequestBody SysOperLogPageDTO dto) {
        Page<SysOperLog> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getTitle()), SysOperLog::getTitle, dto.getTitle());
        wrapper.like(StringUtils.hasText(dto.getOperName()), SysOperLog::getOperName, dto.getOperName());
        wrapper.eq(StringUtils.hasText(dto.getOperType()), SysOperLog::getOperType, dto.getOperType());
        wrapper.eq(dto.getStatus() != null, SysOperLog::getStatus, dto.getStatus());
        wrapper.ge(StringUtils.hasText(dto.getStartTime()), SysOperLog::getOperTime, parse(dto.getStartTime()));
        wrapper.le(StringUtils.hasText(dto.getEndTime()), SysOperLog::getOperTime, parse(dto.getEndTime()));
        wrapper.orderByDesc(SysOperLog::getId);
        operLogMapper.selectPage(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "操作类型字典（前端筛选下拉用）")
    @PostMapping("/types")
    public Result<List<String>> types() {
        return Result.ok(List.of("INSERT", "UPDATE", "DELETE", "IMPORT", "EXPORT", "OTHER"));
    }

    @Operation(summary = "按时间清理日志（只删早于 before 的记录）")
    @DeleteMapping("/before")
    public Result<Integer> cleanBefore(@RequestParam("before") String before) {
        LocalDateTime time = parse(before);
        if (time == null) {
            return Result.fail(400, "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SysOperLog::getOperTime, time);
        return Result.ok(operLogMapper.delete(wrapper));
    }

    private LocalDateTime parse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            // 前端日期控件可能只给到天，补成当天 00:00:00
            return LocalDateTime.parse(text.length() == 10 ? text + " 00:00:00" : text, TIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
