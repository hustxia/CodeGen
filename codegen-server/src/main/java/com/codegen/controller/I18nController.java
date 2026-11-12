package com.codegen.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.common.result.Result;
import com.codegen.config.I18nCatalog;
import com.codegen.dto.I18nMessageDTO;
import com.codegen.service.I18nMessageService;
import com.codegen.vo.I18nVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * V8.0 产物国际化：平台侧文案翻译接口。
 *
 * <p>路径不带 {@code /api}（项目约定）；统一 {@code {code,msg,data}}，
 * 参数错误也由 {@code BusinessException} 走 HTTP 200 + 非 0 code。
 * 不新增鉴权白名单，沿用平台既有 token。
 */
@Tag(name = "V8.0 文案翻译", description = "产物多语言文案编辑")
@RestController
@RequestMapping("/i18n")
public class I18nController {

    @Autowired
    private I18nMessageService messageService;

    @Operation(summary = "该系统可用语言 + 覆盖率")
    @GetMapping("/locales")
    public Result<List<I18nVO.Locale>> locales(@RequestParam("projectId") Long projectId) {
        return Result.ok(messageService.locales(projectId));
    }

    @Operation(summary = "全部文案键（中文原文 + 分类）")
    @GetMapping("/keys")
    public Result<List<I18nVO.Key>> keys() {
        return Result.ok(messageService.keys());
    }

    @Operation(summary = "词库版本与键数（诊断用）")
    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        return Result.ok(Map.of(
                "version", I18nCatalog.version(),
                "totalKeys", I18nCatalog.totalKeys(),
                "builtinLocales", I18nCatalog.builtinLocales(),
                "rewriteRules", I18nCatalog.rules().size()));
    }

    @Operation(summary = "译文分页（records/total）")
    @PostMapping("/message/page")
    public Result<IPage<I18nVO.Message>> page(@RequestBody I18nMessageDTO.Page dto) {
        return Result.ok(messageService.page(dto));
    }

    @Operation(summary = "批量保存译文（value 为空 = 恢复回落）")
    @PostMapping("/message/save")
    public Result<Map<String, Integer>> save(@RequestBody I18nMessageDTO.Save dto) {
        return Result.ok(Map.of("saved", messageService.save(dto)));
    }

    @Operation(summary = "导出某语言的完整文案 JSON")
    @GetMapping("/message/export")
    public Result<Map<String, Object>> export(@RequestParam("projectId") Long projectId,
                                              @RequestParam("locale") String locale) {
        return Result.ok(messageService.export(projectId, locale));
    }

    @Operation(summary = "导入译文（键不在词库内的逐条跳过并回报）")
    @PostMapping("/message/import")
    public Result<Map<String, Object>> importMessages(@RequestBody I18nMessageDTO.Import dto) {
        return Result.ok(messageService.importMessages(dto));
    }
}
