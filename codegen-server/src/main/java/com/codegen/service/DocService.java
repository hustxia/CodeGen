package com.codegen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.DocQuery;
import com.codegen.entity.SysDoc;
import com.codegen.vo.DocMetaVO;
import com.codegen.vo.DocVO;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 平台文档中心（V6.9）：上传、列表、预览能力探测、下载取文件。
 */
public interface DocService {

    DocVO upload(MultipartFile file);

    Page<DocVO> page(DocQuery query);

    DocMetaVO meta(Long id);

    void delete(Long id);

    /**
     * 原始文件（下载/浏览器渲染取原件用）
     */
    Path sourceOf(Long id);

    /**
     * 可交给 pdf.js 的 PDF（原件 PDF 直接返回，Office 走转换）
     */
    Path previewPdfOf(Long id);

    SysDoc getOrThrow(Long id);

    /**
     * 预览开关（doc.preview.enabled）；关闭时预览端点直接拒绝
     */
    boolean previewEnabled();
}
