package com.codegen.testcenter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.TcRunQuery;
import com.codegen.dto.TcRunRequest;
import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.codegen.vo.TcRunDetailVO;
import com.codegen.vo.TcSuiteVO;
import com.codegen.vo.TcTrendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试中心门面：注册表视图、触发、查询、日志、取消、重跑、报告、趋势、产物。
 * Controller 只做参数与 HTTP 语义，业务判定都在这里，便于脱离 Web 层做单测。
 */
@Service
public class TestCenterService {

    private static final Logger log = LoggerFactory.getLogger(TestCenterService.class);

    private final TestCenterExecutor executor;
    private final TcResultStore store;
    private final TcProperties props;
    private final TcReportRenderer renderer;
    private final ObjectMapper mapper;

    public TestCenterService(TestCenterExecutor executor, TcResultStore store, TcProperties props,
                             TcReportRenderer renderer, ObjectMapper mapper) {
        this.executor = executor;
        this.store = store;
        this.props = props;
        this.renderer = renderer;
        this.mapper = mapper;
    }

    /** 服务启动兜底：进程被杀残留的 RUNNING 不能永远转圈（US-5）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            int n = executor.recoverStaleRuns();
            if (n > 0) {
                log.warn("测试中心：收敛 {} 条残留运行为 TIMEOUT", n);
            }
        } catch (Exception e) {
            // tc_* 表尚未初始化时不能把整个服务启动拖垮
            log.warn("测试中心：残留运行收敛跳过（{}）", e.getMessage());
        }
    }

    public List<TcSuiteVO> suites() {
        Map<String, TcCaseResult> last = new LinkedHashMap<>();
        List<TcRun> recent = store.recentRuns(1);
        if (!recent.isEmpty()) {
            for (TcCaseResult c : store.casesOf(recent.get(0).getId())) {
                last.put(c.getSuiteId(), c);
            }
        }
        List<TcSuiteVO> out = new ArrayList<>();
        for (TcSuite s : TcSuite.all()) {
            TcSuiteVO vo = new TcSuiteVO();
            vo.setId(s.getId());
            vo.setName(s.getName());
            vo.setBaseline(s.getBaseline());
            vo.setLevel(s.getLevel());
            vo.setTimeoutMinutes(s.getTimeoutMinutes());
            vo.setDefaultSelected(s.isDefaultSelected());
            vo.setDependsOn(s.getDependsOn());
            vo.setEntryHint(s.getEntryHint());
            vo.setRequires(s.getRequires());
            TcCaseResult c = last.get(s.getId());
            vo.setLastStatus(c == null ? null : c.getStatus());
            vo.setLastDurationMs(c == null ? null : c.getDurationMs());
            out.add(vo);
        }
        return out;
    }

    public TcRun trigger(TcRunRequest request) {
        if (request == null) {
            throw TcException.badRequest("请求体不能为空");
        }
        String forbidden = request.firstForbiddenKey();
        if (forbidden != null) {
            // 由 Controller 的 @Log 记入 sys_oper_log，保留攻击/误用痕迹
            throw TcException.forbidden("请求体含禁止字段: " + forbidden + "（命令只能来自服务端注册表）");
        }
        List<String> ids = request.getSuiteIds();
        if (ids == null || ids.isEmpty()) {
            ids = TcSuite.defaultSuiteIds(); // 空 body = 一键 P0 全量
        }
        return executor.submit(ids, request.getTrigger(), request.getGitCommit());
    }

    /**
     * V9.3：生成完成自动验证产物（S10/S11/S12）。由 GenCodeServiceImpl 在写盘后 best-effort 调用：
     * 测试中心停用或已有运行进行中（409）时由调用方 try/catch 忽略，不影响代码写盘结果。
     *
     * @param genRoot 生成产物后端根目录（SysProject.backendRoot + backendPath）
     */
    public TcRun triggerGen(String genRoot) {
        // 开关关闭则不开自动验证（避免日常生成产生测试运行记录）
        if (!props.isAutoVerifyGen()) {
            return null;
        }
        return executor.submit(TcSuite.genProductSuiteIds(), "GEN", null, genRoot);
    }

    public Page<TcRun> runs(TcRunQuery query) {
        return store.pageRuns(query == null ? new TcRunQuery() : query);
    }

    public TcRunDetailVO detail(long runId) {
        TcRun run = requireRun(runId);
        TcRunDetailVO vo = new TcRunDetailVO();
        vo.setRun(run);
        vo.setCases(store.casesOf(runId));
        vo.setArtifacts(store.artifactsOf(runId));
        try {
            vo.setSummary(run.getSummaryJson() == null ? null
                    : mapper.readValue(run.getSummaryJson(), TcSummary.class));
        } catch (Exception e) {
            vo.setSummary(Map.of("parseError", String.valueOf(e.getMessage())));
        }
        return vo;
    }

    /** 日志尾部轮询；不引 WebSocket。caseId 为空时返回该 run 全部用例日志的拼接。 */
    public String log(long runId, Long caseId, int tailLines) {
        TcRun run = requireRun(runId);
        int tail = tailLines <= 0 ? props.getTailLines() : Math.min(tailLines, 2000);
        List<TcCaseResult> targets = new ArrayList<>();
        if (caseId != null) {
            TcCaseResult c = store.findCase(caseId);
            if (c == null || c.getRunId() == null || c.getRunId() != runId) {
                throw TcException.badRequest("用例不属于该运行: " + caseId);
            }
            targets.add(c);
        } else {
            targets.addAll(store.casesOf(runId));
        }
        StringBuilder sb = new StringBuilder();
        for (TcCaseResult c : targets) {
            sb.append("===== ").append(c.getSuiteId()).append(" [").append(c.getStatus())
                    .append("] =====\n").append(logText(run, c, tail)).append('\n');
        }
        return sb.toString();
    }

