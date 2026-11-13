package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestSuite;
import com.codegen.service.TestCaseService;
import com.codegen.service.TestSuiteGenService;
import com.codegen.service.TestSuiteService;
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

import java.util.List;

/**
 * V9.2 测试套件接口。
 *
 * <p>列表带 {@code caseCount}：用例库左树要显示每个套件的用例数，
 * 单独再请求一次会让树渲染闪烁。套件数量是十级，逐条 count 的 N+1 可接受。
 */
@Tag(name = "V9.2 测试套件", description = "测试套件管理")
@RestController
@RequestMapping("/test/suite")
public class TestSuiteController {

    @Autowired
    private TestSuiteService suiteService;

    @Autowired
    private TestCaseService caseService;

    @Autowired
    private TestSuiteGenService suiteGenService;

    @Operation(summary = "按业务表生成冒烟用例")
    @PostMapping("/generate")
    public Result<TestVO.GenResult> generate(@RequestParam("tableId") Long tableId) {
        return Result.ok(suiteGenService.generate(tableId));
    }

    @Operation(summary = "分页查询套件（含用例数）")
    @PostMapping("/page")
    public Result<Page<TestVO.Suite>> page(@RequestBody TestCenterDTO.SuitePage dto) {
        Page<TestSuite> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<TestSuite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getProjectId() != null, TestSuite::getProjectId, dto.getProjectId());
        wrapper.eq(StringUtils.hasText(dto.getType()), TestSuite::getType, dto.getType());
        wrapper.like(StringUtils.hasText(dto.getKeyword()), TestSuite::getName, dto.getKeyword());
        wrapper.orderByAsc(TestSuite::getId);
        suiteService.page(page, wrapper);

        List<TestVO.Suite> records = page.getRecords().stream().map(e -> new TestVO.Suite(
                e.getId(), e.getProjectId(), e.getTableId(), e.getName(), e.getType(), e.getSource(),
                e.getModuleName(), e.getEnabled(), e.getRemark(), countCases(e.getId()))).toList();
        Page<TestVO.Suite> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "根据 ID 查询套件")
    @GetMapping("/{id}")
    public Result<TestVO.Suite> getById(@PathVariable Long id) {
        TestSuite e = suiteService.getById(id);
        if (e == null) {
            return Result.ok(null);
        }
        return Result.ok(new TestVO.Suite(e.getId(), e.getProjectId(), e.getTableId(), e.getName(),
                e.getType(), e.getSource(), e.getModuleName(), e.getEnabled(), e.getRemark(),
                countCases(e.getId())));
    }

    @Operation(summary = "新增套件")
    @PostMapping
    public Result<Void> add(@RequestBody TestCenterDTO.SuiteSave dto) {
        TestSuite entity = new TestSuite();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (!StringUtils.hasText(entity.getType())) {
            entity.setType("api");
        }
        if (!StringUtils.hasText(entity.getSource())) {
            entity.setSource("manual");
        }
        suiteService.save(entity);
        return Result.ok();
    }

    @Operation(summary = "修改套件")
    @PutMapping
    public Result<Void> update(@RequestBody TestCenterDTO.SuiteSave dto) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "id 不能为空");
        }
        TestSuite entity = new TestSuite();
        BeanUtils.copyProperties(dto, entity);
        suiteService.updateById(entity);
        return Result.ok();
    }

    @Operation(summary = "删除套件（不级联删用例）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        suiteService.removeById(id);
        return Result.ok();
    }

    private long countCases(Long suiteId) {
        return caseService.count(new LambdaQueryWrapper<com.codegen.entity.TestCase>()
                .eq(com.codegen.entity.TestCase::getSuiteId, suiteId));
    }
}
