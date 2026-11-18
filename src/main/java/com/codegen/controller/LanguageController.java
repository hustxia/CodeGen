package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.config.LanguageConfig;
import com.codegen.vo.LanguageOptionVO;
import com.codegen.vo.LanguageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目语言配置：平台级只读语言清单。
 *
 * <p>数据来自 {@link LanguageConfig}（classpath JSON），不查库——
 * 即使没执行 V7.5 迁移脚本，接口也能正常返回，避免「漏列即 500」。
 * 路径不带 {@code /api}：dev 代理 {@code /dev-api} 会去掉前缀（项目适配约定）。
 */
@Tag(name = "项目语言配置", description = "100+ 世界语言清单查询")
@RestController
@RequestMapping("/language")
public class LanguageController {

    @Operation(summary = "全部语言（可按书写方向筛选 dir=ltr/rtl）")
    @GetMapping("/list")
    public Result<List<LanguageVO>> list(
            @RequestParam(value = "dir", required = false) String dir) {
        return Result.ok(LanguageConfig.byDir(dir));
    }

    @Operation(summary = "语言下拉选项（label/value/dir/script/code）")
    @GetMapping("/options")
    public Result<List<LanguageOptionVO>> options() {
        return Result.ok(LanguageConfig.options());
    }

    @Operation(summary = "按语言码或 BCP47 标签查询单个语言")
    @GetMapping("/{code}")
    public Result<LanguageVO> detail(@PathVariable("code") String code) {
        LanguageVO hit = LanguageConfig.find(code);
        if (hit == null) {
            return Result.fail(500, "不支持的语言码：" + code);
        }
        return Result.ok(hit);
    }

    @Operation(summary = "当前默认语言与清单规模（前端兜底/诊断用）")
    @GetMapping("/default")
    public Result<DefaultLanguageVO> defaultLanguage() {
        return Result.ok(new DefaultLanguageVO(LanguageConfig.DEFAULT_LANGUAGE,
                LanguageConfig.all().size()));
    }

    /** 内联 VO：只为两个标量，不值得单开文件 */
    public record DefaultLanguageVO(String defaultLanguage, int total) {
    }
}
