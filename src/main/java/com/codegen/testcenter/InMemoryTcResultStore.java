package com.codegen.testcenter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.TcRunQuery;
import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存存储：无 MySQL 时也能完整跑通测试中心（含页面与 API），以及支撑纯单测。
 * 进程重启后结果清空，因此只用于调试/单测，生产默认走 jdbc。
 */
@Component
@ConditionalOnProperty(name = "app.test-center.store", havingValue = "memory")
public class InMemoryTcResultStore implements TcResultStore {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong seq = new AtomicLong(0);
    private final Map<Long, TcRun> runs = new ConcurrentHashMap<>();
    private final Map<String, TcCaseResult> cases = new ConcurrentHashMap<>();
    private final Map<Long, TcArtifact> artifacts = new ConcurrentHashMap<>();

    @Override
    public TcRun createRun(TcRun run) {
        long id = seq.incrementAndGet();
        run.setId(id);
        if (run.getCreateTime() == null) {
            run.setCreateTime(LocalDateTime.now().withNano(0));
        }
        if (run.getDeleted() == null) {
            run.setDeleted(0);
        }
        runs.put(id, copy(run, new TcRun()));
        return run;
    }

    @Override
    public void updateRun(TcRun run) {
        if (run == null || run.getId() == null || !runs.containsKey(run.getId())) {
            return;
        }
        run.setUpdateTime(LocalDateTime.now().withNano(0));
        runs.put(run.getId(), copy(run, new TcRun()));
    }

    @Override
    public TcRun findRun(long id) {
        return copy(runs.get(id), new TcRun());
    }

    @Override
    public TcRun findRunByNo(String runNo) {
        return runs.values().stream()
                .filter(r -> runNo != null && runNo.equals(r.getRunNo()))
                .findFirst().map(r -> copy(r, new TcRun())).orElse(null);
    }

    @Override
    public Page<TcRun> pageRuns(TcRunQuery q) {
        int pageNum = q.getPageNum() == null ? 1 : Math.max(1, q.getPageNum());
        int pageSize = q.getPageSize() == null ? 10 : Math.max(1, q.getPageSize());
        LocalDateTime from = parse(q.getStartTime());
        LocalDateTime to = parse(q.getEndTime());
        List<TcRun> matched = runs.values().stream()
                .filter(r -> !StringUtils.hasText(q.getStatus()) || q.getStatus().equals(r.getStatus()))
                .filter(r -> !StringUtils.hasText(q.getTriggerType()) || q.getTriggerType().equals(r.getTriggerType()))
                .filter(r -> !StringUtils.hasText(q.getGitCommit()) || q.getGitCommit().equals(r.getGitCommit()))
                .filter(r -> !StringUtils.hasText(q.getKeyword())
                        || (r.getRunNo() != null && r.getRunNo().contains(q.getKeyword())))
                .filter(r -> from == null || r.getCreateTime() == null || !r.getCreateTime().isBefore(from))
                .filter(r -> to == null || r.getCreateTime() == null || !r.getCreateTime().isAfter(to))
                .sorted(Comparator.comparing(TcRun::getId).reversed())
                .map(r -> copy(r, new TcRun()))
                .collect(Collectors.toList());
        Page<TcRun> page = new Page<>(pageNum, pageSize);
        page.setTotal(matched.size());
        int fromIndex = Math.min((pageNum - 1) * pageSize, matched.size());
        int toIndex = Math.min(fromIndex + pageSize, matched.size());
        page.setRecords(new ArrayList<>(matched.subList(fromIndex, toIndex)));
        return page;
    }

    @Override
    public List<TcRun> runningRuns() {
        return runs.values().stream()
                .filter(r -> TcStatus.isRunning(r.getStatus()))
                .map(r -> copy(r, new TcRun()))
                .collect(Collectors.toList());
    }

    @Override
    public List<TcRun> recentRuns(int limit) {
        return runs.values().stream()
                .filter(r -> TcStatus.isTerminal(r.getStatus()))
                .sorted(Comparator.comparing(TcRun::getId).reversed())
                .limit(Math.max(1, limit))
                .map(r -> copy(r, new TcRun()))
                .collect(Collectors.toList());
    }

    @Override
    public TcCaseResult saveCase(TcCaseResult result) {
        String key = result.getRunId() + ":" + result.getSuiteId();
        TcCaseResult old = cases.get(key);
        long id = old == null ? seq.incrementAndGet() : old.getId();
        result.setId(id);
        if (result.getDeleted() == null) {
            result.setDeleted(0);
        }
        if (result.getCreateTime() == null) {
            result.setCreateTime(LocalDateTime.now().withNano(0));
        }
        cases.put(key, copy(result, new TcCaseResult()));
        return result;
    }

    @Override
    public TcCaseResult findCase(long id) {
        return cases.values().stream().filter(c -> id == c.getId())
                .findFirst().map(c -> copy(c, new TcCaseResult())).orElse(null);
    }

    @Override
    public List<TcCaseResult> casesOf(long runId) {
        return cases.values().stream().filter(c -> runId == c.getRunId())
                .sorted(Comparator.comparing(TcCaseResult::getId))
                .map(c -> copy(c, new TcCaseResult()))
                .collect(Collectors.toList());
    }

    @Override
    public TcArtifact saveArtifact(TcArtifact artifact) {
        long id = seq.incrementAndGet();
        artifact.setId(id);
        if (artifact.getDeleted() == null) {
            artifact.setDeleted(0);
        }
        if (artifact.getCreateTime() == null) {
            artifact.setCreateTime(LocalDateTime.now().withNano(0));
        }
        artifacts.put(id, copy(artifact, new TcArtifact()));
        return artifact;
    }

    @Override
    public TcArtifact findArtifact(long id) {
        return copy(artifacts.get(id), new TcArtifact());
    }

    @Override
    public List<TcArtifact> artifactsOf(long runId) {
        return artifacts.values().stream().filter(a -> runId == a.getRunId())
                .sorted(Comparator.comparing(TcArtifact::getId))
                .map(a -> copy(a, new TcArtifact()))
                .collect(Collectors.toList());
    }

    private <T> T copy(Object src, T dest) {
        if (src == null) {
            return null;
        }
        BeanUtils.copyProperties(src, dest);
        return dest;
    }

    private LocalDateTime parse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.length() == 10 ? text + " 00:00:00" : text, FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
