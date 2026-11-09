package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.dto.GenTemplatePageDTO;
import com.codegen.entity.GenTemplate;
import com.codegen.mapper.GenTemplateMapper;
import com.codegen.service.GenTemplateService;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import com.codegen.vo.GenTemplateHistoryVO;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 代码生成器-模板配置 Service 实现
 */
@Service
public class GenTemplateServiceImpl extends ServiceImpl<GenTemplateMapper, GenTemplate>
        implements GenTemplateService {

    @Autowired
    private GenTemplateMapper genTemplateMapper;

    @Autowired
    private GenTemplateEngine genTemplateEngine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public IPage<GenTemplate> pageList(GenTemplatePageDTO dto) {
        LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            wrapper.like(StringUtils.hasText(dto.getTplCode()), GenTemplate::getTplCode, dto.getTplCode());
            wrapper.eq(StringUtils.hasText(dto.getFileGroup()), GenTemplate::getFileGroup, dto.getFileGroup());
            wrapper.eq(dto.getTableId() != null, GenTemplate::getTableId, dto.getTableId());
        }
        wrapper.orderByAsc(GenTemplate::getTableId).orderByAsc(GenTemplate::getId);
        int pageNum = dto == null || dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto == null || dto.getPageSize() == null ? 10 : dto.getPageSize();
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public GenTemplate detail(Long id) {
        GenTemplate tpl = getById(id);
        if (tpl == null) {
            throw new BusinessException(404, "模板不存在：" + id);
        }
        return tpl;
    }

    @Override
    public List<GenTemplate> listByTable(Long tableId) {
        LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tableId != null, GenTemplate::getTableId, tableId);
        wrapper.select(GenTemplate.class, i -> !"content".equals(i.getColumn()));
        wrapper.orderByAsc(GenTemplate::getTableId).orderByAsc(GenTemplate::getId);
        return list(wrapper);
    }

    @Override
    public void create(GenTemplate template) {
        Long tableId = template.getTableId() == null ? 0L : template.getTableId();
        template.setTableId(tableId);
        template.setId(null);
        if (existsActive(template.getTplCode(), tableId)) {
            throw new BusinessException(409, "同编码模板已存在：" + template.getTplCode()
                    + (tableId > 0 ? "（表级）" : "（全局）"));
        }
        genTemplateMapper.purgeDeletedResidual(template.getTplCode(), tableId);
        template.setVersion(1);
        save(template);
        snapshot(template, 1);
        genTemplateEngine.evict();
    }

    @Override
    public void update(GenTemplate template) {
        GenTemplate exist = template.getId() == null ? null : getById(template.getId());
        if (exist == null) {
            throw new BusinessException(404, "模板不存在");
        }
        // 用户改动内容后标记为非系统种子，避免后续内置模板升级覆盖用户编辑
        if (template.getContent() != null && !template.getContent().equals(exist.getContent())
                && template.getRemark() == null
                && exist.getRemark() != null && exist.getRemark().startsWith("全局种子模板")) {
            template.setRemark("用户自定义");
        }
        // 覆盖前先把当前版本存快照，回滚时按 version 取回内容
        int oldVersion = exist.getVersion() == null ? 1 : exist.getVersion();
        snapshot(exist, oldVersion);
        template.setVersion(oldVersion + 1);
        updateById(template);
        genTemplateEngine.evict();
    }

    @Override
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(404, "模板不存在：" + id);
        }
        genTemplateMapper.physicalDeleteById(id);
        genTemplateEngine.evict();
    }

    @Override
    public void copy(Long id, Long tableId) {
        if (tableId == null || tableId <= 0) {
            throw new BusinessException(400, "请选择目标表");
        }
        GenTemplate src = detail(id);
        if (existsActive(src.getTplCode(), tableId)) {
            throw new BusinessException(409, "该表已存在同编码模板覆盖，请先删除旧覆盖");
        }
        genTemplateMapper.purgeDeletedResidual(src.getTplCode(), tableId);
        GenTemplate copy = new GenTemplate();
        copy.setTplCode(src.getTplCode());
        copy.setTplName(src.getTplName());
        copy.setFileGroup(src.getFileGroup());
        copy.setContent(src.getContent());
        copy.setPathPattern(src.getPathPattern());
        copy.setTableId(tableId);
        copy.setStatus(1);
        copy.setVersion(1);
        copy.setRemark("复制自模板#" + id);
        save(copy);
        snapshot(copy, 1);
        genTemplateEngine.evict();
    }

    // ==================== 版本历史 ====================

    @Override
    public List<GenTemplateHistoryVO> history(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(404, "模板不存在：" + id);
        }
        return jdbcTemplate.query("SELECT id, template_id, tpl_code, table_id, version, path_pattern, "
                        + "operator, create_time FROM gen_template_history WHERE template_id = ? "
                        + "ORDER BY version DESC, id DESC",
                (rs, i) -> {
                    GenTemplateHistoryVO vo = new GenTemplateHistoryVO();
                    vo.setId(rs.getLong("id"));
                    vo.setTemplateId(rs.getLong("template_id"));
                    vo.setTplCode(rs.getString("tpl_code"));
                    vo.setTableId(rs.getLong("table_id"));
                    vo.setVersion(rs.getInt("version"));
                    vo.setPathPattern(rs.getString("path_pattern"));
                    vo.setOperator(rs.getString("operator"));
                    vo.setCreateTime(rs.getTimestamp("create_time") == null ? null
                            : rs.getTimestamp("create_time").toLocalDateTime());
                    return vo;
                }, id);
    }

    @Override
    public GenTemplateHistoryVO historyDetail(Long id, Integer version) {
        List<GenTemplateHistoryVO> rows = jdbcTemplate.query(
                "SELECT id, template_id, tpl_code, table_id, version, path_pattern, operator, "
                        + "create_time, content FROM gen_template_history "
                        + "WHERE template_id = ? AND version = ? ORDER BY id DESC LIMIT 1",
                (rs, i) -> {
                    GenTemplateHistoryVO vo = new GenTemplateHistoryVO();
                    vo.setId(rs.getLong("id"));
                    vo.setTemplateId(rs.getLong("template_id"));
                    vo.setTplCode(rs.getString("tpl_code"));
                    vo.setTableId(rs.getLong("table_id"));
                    vo.setVersion(rs.getInt("version"));
                    vo.setPathPattern(rs.getString("path_pattern"));
                    vo.setOperator(rs.getString("operator"));
                    vo.setCreateTime(rs.getTimestamp("create_time") == null ? null
                            : rs.getTimestamp("create_time").toLocalDateTime());
                    vo.setContent(rs.getString("content"));
                    return vo;
                }, id, version);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "该版本快照不存在：v" + version);
        }
        return rows.get(0);
    }

    @Override
    @Transactional
    public Integer rollback(Long id, Integer version) {
        GenTemplate exist = getById(id);
        if (exist == null) {
            throw new BusinessException(404, "模板不存在：" + id);
        }
        GenTemplateHistoryVO snap = historyDetail(id, version);
        int oldVersion = exist.getVersion() == null ? 1 : exist.getVersion();
        // 回滚前把当前内容也存一份，回滚本身也可再回滚
        snapshot(exist, oldVersion);
        GenTemplate patch = new GenTemplate();
        patch.setId(id);
        patch.setContent(snap.getContent());
        patch.setPathPattern(snap.getPathPattern());
        patch.setVersion(oldVersion + 1);
        patch.setRemark("回滚自 v" + version);
        updateById(patch);
        genTemplateEngine.evict();
        return oldVersion + 1;
    }

    /**
     * 写一条版本快照（同一版本重复保存时跳过，避免刷屏）
     */
    private void snapshot(GenTemplate tpl, int version) {
        if (tpl == null || tpl.getId() == null) {
            return;
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gen_template_history WHERE template_id = ? AND version = ?",
                Integer.class, tpl.getId(), version);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO gen_template_history(template_id, tpl_code, table_id, version, "
                        + "content, path_pattern, operator) VALUES(?,?,?,?,?,?,?)",
                tpl.getId(), tpl.getTplCode(), tpl.getTableId() == null ? 0L : tpl.getTableId(),
                version, tpl.getContent(), tpl.getPathPattern(), operator());
    }

    private String operator() {
        String name = UserContext.getUsername();
        if (StringUtils.hasText(name)) {
            return name;
        }
        Long userId = UserContext.getUserId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    @Override
    public void validate(String content, String tplCode) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "模板内容不能为空");
        }
        GenTemplateEngine.renderString(content, GenTemplateModel.sample());
    }

    private boolean existsActive(String tplCode, Long tableId) {
        LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTemplate::getTplCode, tplCode);
        wrapper.eq(GenTemplate::getTableId, tableId);
        return count(wrapper) > 0;
    }
}
