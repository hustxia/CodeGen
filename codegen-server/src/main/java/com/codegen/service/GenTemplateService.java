package com.codegen.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.dto.GenTemplatePageDTO;
import com.codegen.entity.GenTemplate;

import java.util.List;

/**
 * 代码生成器-模板配置 Service
 */
public interface GenTemplateService {

    /**
     * 分页查询
     */
    IPage<GenTemplate> pageList(GenTemplatePageDTO dto);

    /**
     * 详情（含 content）
     */
    GenTemplate detail(Long id);

    /**
     * 列表（下拉用，不含 content；tableId 为空查全部）
     */
    List<GenTemplate> listByTable(Long tableId);

    /**
     * 新增模板（同 tpl_code+table_id 冲突 409）
     */
    void create(GenTemplate template);

    /**
     * 修改模板（保存即生效）
     */
    void update(GenTemplate template);

    /**
     * 删除模板（物理删除）
     */
    void delete(Long id);

    /**
     * 复制为表级模板
     */
    void copy(Long id, Long tableId);

    /**
     * 模板语法校验（用样例模型试渲染）
     */
    void validate(String content, String tplCode);

    /**
     * 版本历史列表（不含 content）
     */
    List<com.codegen.vo.GenTemplateHistoryVO> history(Long id);

    /**
     * 指定版本快照（含 content）
     */
    com.codegen.vo.GenTemplateHistoryVO historyDetail(Long id, Integer version);

    /**
     * 回滚到指定版本，返回回滚后生成的新版本号
     */
    Integer rollback(Long id, Integer version);
}
