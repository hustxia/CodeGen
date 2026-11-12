package com.codegen.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.dto.I18nMessageDTO;
import com.codegen.vo.I18nVO;

import java.util.List;
import java.util.Map;

/**
 * V8.0 平台侧译文服务：键清单、覆盖率、分页编辑、导入导出，以及给脚手架用的覆盖表。
 */
public interface I18nMessageService {

    List<I18nVO.Key> keys();

    List<I18nVO.Locale> locales(Long projectId);

    IPage<I18nVO.Message> page(I18nMessageDTO.Page dto);

    int save(I18nMessageDTO.Save dto);

    Map<String, Object> export(Long projectId, String locale);

    Map<String, Object> importMessages(I18nMessageDTO.Import dto);

    /** 用户译文覆盖表（key → value），脚手架组装语言包时调用 */
    Map<String, String> overrides(Long projectId, String locale);
}
