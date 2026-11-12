package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.codegen.common.exception.BusinessException;
import com.codegen.config.I18nCatalog;
import com.codegen.config.LanguageConfig;
import com.codegen.dto.I18nMessageDTO;
import com.codegen.entity.SysI18nMessage;
import com.codegen.entity.SysProject;
import com.codegen.mapper.SysI18nMessageMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.service.I18nMessageService;
import com.codegen.vo.I18nVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 译文服务实现。
 *
 * <p>词库只有 60 个键，分页在内存里做（不查库分页），但仍返回标准 {@link IPage}
 * 结构，前端与其它分页页共用同一套表格。用户译文走 Mapper 的 upsert，
 * 清空 = 写 NULL，回落链由产物运行时接管。
 */
@Service
public class I18nMessageServiceImpl implements I18nMessageService {

    private static final Logger log = LoggerFactory.getLogger(I18nMessageServiceImpl.class);
    private static final int MAX_IMPORT = 5000;
    private static final String SAFE_TAG = "^[A-Za-z0-9-]{2,32}$";

    @Autowired
    private SysI18nMessageMapper messageMapper;

    @Autowired
    private SysProjectMapper projectMapper;

    @Override
    public List<I18nVO.Key> keys() {
        List<I18nVO.Key> out = new ArrayList<>();
        for (I18nCatalog.KeyDef k : I18nCatalog.keys()) {
            out.add(new I18nVO.Key(k.key(), k.category(), k.sourceText()));
        }
        return out;
    }

    @Override
    public List<I18nVO.Locale> locales(Long projectId) {
        SysProject project = requireProject(projectId);
        List<String> tags = I18nCatalog.resolveLocales(
                project.getSupportedLanguages(), project.getDefaultLanguage());
        List<I18nVO.Locale> out = new ArrayList<>();
        for (String tag : tags) {
            I18nCatalog.LocaleMeta meta = I18nCatalog.localeMeta(tag);
            int translated = messageMapper.countTranslated(projectId, tag);
            int builtinFilled = I18nCatalog.builtin(tag).size();
            // 覆盖率口径：用户译文条数 + 内置精译里用户未覆盖的条数，封顶 totalKeys
            int total = I18nCatalog.totalKeys();
            int covered = Math.min(total, Math.max(translated, builtinFilled));
            out.add(new I18nVO.Locale(tag, meta.label(), meta.nameNative(), meta.dir(),
                    I18nCatalog.isBuiltin(tag), covered, total));
        }
        return out;
    }

    @Override
    public IPage<I18nVO.Message> page(I18nMessageDTO.Page dto) {
        Long projectId = requireProject(dto.getProjectId()).getId();
        String locale = requireLocale(dto.getLocale());
        Map<String, String> overrides = overrides(projectId, locale);
        Map<String, String> builtin = I18nCatalog.builtin(locale);
        String keyword = dto.getKeyword() == null ? "" : dto.getKeyword().trim().toLowerCase(Locale.ROOT);

        List<I18nVO.Message> all = new ArrayList<>();
        for (I18nCatalog.KeyDef k : I18nCatalog.keys()) {
            if (!keyword.isEmpty()
                    && !k.key().toLowerCase(Locale.ROOT).contains(keyword)
                    && !k.sourceText().contains(dto.getKeyword().trim())
                    && !k.category().contains(dto.getKeyword().trim())) {
                continue;
            }
            String userValue = overrides.get(k.key());
            String effective = userValue != null ? userValue : builtin.getOrDefault(k.key(), "");
            all.add(new I18nVO.Message(k.key(), k.category(), k.sourceText(),
                    builtin.getOrDefault(k.key(), ""), effective, userValue != null));
        }
        int pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 20 :
                Math.min(dto.getPageSize(), 200);
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        Page<I18nVO.Message> page = new Page<>(pageNum, pageSize, all.size());
        page.setRecords(new ArrayList<>(all.subList(from, to)));
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int save(I18nMessageDTO.Save dto) {
        Long projectId = requireProject(dto.getProjectId()).getId();
        String locale = requireLocale(dto.getLocale());
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(400, "没有需要保存的译文");
        }
        int saved = 0;
        for (I18nMessageDTO.Item item : dto.getItems()) {
            if (item == null || !I18nCatalog.isKnownKey(item.getKey())) {
                continue;
            }
            SysI18nMessage entity = new SysI18nMessage();
            entity.setId(IdWorker.getId());
            entity.setProjectId(projectId);
            entity.setLocale(locale);
            entity.setMsgKey(item.getKey());
            entity.setMsgValue(blankToNull(item.getValue()));
            messageMapper.upsert(entity);
            saved++;
        }
        log.info("[I18nMessage] 保存译文 project={} locale={} saved={}", projectId, locale, saved);
        return saved;
    }

