package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.entity.GenQualityIssue;
import com.codegen.entity.GenQualityReport;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenQualityIssueMapper;
import com.codegen.mapper.GenQualityReportMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.quality.DeterministicQualityScanner;
import com.codegen.quality.QualityRule;
import com.codegen.quality.QualityRuleCatalog;
import com.codegen.service.GenCodeService;
import com.codegen.service.QualityGateService;
import com.codegen.service.QualityLlmClient;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenQualityGateVO;
import com.codegen.vo.GenQualityIssueVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 质量门禁实现（M2.2）。
 *
 * <p>执行顺序：
 * <ol>
 *   <li>确定性引擎逐行扫描（总是执行）；</li>
 *   <li>LLM 语义引擎（配置可用时执行，失败/超时/解析失败自动降级，不阻断）；</li>
 *   <li>合并去重、按 BLOCKER 优先排序、落报告与行级明细。</li>
 * </ol>
 * 是否阻断由调用方根据 {@code blocked} 决定；本服务只给结论。
 */
@Slf4j
@Service
public class QualityGateServiceImpl implements QualityGateService {

    @Autowired
    private GenCodeService genCodeService;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenQualityReportMapper reportMapper;

    @Autowired
    private GenQualityIssueMapper issueMapper;

    @Autowired
    private DeterministicQualityScanner deterministicScanner;

    @Autowired
    private QualityLlmClient llmClient;

    @Override
    public GenQualityGateVO run(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要生成的表");
        }
        long started = System.currentTimeMillis();
        GenDownloadDTO dto = new GenDownloadDTO();
        dto.setIds(ids);
        List<GenFileVO> files = genCodeService.preview(dto);

        List<GenQualityIssueVO> issues = new ArrayList<>(deterministicScanner.scan(files));
        String llmState = GenQualityGateVO.LLM_DISABLED;
        String degradedReason = "AI 未启用或未配置，仅执行确定性引擎";
        String engine = "DET";
        if (llmClient.available()) {
            try {
                List<QualityLlmClient.LlmIssue> raw = llmClient.analyze(files, QualityRuleCatalog.llmRules());
                issues.addAll(mergeLlmIssues(raw, files));
                llmState = GenQualityGateVO.LLM_APPLIED;
                degradedReason = null;
                engine = "DET+LLM";
            } catch (Exception e) {
                llmState = GenQualityGateVO.LLM_DEGRADED;
                degradedReason = "LLM 引擎不可用，已回落确定性引擎：" + abbreviate(e.getMessage(), 300);
                log.warn("质量门禁 LLM 引擎降级: {}", e.getMessage());
            }
        }

        List<GenQualityIssueVO> merged = dedupe(issues);
        merged.sort(Comparator
                .comparing((GenQualityIssueVO i) -> "BLOCKER".equals(i.getSeverity()) ? 0 : 1)
                .thenComparing(i -> i.getFile() == null ? "" : i.getFile())
                .thenComparing(i -> i.getLineNo() == null ? 0L : i.getLineNo()));

        int blockers = (int) merged.stream().filter(i -> "BLOCKER".equals(i.getSeverity())).count();
        int warns = (int) merged.stream().filter(i -> "WARN".equals(i.getSeverity())).count();

        GenQualityGateVO vo = new GenQualityGateVO();
        vo.setProjectId(projectIdOf(ids));
        vo.setStatus(blockers > 0 ? GenQualityGateVO.STATUS_FAIL : GenQualityGateVO.STATUS_PASS);
        vo.setBlocked(blockers > 0);
        vo.setEngine(engine);
        vo.setLlmState(llmState);
        vo.setDegradedReason(degradedReason);
        vo.setBlockerCount(blockers);
        vo.setWarnCount(warns);
        vo.setIssueCount(merged.size());
        vo.setFileCount(files.size());
        vo.setRuleCount(QualityRuleCatalog.all().size());
        vo.setLlmRuleCount(QualityRuleCatalog.llmRules().size());
        vo.setIssues(merged);
        vo.setElapsedMs(System.currentTimeMillis() - started);
        vo.setMessage(String.format("52 条规则（%s）：BLOCKER %d / WARN %d，扫描 %d 个文件，耗时 %dms",
                engine, blockers, warns, files.size(), vo.getElapsedMs()));

