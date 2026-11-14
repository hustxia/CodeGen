package com.codegen.testcenter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.TcRunQuery;
import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;

import java.util.List;

/**
 * 结果存储抽象。
 *
 * <p>默认实现走 MySQL（MyBatis-Plus），另有 memory 实现用于无 DB 环境与纯单测。
 * 抽接口不只是为了测试：执行器/服务层不该知道 SQL 长什么样，
 * 才能在不启动 Spring、不连数据库的前提下把状态机与契约完整验证一遍。
 */
public interface TcResultStore {

    TcRun createRun(TcRun run);

    void updateRun(TcRun run);

    TcRun findRun(long id);

    TcRun findRunByNo(String runNo);

    Page<TcRun> pageRuns(TcRunQuery query);

    /** 服务重启兜底用：找出残留的 QUEUED/RUNNING */
    List<TcRun> runningRuns();

    /** 最近 N 次已结束的运行，按创建时间倒序 */
    List<TcRun> recentRuns(int limit);

    /** 按 (runId, suiteId) upsert，避免重跑时插出重复行 */
    TcCaseResult saveCase(TcCaseResult result);

    TcCaseResult findCase(long id);

    List<TcCaseResult> casesOf(long runId);

    TcArtifact saveArtifact(TcArtifact artifact);

    TcArtifact findArtifact(long id);

    List<TcArtifact> artifactsOf(long runId);
}
