package com.codegen.service;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.dto.GenAiTemplatePlanDTO;
import com.codegen.dto.IndustryPackInstallDTO;
import com.codegen.entity.GenIndustryInstall;
import com.codegen.entity.GenIndustryParadigm;
import com.codegen.mapper.GenIndustryInstallMapper;
import com.codegen.mapper.GenIndustryParadigmMapper;
import com.codegen.support.IndustryTemplateRegistry;
import com.codegen.vo.GenAiBuildPlanVO;
import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.IndustryInstallVO;
import com.codegen.vo.IndustryParadigmVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * V9.22 行业范式模板市场 + 安装机制。
 *
 * <p>职责边界：
 * <ul>
 *   <li>范式元数据来自 gen_industry_paradigm，表结构来自 IndustryTemplateRegistry（V9.7/V9.19）；</li>
 *   <li>安装只调用 V9.7 已验证的 planFromTemplate 生成 plan，<b>不建库、不写 gen_table</b>；</li>
 *   <li>台账记录版本与模块，让 Step2 建库有据可依；重复安装同包同版本拒绝。</li>
 * </ul>
 */
@Slf4j
@Service
public class IndustryPackService {

    private final GenIndustryParadigmMapper paradigmMapper;
    private final GenIndustryInstallMapper installMapper;
    private final AiTableRecommendService aiTableRecommendService;
    private final IndustryTemplateRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IndustryPackService(GenIndustryParadigmMapper paradigmMapper,
                               GenIndustryInstallMapper installMapper,
                               AiTableRecommendService aiTableRecommendService,
                               IndustryTemplateRegistry registry) {
        this.paradigmMapper = paradigmMapper;
        this.installMapper = installMapper;
        this.aiTableRecommendService = aiTableRecommendService;
        this.registry = registry;
    }

    /** 范式包清单：元数据 + registry 实算模块/表数 */
    public List<IndustryParadigmVO> list(String industry, String keyword) {
        List<GenIndustryParadigm> rows = paradigmMapper.selectList(new LambdaQueryWrapper<GenIndustryParadigm>()
                .eq(GenIndustryParadigm::getEnabled, 1)
                .eq(industry != null && !industry.isBlank(), GenIndustryParadigm::getIndustry, industry)
                .orderByAsc(GenIndustryParadigm::getSortNo));
        List<IndustryParadigmVO> result = new ArrayList<>();
        for (GenIndustryParadigm row : rows) {
            IndustryParadigmVO vo = toVO(row);
            if (keyword != null && !keyword.isBlank()
                    && !contains(vo.getName(), keyword) && !contains(vo.getIndustry(), keyword)
                    && !contains(vo.getCoreChain(), keyword)) {
                continue;
            }
            result.add(vo);
        }
        return result;
    }

    /** 范式包详情：填充 registry 的模块与表清单 */
    public IndustryParadigmVO detail(String packKey) {
        GenIndustryParadigm row = requireParadigm(packKey);
        IndustryParadigmVO vo = toVO(row);
        GenAiTemplateVO template = registry.get(packKey);
        if (template != null) {
            vo.setModules(template.getModules());
        }
        return vo;
    }