        persist(vo, ids);
        if (vo.getReportId() != null) {
            vo.setMessage(vo.getMessage() + "；报告 #" + vo.getReportId());
        }
        return vo;
    }

    @Override
    public GenQualityGateVO reportDetail(Long reportId) {
        GenQualityReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(400, "质量门禁报告不存在: " + reportId);
        }
        List<GenQualityIssue> items = issueMapper.selectList(new LambdaQueryWrapper<GenQualityIssue>()
                .eq(GenQualityIssue::getReportId, reportId));
        items.sort(Comparator
                .comparing((GenQualityIssue i) -> "BLOCKER".equals(i.getSeverity()) ? 0 : 1)
                .thenComparing(i -> i.getFilePath() == null ? "" : i.getFilePath())
                .thenComparing(i -> i.getLineNo() == null ? 0L : i.getLineNo()));
        GenQualityGateVO vo = new GenQualityGateVO();
        vo.setReportId(report.getId());
        vo.setProjectId(report.getProjectId());
        vo.setStatus(report.getStatus());
        vo.setBlocked(Boolean.TRUE.equals(report.getBlocked()));
        vo.setEngine(report.getEngine());
        vo.setLlmState(report.getLlmState());
        vo.setDegradedReason(report.getDegradedReason());
        vo.setBlockerCount(n(report.getBlockerCount()));
        vo.setWarnCount(n(report.getWarnCount()));
        vo.setIssueCount(n(report.getIssueCount()));
        vo.setFileCount(n(report.getFileCount()));
        vo.setRuleCount(n(report.getRuleCount()));
        vo.setElapsedMs(report.getElapsedMs());
        vo.setMessage(report.getMessage());
        items.forEach(i -> vo.getIssues().add(toVO(i)));
        return vo;
    }

    // ---------- LLM 结果合并 ----------

    private List<GenQualityIssueVO> mergeLlmIssues(List<QualityLlmClient.LlmIssue> raw, List<GenFileVO> files) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (QualityLlmClient.LlmIssue item : raw) {
            QualityRule rule = QualityRuleCatalog.byId(item.ruleId());
            if (rule == null || rule.engine() != QualityRule.Engine.LLM) {
                continue;
            }
            String path = normalizePath(item.file(), files);
            if (path == null) {
                continue;
            }
            String content = contentOf(path, files);
            long line = locateLine(content, item.badSnippet(), item.line() == null ? 0L : item.line());
            GenQualityIssueVO vo = new GenQualityIssueVO();
            vo.setRuleId(rule.id());
            vo.setTitle(rule.title());
            vo.setCategory(rule.category().name());
            vo.setEngine(QualityRule.Engine.LLM.name());
            // LLM 只能维持或升级级别，不能把规则库的 BLOCKER 降成 WARN
            vo.setSeverity(moreSevere(rule.severity(), item.severity()));
            vo.setFile(path);
            vo.setLineNo(line);
            vo.setMessage(StringUtils.hasText(item.message()) ? item.message() : rule.message());
            vo.setBadSnippet(abbreviate(StringUtils.hasText(item.badSnippet())
                    ? item.badSnippet() : rule.badSnippet(), 300));
            vo.setFixSnippet(abbreviate(StringUtils.hasText(item.fixSnippet())
                    ? item.fixSnippet() : rule.goodSnippet(), 300));
            vo.setSourceRef(rule.sourceRef());
            out.add(vo);
        }
        return out;
    }

    private String moreSevere(QualityRule.Severity ruleSeverity, String reported) {
        if (ruleSeverity == QualityRule.Severity.BLOCKER) {
            return "BLOCKER";
        }
        return "BLOCKER".equalsIgnoreCase(reported) ? "BLOCKER" : "WARN";
    }

    private String normalizePath(String reported, List<GenFileVO> files) {
        if (!StringUtils.hasText(reported)) {
            return null;
        }
        String target = reported.replace('\\', '/');
        for (GenFileVO file : files) {
            String p = file.getPath() == null ? "" : file.getPath().replace('\\', '/');
            if (p.equals(target) || p.endsWith("/" + target) || target.endsWith("/" + p)) {
                return file.getPath();
            }
        }
        return null;
    }

    private String contentOf(String path, List<GenFileVO> files) {
        for (GenFileVO file : files) {
            if (path.equals(file.getPath())) {
                return file.getContent() == null ? "" : file.getContent();
            }
        }
        return "";
    }

    /**
     * 优先用 badSnippet 在原文中二次定位行号，LLM 报的行号只作兜底。
     */
    private long locateLine(String content, String snippet, long fallback) {
        if (content == null || content.isEmpty() || !StringUtils.hasText(snippet)) {
            return Math.max(fallback, 0L);
        }
        String needle = snippet.trim();
        int idx = content.indexOf(needle);
        if (idx < 0) {
            int newline = needle.indexOf('\n');
            if (newline > 0) {
                idx = content.indexOf(needle.substring(0, newline).trim());
            }
        }
        if (idx < 0) {
            return Math.max(fallback, 0L);
        }
        long line = 1;
        for (int i = 0; i < idx; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    // ---------- 持久化 ----------

    private void persist(GenQualityGateVO vo, List<Long> ids) {
        try {
            GenQualityReport report = new GenQualityReport();
            report.setProjectId(vo.getProjectId());
            report.setTableIds(abbreviate(join(ids), 1000));
            report.setEngine(vo.getEngine());
            report.setLlmState(vo.getLlmState());
            report.setStatus(vo.getStatus());
            report.setBlocked(vo.isBlocked());
            report.setBlockerCount(vo.getBlockerCount());
            report.setWarnCount(vo.getWarnCount());
            report.setIssueCount(vo.getIssueCount());
            report.setFileCount(vo.getFileCount());
            report.setRuleCount(vo.getRuleCount());
            report.setDegraded(GenQualityGateVO.LLM_DEGRADED.equals(vo.getLlmState()));
            report.setDegradedReason(abbreviate(vo.getDegradedReason(), 500));
            report.setMessage(abbreviate(vo.getMessage(), 500));
            report.setElapsedMs(vo.getElapsedMs());
            reportMapper.insert(report);
            vo.setReportId(report.getId());
            for (GenQualityIssueVO item : vo.getIssues()) {
                GenQualityIssue entity = new GenQualityIssue();
                entity.setReportId(report.getId());
                entity.setRuleId(item.getRuleId());
                entity.setCategory(item.getCategory());
                entity.setEngine(item.getEngine());
                entity.setSeverity(item.getSeverity());
                entity.setFilePath(abbreviate(item.getFile(), 500));
                entity.setLineNo(item.getLineNo() == null ? 0L : item.getLineNo());
                entity.setMessage(abbreviate(item.getMessage(), 1000));
                entity.setBadSnippet(item.getBadSnippet());
                entity.setFixSnippet(item.getFixSnippet());
                entity.setSourceRef(abbreviate(item.getSourceRef(), 300));
                issueMapper.insert(entity);
                item.setId(entity.getId());
            }
        } catch (Exception e) {
            // 报告表未迁移时门禁仍要能工作：降级为「不落库」，结果照常返回
            log.warn("质量门禁报告落库失败（不影响门禁结论）: {}", e.getMessage());
        }
    }

    private GenQualityIssueVO toVO(GenQualityIssue entity) {
        GenQualityIssueVO vo = new GenQualityIssueVO();
        vo.setId(entity.getId());
        vo.setRuleId(entity.getRuleId());
        QualityRule rule = QualityRuleCatalog.byId(entity.getRuleId());
        vo.setTitle(rule == null ? null : rule.title());
        vo.setCategory(entity.getCategory());
        vo.setEngine(entity.getEngine());
        vo.setSeverity(entity.getSeverity());
        vo.setFile(entity.getFilePath());
        vo.setLineNo(entity.getLineNo());
        vo.setMessage(entity.getMessage());
        vo.setBadSnippet(entity.getBadSnippet());
        vo.setFixSnippet(entity.getFixSnippet());
        vo.setSourceRef(entity.getSourceRef());
        return vo;
    }

    // ---------- 工具 ----------

    private List<GenQualityIssueVO> dedupe(List<GenQualityIssueVO> issues) {
        Set<String> seen = new LinkedHashSet<>();
        List<GenQualityIssueVO> out = new ArrayList<>();
        for (GenQualityIssueVO issue : issues) {
            String key = issue.getRuleId() + "|" + issue.getFile() + "|" + issue.getLineNo();
            if (seen.add(key)) {
                out.add(issue);
            }
        }
        return out;
    }

    private Long projectIdOf(List<Long> ids) {
        try {
            List<GenTable> tables = genTableMapper.selectBatchIds(ids);
            return tables.stream().map(GenTable::getProjectId).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String join(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private int n(Integer v) {
        return v == null ? 0 : v;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
