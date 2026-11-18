package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.DocProperties;
import com.codegen.dto.DocQuery;
import com.codegen.entity.SysDoc;
import com.codegen.mapper.SysDocMapper;
import com.codegen.service.DocPreviewEngine;
import com.codegen.service.DocService;
import com.codegen.service.DocStorage;
import com.codegen.vo.DocMetaVO;
import com.codegen.vo.DocVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 平台文档中心实现（V6.9）。
 *
 * <p>大小上限做两层：Spring 的 multipart 配置负责「请求还没进业务就被拒」，
 * 这里负责「业务语义的错误提示」（超限要说清楚上限是多少，不能只抛 500）。
 */
@Service
public class DocServiceImpl implements DocService {

    private static final Logger log = LoggerFactory.getLogger(DocServiceImpl.class);

    private static final int MAX_NAME_LEN = 255;

    @Autowired
    private DocProperties props;

    @Autowired
    private SysDocMapper docMapper;

    @Autowired
    private DocStorage storage;

    @Autowired
    private DocPreviewEngine engine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public DocVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择要上传的文件");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());
        String ext = DocStorage.extOf(originalName);
        Set<String> allowed = props.allowedExtSet();
        if (ext.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "无法识别文件类型，文件名必须带扩展名");
        }
        if (!allowed.contains(ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "不支持的扩展名 ." + ext + "，仅支持：" + props.getAllowedExts());
        }
        long maxBytes = (long) props.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "文件超过大小上限（" + props.getMaxSizeMb() + "MB）");
        }

        String relativePath;
        try {
            relativePath = storage.store(file, ext);
        } catch (IOException e) {
            log.error("文档落盘失败", e);
            throw new BusinessException(ResultCode.FAIL.getCode(), "文件保存失败：" + e.getMessage());
        }

        SysDoc doc = new SysDoc();
        doc.setOriginalName(truncateName(originalName, ext));
        doc.setStorageName(relativePath.substring(relativePath.lastIndexOf('/') + 1));
        doc.setRelativePath(relativePath);
        doc.setExt(ext);
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setCreateBy(UserContext.getUserId());
        doc.setUpdateBy(UserContext.getUserId());
        docMapper.insert(doc);

        log.info("文档上传：id={} name={} size={} path={}", doc.getId(), originalName, file.getSize(), relativePath);
        return toVO(doc, engine.modeOf(doc), UserContext.getUsername());
    }

    @Override
    public Page<DocVO> page(DocQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        pageSize = Math.min(Math.max(pageSize, 1), 100);

        LambdaQueryWrapper<SysDoc> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getName())) {
            qw.like(SysDoc::getOriginalName, query.getName().trim());
        }
        List<String> extFilter = extFilterOf(query);
        if (extFilter != null && extFilter.isEmpty()) {
            // 传了不认识的大类：不返回全量，直接空结果，避免「筛选失效看起来像全都能查」
            Page<DocVO> empty = new Page<>(pageNum, pageSize);
            empty.setTotal(0);
            empty.setRecords(List.of());
            return empty;
        }
        if (extFilter != null) {
            qw.in(SysDoc::getExt, extFilter);
        }
        qw.orderByDesc(SysDoc::getCreateTime).orderByDesc(SysDoc::getId);

        Page<SysDoc> raw = docMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        Map<Long, String> uploaders = uploaderNames(raw.getRecords());
        Page<DocVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<DocVO> rows = new ArrayList<>();
        for (SysDoc doc : raw.getRecords()) {
            String name = doc.getCreateBy() == null ? "—" : uploaders.getOrDefault(doc.getCreateBy(), "—");
            rows.add(toVO(doc, engine.modeOf(doc), name));
        }
        result.setRecords(rows);
        return result;
    }

    @Override
    public DocMetaVO meta(Long id) {
        SysDoc doc = getOrThrow(id);
        DocPreviewEngine.Mode mode = engine.modeOf(doc);
        DocMetaVO vo = new DocMetaVO();
        vo.setId(doc.getId());
        vo.setPreviewMode(mode.code());
        vo.setEngine(engine.engineName());
        vo.setConvertible(mode != DocPreviewEngine.Mode.UNSUPPORTED && engine.previewEnabled());
        if (mode == DocPreviewEngine.Mode.UNSUPPORTED) {
            vo.setReason(engine.unsupportedReason(doc));
        } else if (!engine.previewEnabled()) {
            vo.setReason("预览功能已关闭（doc.preview.enabled=false），请下载查看");
        }
        return vo;
    }

    @Override
    public void delete(Long id) {
        SysDoc doc = getOrThrow(id);
        docMapper.deleteById(doc.getId());
        log.info("文档删除：id={} name={}", id, doc.getOriginalName());
    }

    @Override
    public Path sourceOf(Long id) {
        return storage.resolve(getOrThrow(id).getRelativePath());
    }

    @Override
    public Path previewPdfOf(Long id) {
        return engine.pdfOf(getOrThrow(id));
    }

    @Override
    public SysDoc getOrThrow(Long id) {
        SysDoc doc = id == null ? null : docMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "文档不存在或已删除");
        }
        return doc;
    }

    @Override
    public boolean previewEnabled() {
        return engine.previewEnabled();
    }

    private DocVO toVO(SysDoc doc, DocPreviewEngine.Mode mode, String uploaderName) {
        DocVO vo = new DocVO();
        vo.setId(doc.getId());
        vo.setOriginalName(doc.getOriginalName());
        vo.setExt(doc.getExt());
        vo.setFileSize(doc.getFileSize());
        vo.setContentType(doc.getContentType());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUploaderName(uploaderName);
        vo.setPreviewMode(mode.code());
        return vo;
    }

    /**
     * 大类 → 扩展名集合；未传大类返回 null。
     */
    private List<String> extFilterOf(DocQuery query) {
        if (StringUtils.hasText(query.getExt())) {
            return List.of(query.getExt().trim().toLowerCase(Locale.ROOT));
        }
        String category = query.getCategory();
        if (!StringUtils.hasText(category)) {
            return null;
        }
        switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "pdf":
                return List.of("pdf");
            case "word":
                return List.of("doc", "docx");
            case "excel":
                return List.of("xls", "xlsx");
            case "ppt":
                return List.of("ppt", "pptx");
            default:
                return List.of();
        }
    }

    /**
     * 批量取上传人展示名（一次 SQL，避免逐行 N+1）。
     */
    private Map<Long, String> uploaderNames(List<SysDoc> docs) {
        Map<Long, String> map = new HashMap<>();
        List<Long> ids = docs.stream().map(SysDoc::getCreateBy).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return map;
        }
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        try {
            jdbcTemplate.query("SELECT id, username FROM sys_user WHERE id IN (" + placeholders + ")",
                    rs -> {
                        map.put(rs.getLong("id"), rs.getString("username"));
                    },
                    ids.toArray());
        } catch (Exception e) {
            log.warn("查询上传人失败（降级显示—）：{}", e.getMessage());
        }
        return map;
    }

    private String safeOriginalName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unnamed";
        }
        // 去掉路径分隔符：只取最后一段，避免原文件名被当作路径
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isBlank() ? "unnamed" : name;
    }

    /**
     * 超长文件名截断到 255 字符（列宽上限），保留扩展名（V6.6 A-17 的教训）。
     */
    private String truncateName(String name, String ext) {
        if (name.length() <= MAX_NAME_LEN) {
            return name;
        }
        int keep = Math.max(1, MAX_NAME_LEN - ext.length() - 1);
        return name.substring(0, Math.min(keep, name.length())) + "." + ext;
    }
}
