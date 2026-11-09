package com.codegen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import com.codegen.common.result.ResultCode;
import com.codegen.dto.DocQuery;
import com.codegen.entity.SysDoc;
import com.codegen.service.DocService;
import com.codegen.service.PermissionService;
import com.codegen.vo.DocMetaVO;
import com.codegen.vo.DocVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 平台文档中心（V6.9）。
 *
 * <p>与产物侧 V6.6 的 FileController 的关键差异：**不开放免鉴权静态直链**。
 * 平台没有「前端 img 直接引用」的需求，因此所有文件访问都走这里校验登录态与权限码。
 *
 * <p>二进制端点（preview/raw/download）不套 {code,msg,data} 信封：
 * 成功返回文件流，失败返回对应 HTTP 状态 + JSON 错误体，避免前端把错误 JSON 当文件解析。
 */
@Tag(name = "文档中心", description = "平台文档上传、列表与在线预览")
@RestController
@RequestMapping("/doc")
public class DocController {

    @Autowired
    private DocService docService;

    @Autowired
    private PermissionService permissionService;

    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    public Result<DocVO> upload(@RequestParam("file") MultipartFile file) {
        require("doc:upload");
        return Result.ok(docService.upload(file));
    }

    @Operation(summary = "文档分页列表")
    @PostMapping("/page")
    public Result<Page<DocVO>> page(@RequestBody(required = false) DocQuery query) {
        require("doc:list");
        return Result.ok(docService.page(query == null ? new DocQuery() : query));
    }

    @Operation(summary = "预览能力探测")
    @GetMapping("/meta/{id}")
    public Result<DocMetaVO> meta(@PathVariable("id") Long id) {
        require("doc:list");
        return Result.ok(docService.meta(id));
    }

    @Operation(summary = "PDF 预览流（原件 PDF 或 Office 转换结果）")
    @GetMapping("/preview/{id}")
    public ResponseEntity<?> preview(@PathVariable("id") Long id) {
        try {
            require("doc:list");
            if (!docService.previewEnabled()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "预览功能已关闭，请下载查看");
            }
            Path pdf = docService.previewPdfOf(id);
            return stream(pdf, MediaType.APPLICATION_PDF, true, null);
        } catch (BusinessException e) {
            return error(e);
        }
    }

    @Operation(summary = "原始文件流（浏览器渲染 .docx 用）")
    @GetMapping("/raw/{id}")
    public ResponseEntity<?> raw(@PathVariable("id") Long id) {
        try {
            require("doc:list");
            SysDoc doc = docService.getOrThrow(id);
            Path file = docService.sourceOf(id);
            MediaType type = doc.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(doc.getContentType());
            return stream(file, type, true, doc.getOriginalName());
        } catch (BusinessException e) {
            return error(e);
        }
    }

    @Operation(summary = "下载文档（附件，保留原始文件名）")
    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable("id") Long id) {
        try {
            require("doc:list");
            SysDoc doc = docService.getOrThrow(id);
            Path file = docService.sourceOf(id);
            return stream(file, MediaType.APPLICATION_OCTET_STREAM, false, doc.getOriginalName());
        } catch (BusinessException e) {
            return error(e);
        }
    }

    @Operation(summary = "删除文档（逻辑删，物理文件保留）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        require("doc:remove");
        docService.delete(id);
        return Result.ok();
    }

    /**
     * 流式返回。
     *
     * @param inline true = 浏览器内联展示（预览），false = 附件下载
     */
    private ResponseEntity<?> stream(Path file, MediaType type, boolean inline, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        // 禁止浏览器嗅探：避免被当成 HTML 执行
        headers.set("X-Content-Type-Options", "nosniff");
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(filename == null ? "preview" : filename, StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(filename == null ? "download" : filename, StandardCharsets.UTF_8).build();
        headers.setContentDisposition(disposition);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok().headers(headers).contentLength(file.toFile().length()).body(resource);
    }

    /**
     * 二进制端点的错误体：HTTP 状态与业务码对齐，body 仍是统一的 Result。
     */
    private ResponseEntity<Result<Void>> error(BusinessException e) {
        int status = e.getCode() == ResultCode.UNAUTHORIZED.getCode() ? 401
                : e.getCode() == ResultCode.FORBIDDEN.getCode() ? 403
                : e.getCode() == ResultCode.NOT_FOUND.getCode() ? 404
                : 400;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 权限码校验：平台此前只有登录态，文档中心的写操作必须落到权限点。
     * 超管直通，其余用户按「角色 → 菜单 perms」判定。
     */
    private void require(String perm) {
        Long userId = UserContext.getUserId();
        if (permissionService.isSuperAdmin(userId)) {
            return;
        }
        if (!permissionService.permissionsOf(userId).contains(perm)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
