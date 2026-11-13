package com.codegen.testcenter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.TcRunQuery;
import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.codegen.mapper.TcArtifactMapper;
import com.codegen.mapper.TcCaseResultMapper;
import com.codegen.mapper.TcRunMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 默认存储：三张 tc_* 表落 MySQL。 */
@Component
@ConditionalOnProperty(name = "app.test-center.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcTcResultStore implements TcResultStore {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TcRunMapper runMapper;
    private final TcCaseResultMapper caseMapper;
    private final TcArtifactMapper artifactMapper;

    public JdbcTcResultStore(TcRunMapper runMapper, TcCaseResultMapper caseMapper,
                             TcArtifactMapper artifactMapper) {
        this.runMapper = runMapper;
        this.caseMapper = caseMapper;
        this.artifactMapper = artifactMapper;
    }

    @Override
    public TcRun createRun(TcRun run) {
        runMapper.insert(run);
        return run;
    }

    @Override
    public void updateRun(TcRun run) {
        runMapper.updateById(run);
    }

    @Override
    public TcRun findRun(long id) {
        return runMapper.selectById(id);
    }

    @Override
    public TcRun findRunByNo(String runNo) {
        return runMapper.selectOne(new LambdaQueryWrapper<TcRun>().eq(TcRun::getRunNo, runNo));
    }

    @Override
    public Page<TcRun> pageRuns(TcRunQuery q) {
        Page<TcRun> page = new Page<>(q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize());
        LambdaQueryWrapper<TcRun> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(q.getStatus()), TcRun::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getTriggerType()), TcRun::getTriggerType, q.getTriggerType())
                .eq(StringUtils.hasText(q.getGitCommit()), TcRun::getGitCommit, q.getGitCommit())
                .like(StringUtils.hasText(q.getKeyword()), TcRun::getRunNo, q.getKeyword())
                .ge(parse(q.getStartTime()) != null, TcRun::getCreateTime, parse(q.getStartTime()))
                .le(parse(q.getEndTime()) != null, TcRun::getCreateTime, parse(q.getEndTime()))
                .orderByDesc(TcRun::getId);
        return runMapper.selectPage(page, w);
    }

    @Override
    public List<TcRun> runningRuns() {
        return runMapper.selectList(new LambdaQueryWrapper<TcRun>()
                .in(TcRun::getStatus, List.of(TcStatus.QUEUED, TcStatus.RUNNING)));
    }

    @Override
    public List<TcRun> recentRuns(int limit) {
        int n = Math.max(1, Math.min(limit, 200));
        return runMapper.selectList(new LambdaQueryWrapper<TcRun>()
                .in(TcRun::getStatus, List.of(TcStatus.PASSED, TcStatus.FAILED,
                        TcStatus.CANCELLED, TcStatus.TIMEOUT))
                .orderByDesc(TcRun::getId)
                .last("LIMIT " + n));
    }

    @Override
    public TcCaseResult saveCase(TcCaseResult result) {
        TcCaseResult old = caseMapper.selectOne(new LambdaQueryWrapper<TcCaseResult>()
                .eq(TcCaseResult::getRunId, result.getRunId())
                .eq(TcCaseResult::getSuiteId, result.getSuiteId()));
        if (old == null) {
            caseMapper.insert(result);
            return result;
        }
        result.setId(old.getId());
        caseMapper.updateById(result);
        return result;
    }

    @Override
    public TcCaseResult findCase(long id) {
        return caseMapper.selectById(id);
    }

    @Override
    public List<TcCaseResult> casesOf(long runId) {
        return caseMapper.selectList(new LambdaQueryWrapper<TcCaseResult>()
                .eq(TcCaseResult::getRunId, runId)
                .orderByAsc(TcCaseResult::getId));
    }

    @Override
    public TcArtifact saveArtifact(TcArtifact artifact) {
        artifactMapper.insert(artifact);
        return artifact;
    }

    @Override
    public TcArtifact findArtifact(long id) {
        return artifactMapper.selectById(id);
    }

    @Override
    public List<TcArtifact> artifactsOf(long runId) {
        return artifactMapper.selectList(new LambdaQueryWrapper<TcArtifact>()
                .eq(TcArtifact::getRunId, runId)
                .orderByAsc(TcArtifact::getId));
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
