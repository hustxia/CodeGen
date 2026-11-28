package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.dto.IndustryPackInstallDTO;
import com.codegen.entity.GenIndustryInstall;
import com.codegen.service.IndustryPackService;
import com.codegen.vo.IndustryInstallVO;
import com.codegen.vo.IndustryParadigmVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V9.22 行业范式模板市场：四包清单/详情 + 安装机制。
 * 安装只生成 plan（复用 V9.7 克隆链路），不建库。
 */
@Tag(name = "行业范式市场", description = "四包范式 + 安装到项目（只生成plan）")
@RestController
@RequestMapping("/gen/industry-pack")
public class IndustryPackController {

    @Autowired
    private IndustryPackService industryPackService;

    @Operation(summary = "范式包清单（行业/关键词筛选，含registry实算模块/表数）")
    @GetMapping("/list")
    public Result<List<IndustryParadigmVO>> list(
            @RequestParam(value = "industry", required = false) String industry,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(industryPackService.list(industry, keyword));
    }

    @Operation(summary = "范式包详情（含模块与表清单/去重/边界/主线）")
    @GetMapping("/detail/{key}")
    public Result<IndustryParadigmVO> detail(@PathVariable("key") String key) {
        return Result.ok(industryPackService.detail(key));
    }

    @Operation(summary = "安装到项目：生成plan+写台账（不建库）")
    @PostMapping("/install")
    public Result<IndustryInstallVO> install(@RequestBody @Valid IndustryPackInstallDTO dto) {
        return Result.ok(industryPackService.install(dto));
    }

    @Operation(summary = "项目已装范式包列表")
    @PostMapping("/installed")
    public Result<List<GenIndustryInstall>> installed(@RequestBody GenIndustryInstall query) {
        return Result.ok(industryPackService.installed(query.getProjectId()));
    }

    @Operation(summary = "移除安装台账（不删已生成的表）")
    @DeleteMapping("/install/{id}")
    public Result<Void> remove(@PathVariable("id") Long id) {
        industryPackService.remove(id);
        return Result.ok();
    }
}