    /**
     * 安装：生成 plan（复用 V9.7 克隆链路）+ 写台账，同一事务。
     * 不建库、不写 gen_table——保留 Step2 人工二次确认卡点。
     */
    @Transactional(rollbackFor = Exception.class)
    public IndustryInstallVO install(IndustryPackInstallDTO dto) {
        GenIndustryParadigm paradigm = requireParadigm(dto.getPackKey());
        if (dto.getProjectId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择目标项目");
        }
        if (dto.getModuleNames() == null || dto.getModuleNames().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请至少选择一个模块");
        }
        // 重复安装检测：同项目同包同版本只允许一次，重装需先移除台账
        Long exists = installMapper.selectCount(new LambdaQueryWrapper<GenIndustryInstall>()
                .eq(GenIndustryInstall::getProjectId, dto.getProjectId())
                .eq(GenIndustryInstall::getPackKey, dto.getPackKey())
                .eq(GenIndustryInstall::getStatus, "INSTALLED"));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "该项目已安装该范式包 v" + paradigm.getVersion() + "，请勿重复安装；如确需重装请先移除台账");
        }

        // 复用 V9.7 planFromTemplate（含去重/差集/关系视图收口），不重复实现克隆规则
        GenAiTemplatePlanDTO planDto = new GenAiTemplatePlanDTO();
        planDto.setTemplateKey(dto.getPackKey());
        planDto.setSystemName(dto.getSystemName() == null || dto.getSystemName().isBlank()
                ? paradigm.getIndustry() + "系统" : dto.getSystemName());
        planDto.setSystemDesc(dto.getSystemDesc());
        planDto.setModuleNames(dto.getModuleNames());
        planDto.setScope(dto.getScope() == null ? "new" : dto.getScope());
        planDto.setProjectId(dto.getProjectId());
        planDto.setScale(dto.getScale() == null ? "small" : dto.getScale());
        planDto.setDeploy(dto.getDeploy() == null ? "mono" : dto.getDeploy());
        GenAiBuildPlanVO plan = aiTableRecommendService.planFromTemplate(planDto);

        GenIndustryInstall record = new GenIndustryInstall();
        record.setProjectId(dto.getProjectId());
        record.setPackKey(dto.getPackKey());
        record.setPackVersion(paradigm.getVersion());
        record.setScope(planDto.getScope());
        record.setModuleNames(toJson(dto.getModuleNames()));
        record.setTableCount(plan.getTableCount());
        record.setPlanSummary(toJson(Map.of(
                "moduleCount", plan.getModuleCount(),
                "tableCount", plan.getTableCount(),
                "excludedCount", plan.getExcludedCount(),
                "source", plan.getSource() == null ? "template" : plan.getSource())));
        record.setStatus("INSTALLED");
        record.setInstallTime(java.time.LocalDateTime.now());
        installMapper.insert(record);

        IndustryInstallVO vo = new IndustryInstallVO();
        vo.setInstallId(String.valueOf(record.getId()));
        vo.setPackKey(dto.getPackKey());
        vo.setPackVersion(paradigm.getVersion());
        vo.setPlanSource(plan.getSource() == null ? "template" : plan.getSource());
        vo.setModuleCount(plan.getModuleCount());
        vo.setTableCount(plan.getTableCount());
        vo.setExcludedCount(plan.getExcludedCount());
        vo.setDdlReady(false);
        log.info("V9.22 范式包安装成功：project={} pack={} v{} 模块={} 表={}（未建库）",
                dto.getProjectId(), dto.getPackKey(), paradigm.getVersion(),
                plan.getModuleCount(), plan.getTableCount());
        return vo;
    }

    /** 项目已装包列表 */
    public List<GenIndustryInstall> installed(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "projectId不能为空");
        }
        return installMapper.selectList(new LambdaQueryWrapper<GenIndustryInstall>()
                .eq(GenIndustryInstall::getProjectId, projectId)
                .eq(GenIndustryInstall::getStatus, "INSTALLED")
                .orderByDesc(GenIndustryInstall::getInstallTime));
    }

    /** 移除台账（逻辑删除状态置 REMOVED），不删 gen_table */
    public void remove(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "安装记录ID不能为空");
        }
        GenIndustryInstall record = installMapper.selectById(id);
        if (record == null || "REMOVED".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "安装记录不存在或已移除：" + id);
        }
        record.setStatus("REMOVED");
        installMapper.updateById(record);
        log.info("V9.22 已移除安装台账：id={} pack={}（未删除已生成的表）", id, record.getPackKey());
    }

    private GenIndustryParadigm requireParadigm(String packKey) {
        if (packKey == null || packKey.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "范式包key不能为空");
        }
        GenIndustryParadigm row = paradigmMapper.selectOne(new LambdaQueryWrapper<GenIndustryParadigm>()
                .eq(GenIndustryParadigm::getPackKey, packKey.trim())
                .eq(GenIndustryParadigm::getEnabled, 1));
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "范式包不存在：" + packKey);
        }
        return row;
    }

    private IndustryParadigmVO toVO(GenIndustryParadigm row) {
        IndustryParadigmVO vo = new IndustryParadigmVO();
        BeanUtils.copyProperties(row, vo);
        GenAiTemplateVO template = registry.get(row.getPackKey());
        if (template != null) {
            vo.setModuleCount(template.getModuleCount());
            vo.setTableCount(template.getTableCount());
            vo.setTemplateAvailable(true);
            if (vo.getName() == null || vo.getName().isBlank()) {
                vo.setName(template.getName());
            }
        } else {
            vo.setModuleCount(0);
            vo.setTableCount(row.getDirectTables());
            vo.setTemplateAvailable(false);
        }
        return vo;
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "JSON序列化失败：" + e.getMessage());
        }
    }
}