    @Override
    public Map<String, Object> export(Long projectId, String locale) {
        Long pid = requireProject(projectId).getId();
        String loc = requireLocale(locale);
        Map<String, String> overrides = overrides(pid, loc);
        Map<String, String> builtin = I18nCatalog.builtin(loc);
        List<Map<String, String>> items = new ArrayList<>();
        for (I18nCatalog.KeyDef k : I18nCatalog.keys()) {
            String value = overrides.containsKey(k.key()) ? overrides.get(k.key())
                    : builtin.getOrDefault(k.key(), "");
            Map<String, String> row = new LinkedHashMap<>();
            row.put("key", k.key());
            row.put("value", value == null ? "" : value);
            items.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", I18nCatalog.version());
        out.put("locale", loc);
        out.put("total", items.size());
        out.put("items", items);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importMessages(I18nMessageDTO.Import dto) {
        Long projectId = requireProject(dto.getProjectId()).getId();
        String locale = requireLocale(dto.getLocale());
        List<I18nMessageDTO.Item> items = dto.getItems() == null ? List.of() : dto.getItems();
        if (items.size() > MAX_IMPORT) {
            throw new BusinessException(400, "单次导入不能超过 " + MAX_IMPORT + " 条");
        }
        Map<String, String> existing = overrides(projectId, locale);
        boolean overwrite = !Boolean.FALSE.equals(dto.getOverwrite());
        int imported = 0;
        List<String> skippedKeys = new ArrayList<>();
        for (I18nMessageDTO.Item item : items) {
            if (item == null || !I18nCatalog.isKnownKey(item.getKey())) {
                skippedKeys.add(item == null ? "(null)" : item.getKey());
                continue;
            }
            if (!overwrite && existing.containsKey(item.getKey())) {
                skippedKeys.add(item.getKey());
                continue;
            }
            SysI18nMessage entity = new SysI18nMessage();
            entity.setId(IdWorker.getId());
            entity.setProjectId(projectId);
            entity.setLocale(locale);
            entity.setMsgKey(item.getKey());
            entity.setMsgValue(blankToNull(item.getValue()));
            messageMapper.upsert(entity);
            imported++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", imported);
        out.put("skipped", skippedKeys.size());
        out.put("skippedKeys", skippedKeys);
        return out;
    }

    @Override
    public Map<String, String> overrides(Long projectId, String locale) {
        Map<String, String> out = new LinkedHashMap<>();
        if (projectId == null || locale == null) {
            return out;
        }
        for (SysI18nMessage row : messageMapper.selectByProjectLocale(projectId, locale)) {
            out.put(row.getMsgKey(), row.getMsgValue());
        }
        return out;
    }

    // ==================== 校验 ====================

    private SysProject requireProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(400, "缺少系统 ID");
        }
        SysProject p = projectMapper.selectById(projectId);
        if (p == null) {
            throw new BusinessException(404, "系统不存在：" + projectId);
        }
        return p;
    }

    private String requireLocale(String locale) {
        if (locale == null || !locale.matches(SAFE_TAG)) {
            throw new BusinessException(400, "语言标签不合法：" + locale);
        }
        // 白名单：V7.5 LanguageConfig 下拉里的唯一 BCP47 标签 + 8 个内置精译
        boolean allowed = I18nCatalog.isBuiltin(locale);
        if (!allowed) {
            for (var option : LanguageConfig.options()) {
                if (option.getValue().equals(locale)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            throw new BusinessException(400, "不支持的语言标签：" + locale);
        }
        return locale;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
