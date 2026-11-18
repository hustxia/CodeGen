package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestEnv;
import com.codegen.service.TestEnvService;
import com.codegen.vo.TestVO;
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

/**
 * V9.2 测试环境接口。
 *
 * <p>下沉到环境维度的校验只有两条：baseUrl 必须是 http(s) 绝对地址、名称必填。
 * 生产环境标记由用户显式勾选，不做自动推断——猜错会把写请求放进生产。
 */
@Tag(name = "V9.2 测试环境", description = "测试环境配置与探活")
@RestController
@RequestMapping("/test/env")
public class TestEnvController {

    @Autowired
    private TestEnvService envService;

    @Operation(summary = "分页查询环境")
    @PostMapping("/page")
    public Result<Page<TestVO.Env>> page(@RequestBody TestCenterDTO.EnvPage dto) {
        Page<TestEnv> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<TestEnv> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getProjectId() != null, TestEnv::getProjectId, dto.getProjectId());
        wrapper.like(StringUtils.hasText(dto.getKeyword()), TestEnv::getName, dto.getKeyword());
        wrapper.orderByAsc(TestEnv::getId);
        envService.page(page, wrapper);
        Page<TestVO.Env> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(envService::toVO).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "根据 ID 查询环境")
    @GetMapping("/{id}")
    public Result<TestVO.Env> getById(@PathVariable Long id) {
        return Result.ok(envService.toVO(envService.getById(id)));
    }

    @Operation(summary = "新增环境")
    @PostMapping
    public Result<Void> add(@RequestBody TestCenterDTO.EnvSave dto) {
        validate(dto);
        TestEnv entity = new TestEnv();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getIsProd() == null) {
            entity.setIsProd(0);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        envService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改环境")
    @PutMapping
    public Result<Void> update(@RequestBody TestCenterDTO.EnvSave dto) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "id 不能为空");
        }
        validate(dto);
        TestEnv entity = new TestEnv();
        BeanUtils.copyProperties(dto, entity);
        envService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除环境")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        envService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "环境探活")
    @PostMapping("/{id}/verify")
    public Result<TestVO.EnvCheck> verify(@PathVariable Long id) {
        return Result.ok(envService.verify(id));
    }

    private static void validate(TestCenterDTO.EnvSave dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new BusinessException(400, "环境名不能为空");
        }
        if (!StringUtils.hasText(dto.getBaseUrl())) {
            throw new BusinessException(400, "baseUrl 不能为空");
        }
        String url = dto.getBaseUrl().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(400, "baseUrl 必须是以 http:// 或 https:// 开头的绝对地址");
        }
    }
}