    public String logText(TcRun run, TcCaseResult c, int tail) {
        if (c == null || c.getLogPath() == null) {
            return "";
        }
        try {
            Path abs = Paths.get(c.getLogPath());
            if (!abs.isAbsolute()) {
                abs = props.runsRoot().resolve(c.getLogPath());
            }
            Path safe = TcPathGuard.resolve(props.workDirOf(run.getRunNo()), abs.toString());
            return TcMetrics.tail(Files.readString(safe, StandardCharsets.UTF_8), tail);
        } catch (Exception e) {
            return "(日志暂不可用: " + e.getMessage() + ")";
        }
    }

    public boolean cancel(long runId) {
        return executor.cancel(runId);
    }

    /** 单个失败套件重跑：生成新 run，trigger=RERUN，沿用原 commit。 */
    public TcRun rerun(long caseId) {
        TcCaseResult c = store.findCase(caseId);
        if (c == null) {
            throw TcException.notFound("用例不存在: " + caseId);
        }
        TcRun origin = requireRun(c.getRunId());
        return executor.submit(List.of(c.getSuiteId()), "RERUN", origin.getGitCommit());
    }

    /** 仅失败项重跑：一次触发多个失败套件。 */
    public TcRun rerunFailed(long runId) {
        TcRun origin = requireRun(runId);
        List<String> ids = TestCenterExecutor.failedSuiteIds(store.casesOf(runId));
        if (ids.isEmpty()) {
            throw TcException.badRequest("该运行没有失败用例，无需重跑");
        }
        return executor.submit(ids, "RERUN", origin.getGitCommit());
    }

    public String report(long runId, String format) {
        TcRunDetailVO detail = detail(runId);
        if ("html".equalsIgnoreCase(format)) {
            return renderer.render(detail, c -> logText(detail.getRun(), c, 40));
        }
        String json = detail.getRun().getSummaryJson();
        return json == null || json.isBlank() ? "{}" : json;
    }

    public TcArtifactFile artifact(long artifactId) {
        TcArtifact a = store.findArtifact(artifactId);
        if (a == null) {
            throw TcException.notFound("产物不存在: " + artifactId);
        }
        TcRun run = requireRun(a.getRunId());
        try {
            Path stored = Paths.get(a.getFilePath());
            if (!stored.isAbsolute()) {
                stored = props.runsRoot().resolve(a.getFilePath());
            }
            Path safe = TcPathGuard.resolve(props.workDirOf(run.getRunNo()), stored.toString());
            return new TcArtifactFile(a.getFileName(),
                    a.getMimeType() == null ? "application/octet-stream" : a.getMimeType(),
                    Files.readAllBytes(safe));
        } catch (SecurityException e) {
            throw TcException.forbidden(e.getMessage());
        } catch (Exception e) {
            throw TcException.notFound("产物读取失败: " + e.getMessage());
        }
    }

    public List<TcTrendVO> trends(int limit) {
        List<TcRun> recent = store.recentRuns(Math.max(1, Math.min(limit, 100)));
        List<TcTrendVO> out = new ArrayList<>();
        // recentRuns 是倒序，趋势图按时间正序更好读
        for (int i = recent.size() - 1; i >= 0; i--) {
            TcRun r = recent.get(i);
            TcTrendVO vo = new TcTrendVO();
            vo.setRunId(r.getId());
            vo.setRunNo(r.getRunNo());
            vo.setStatus(r.getStatus());
            int total = r.getTotal() == null ? 0 : r.getTotal();
            int passed = r.getPassed() == null ? 0 : r.getPassed();
            vo.setPassRate(total == 0 ? 0 : Math.round(passed * 10000.0 / total) / 100.0);
            vo.setDurationMs(r.getDurationMs());
            vo.setCreateTime(r.getCreateTime());
            vo.setGitCommit(r.getGitCommit());
            out.add(vo);
        }
        return out;
    }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<TcRun> recent = store.recentRuns(1);
        TcRun last = recent.isEmpty() ? null : recent.get(0);
        out.put("lastRun", last);
        out.put("busy", executor.isBusy());
        // 雪花 ID 是 19 位，JSON number 在 JS 里会被截断（BaseEntity 用 ToStringSerializer
        // 就是为这个坑）。前端拿它拼 /tc/runs/{id}/cancel，必须下发字符串。
        long active = executor.getActiveRunId();
        out.put("activeRunId", active < 0 ? null : String.valueOf(active));
        if (last != null) {
            int total = last.getTotal() == null ? 0 : last.getTotal();
            int passed = last.getPassed() == null ? 0 : last.getPassed();
            out.put("passRate", total == 0 ? 0 : Math.round(passed * 10000.0 / total) / 100.0);
            out.put("failed", last.getFailed());
            out.put("durationMs", last.getDurationMs());
            out.put("gitCommit", last.getGitCommit());
        }
        return out;
    }

    public TcRun requireRun(long runId) {
        TcRun run = store.findRun(runId);
        if (run == null) {
            throw TcException.notFound("运行不存在: " + runId);
        }
        return run;
    }

    /** 产物下载结果（文件名 / MIME / 字节） */
    public record TcArtifactFile(String fileName, String mimeType, byte[] bytes) {
    }
}
