package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestCase;
import com.codegen.service.TestCaseService;
import com.codegen.support.TestJson;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V9.2 测试用例接口。
 *
 * <p>保存时做最小校验：path 必须以 {@code /} 开头、asserts 必须是合法 JSON 数组。
 * 断言写错是最高频的配置错误，必须在保存时拦住，而不是执行时才报「断言不生效」。
 */
@Tag(name = "V9.2 测试用例", description = "测试用例编写、试运行")
@RestController
@RequestMapping("/test/case")
public class TestCaseController {

    @Autowired
    private TestCaseService caseService;

    @Operation(summary = "分页查询用例")
    @PostMapping("/page")
    public Result<Page<TestVO.CaseView>> page(@RequestBody TestCenterDTO.CasePage dto) {
        Page<TestCase> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getSuiteId() != null, TestCase::getSuiteId, dto.getSuiteId());
        wrapper.eq(StringUtils.hasText(dto.getSource()), TestCase::getSource, dto.getSource());
        wrapper.like(StringUtils.hasText(dto.getKeyword()), TestCase::getName, dto.getKeyword());
        wrapper.orderByAsc(TestCase::getOrderNo).orderByAsc(TestCase::getId);
        caseService.page(page, wrapper);

        Page<TestVO.CaseView> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(TestCaseController::toView).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "根据 ID 查询用例（含请求体，供编辑页回填）")
    @GetMapping("/{id}")
    public Result<TestCase> getById(@PathVariable Long id) {
        return Result.ok(caseService.getById(id));
    }

    @Operation(summary = "新增用例")
    @PostMapping
    public Result<Void> add(@RequestBody TestCenterDTO.CaseSave dto) {
        validate(dto);
        TestCase entity = new TestCase();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (!StringUtils.hasText(entity.getSource())) {
            entity.setSource("manual");
        }
        if (entity.getOrderNo() == null) {
            entity.setOrderNo(0);
        }
        caseService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改用例")
    @PutMapping
    public Result<Void> update(@RequestBody TestCenterDTO.CaseSave dto) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "id 不能为空");
        }
        validate(dto);
        TestCase entity = new TestCase();
        BeanUtils.copyProperties(dto, entity);
        caseService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除用例")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        caseService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "试运行（不落批次、不更新最近结果）")
    @PostMapping("/{id}/try")
    public Result<TestVO.TryResult> tryRun(@PathVariable Long id, @RequestParam("envId") Long envId) {
        return Result.ok(caseService.tryRun(id, envId));
    }

    private static TestVO.CaseView toView(TestCase e) {
        return new TestVO.CaseView(e.getId(), e.getSuiteId(), e.getName(), e.getSource(), e.getMethod(),
                e.getPath(), e.getAssertsJson(), e.getOrderNo(), e.getEnabled(), e.getLastResult(),
                e.getLastRunTime());
    }

    private static void validate(TestCenterDTO.CaseSave dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new BusinessException(400, "用例名不能为空");
        }
        if (dto.getSuiteId() == null) {
            throw new BusinessException(400, "请选择所属套件");
        }
        if (!StringUtils.hasText(dto.getPath()) || !dto.getPath().trim().startsWith("/")) {
            throw new BusinessException(400, "接口路径必须以 / 开头");
        }
        if (StringUtils.hasText(dto.getAssertsJson()) && TestJson.assertList(dto.getAssertsJson()).isEmpty()) {
            throw new BusinessException(400, "断言必须是合法 JSON 数组，形如 [{\"type\":\"status\",\"expect\":200}]");
        }
    }
}
