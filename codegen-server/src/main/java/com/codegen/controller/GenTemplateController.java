package com.codegen.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.dto.GenTemplateCopyDTO;
import com.codegen.dto.GenTemplatePageDTO;
import com.codegen.dto.GenTemplateValidateDTO;
import com.codegen.entity.GenTemplate;
import com.codegen.service.GenTemplateService;
import com.codegen.vo.GenTemplateHistoryVO;
import com.codegen.common.result.Result;
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
 * 代码生成器-模板配置 Controller
 */
@Tag(name = "代码模板", description = "代码生成器：模板在线配置（全局+表级覆盖）")
@RestController
@RequestMapping("/gen/template")
public class GenTemplateController {

    @Autowired
    private GenTemplateService genTemplateService;

    @Operation(summary = "模板分页查询")
    @PostMapping("/page")
    public Result<IPage<GenTemplate>> page(@RequestBody GenTemplatePageDTO dto) {
        return Result.ok(genTemplateService.pageList(dto));
    }

    @Operation(summary = "模板详情（含内容）")
    @GetMapping("/{id}")
    public Result<GenTemplate> detail(@PathVariable("id") Long id) {
        return Result.ok(genTemplateService.detail(id));
    }

    @Operation(summary = "模板列表（下拉用）")
    @GetMapping("/list")
    public Result<List<GenTemplate>> list(@RequestParam(value = "tableId", required = false) Long tableId) {
        return Result.ok(genTemplateService.listByTable(tableId));
    }

    @Operation(summary = "新增模板")
    @PostMapping
    public Result<Void> create(@RequestBody GenTemplate template) {
        genTemplateService.create(template);
        return Result.ok();
    }

    @Operation(summary = "修改模板")
    @PutMapping
    public Result<Void> update(@RequestBody GenTemplate template) {
        genTemplateService.update(template);
        return Result.ok();
    }

    @Operation(summary = "删除模板（物理删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        genTemplateService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "复制为表级模板")
    @PostMapping("/copy")
    public Result<Void> copy(@RequestBody GenTemplateCopyDTO dto) {
        genTemplateService.copy(dto.getId(), dto.getTableId());
        return Result.ok();
    }

    @Operation(summary = "模板语法校验")
    @PostMapping("/validate")
    public Result<Void> validate(@RequestBody GenTemplateValidateDTO dto) {
        genTemplateService.validate(dto.getContent(), dto.getTplCode());
        return Result.ok();
    }

    @Operation(summary = "模板版本历史（不含内容）")
    @GetMapping("/{id}/history")
    public Result<List<GenTemplateHistoryVO>> history(@PathVariable("id") Long id) {
        return Result.ok(genTemplateService.history(id));
    }

    @Operation(summary = "指定版本快照（含内容）")
    @GetMapping("/{id}/history/{version}")
    public Result<GenTemplateHistoryVO> historyDetail(@PathVariable("id") Long id,
                                                      @PathVariable("version") Integer version) {
        return Result.ok(genTemplateService.historyDetail(id, version));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/{id}/rollback/{version}")
    public Result<Integer> rollback(@PathVariable("id") Long id, @PathVariable("version") Integer version) {
        return Result.ok(genTemplateService.rollback(id, version));
    }
}
