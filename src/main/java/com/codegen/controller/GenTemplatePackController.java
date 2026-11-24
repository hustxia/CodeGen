package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.service.GenTemplatePackService;
import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenTemplatePackImportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * V9.19 模板市场：行业模板包导出 / 导入 / 删除用户包。
 *
 * <p>导出返回文件字节流（带 Content-Disposition），导入与删除返回统一 {@link Result}。
 * 列表沿用 {@code /gen/ai-build/templates}，并新增 source 字段区分预置/用户包。
 */
@Tag(name = "模板市场", description = "行业模板包导入/导出（用户自定义包）")
@RestController
@RequestMapping("/gen/template-pack")
public class GenTemplatePackController {

    @Autowired
    private GenTemplatePackService templatePackService;

    @Operation(summary = "模板清单（含来源 builtin/user、分类、表数量）")
    @GetMapping("/list")
    public Result<List<GenAiTemplateVO>> list() {
        return Result.ok(templatePackService.list());
    }

    @Operation(summary = "导出单个模板 JSON（key）或多选 ZIP（keys=a,b,c）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "keys", required = false) String keys) {
        GenTemplatePackService.PackFile pack = (keys != null && !keys.isBlank())
                ? templatePackService.exportZip(keys)
                : templatePackService.export(key);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(pack.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(pack.filename(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pack.content().length);
        return ResponseEntity.ok().headers(headers).body(pack.content());
    }

    @Operation(summary = "导入模板包（multipart file，支持 .json / .zip）；overwrite=true 覆盖同名 key")
    @PostMapping("/import")
    public Result<GenTemplatePackImportVO> importPack(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return Result.ok(templatePackService.importPack(file, overwrite));
    }

    @Operation(summary = "删除用户自定义包（预置模板不可删）")
    @DeleteMapping("/{key}")
    public Result<Void> delete(@PathVariable("key") String key) {
        templatePackService.deleteUserPack(key);
        return Result.ok();
    }
}
