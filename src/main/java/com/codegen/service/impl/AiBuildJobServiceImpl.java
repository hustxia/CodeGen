package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.AiBuildJobProperties;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenAiBuildPlanDTO;
import com.codegen.entity.GenAiBuildJob;
import com.codegen.entity.GenAiBuildJobBatch;
import com.codegen.mapper.GenAiBuildJobBatchMapper;
import com.codegen.mapper.GenAiBuildJobMapper;
import com.codegen.service.AiBuildJobService;
import com.codegen.service.AiModelService;
import com.codegen.vo.GenAiBuildJobVO;
import com.codegen.vo.GenAiBuildPlanVO;
import com.codegen.vo.GenPipelineAiModulesVO;
import com.codegen.vo.GenPipelineAiVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V9.6 分批建模任务实现。
 *
 * <p>流程：提交（落 job + 1 个 split 批次）→ 调度器认领 → split 调模型拆模块并生成 N 个 module 批次
 * → 多 worker 并发逐模块建模 → 全部批次结束收口成 {@link GenAiBuildPlanVO}。
 *
 * <p>并发不直接开 {@code parallelStream}：批次必须能「落库 + 重试 + 重启续跑」，
 * 所以每次执行前先用带条件的 UPDATE 认领（pending→running），抢到的才跑。
 */
@Slf4j
@Service
public class AiBuildJobServiceImpl implements AiBuildJobService {

    private static final DateTimeFormatter JOB_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private AiModelService aiModelService;
    @Autowired
    private AiTableRecommendServiceImpl recommendService;
    @Autowired
    private GenAiBuildJobMapper jobMapper;
    @Autowired
    private GenAiBuildJobBatchMapper batchMapper;
    @Autowired
    private AiBuildJobProperties props;
    @Autowired
    private GenPipelineProperties pipelineProps;
    @Autowired
    private ObjectMapper objectMapper;

    private ExecutorService pool;

    @PostConstruct
    void init() {
        int threads = Math.max(1, props.getWorkerThreads());
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ai-build-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(threads, tf);
        log.info("V9.6 AI 建表分批任务：worker={} interval={}ms timeout={}s maxRetry={}",
                threads, props.getIntervalMs(), props.getBatchTimeoutSeconds(), props.getMaxRetry());
    }

    @PreDestroy
    void shutdown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    // ---------------- 对外 ----------------

    @Override
    public GenAiBuildJobVO submitPlan(GenAiBuildPlanDTO dto) {
        if (!pipelineProps.getAi().isEnabled()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建模未启用，请先在配置中开启 LLM");
        }
        if (!StringUtils.hasText(dto.getSystemName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "系统名称不能为空");
        }
        boolean supplement = "supplement".equalsIgnoreCase(
                dto.getScope() == null ? "" : dto.getScope().trim());
        Set<String> existing = new LinkedHashSet<>();
        if (supplement) {
            if (dto.getProjectId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "补表必须选择目标系统（projectId），否则无法判断哪些表已存在");
            }
            existing.addAll(recommendService.loadExistingTableNames(dto.getProjectId()));
        }

        Payload payload = new Payload();
        payload.setDto(dto);
        payload.setExisting(new ArrayList<>(existing));
        // 已有表约束只下沉到逐模块建模的 prompt；拆模块仍用原始 desc（否则会按已建模块重复拆）
        payload.setDescForAi(AiTableRecommendServiceImpl.withExistingHint(dto.getSystemDesc(), existing));

        GenAiBuildJob job = new GenAiBuildJob();
        job.setJobNo("AIJ" + LocalDateTime.now().format(JOB_NO_FMT) + (int) (Math.random() * 900 + 100));
        job.setJobType("plan");
        job.setStatus("pending");
        job.setSystemName(dto.getSystemName());
        job.setSystemDesc(trim(dto.getSystemDesc(), 990));
        job.setScope(dto.getScope());
        job.setProjectId(dto.getProjectId());
        job.setScale(dto.getScale());
        job.setDeploy(dto.getDeploy());
        job.setTotalBatch(1);
        job.setDoneBatch(0);
        job.setFailedBatch(0);
        job.setTotalTable(0);
        job.setPayload(toJson(payload));
        jobMapper.insert(job);

        GenAiBuildJobBatch split = newBatch(job.getId(), 0, "split", null);
        batchMapper.insert(split);

        log.info("V9.6 提交建模任务 jobId={} no={} scale={} 已有表={}",
                job.getId(), job.getJobNo(), dto.getScale(), existing.size());
        return status(job.getId());
    }

    @Override
    public GenAiBuildJobVO status(Long jobId) {
        GenAiBuildJob job = mustGet(jobId);
        List<GenAiBuildJobBatch> batches = batchMapper.selectList(new QueryWrapper<GenAiBuildJobBatch>()
                .eq("job_id", jobId).orderByAsc("seq"));

        GenAiBuildJobVO vo = new GenAiBuildJobVO();
        vo.setJobId(job.getId());
        vo.setJobNo(job.getJobNo());
        vo.setStatus(job.getStatus());
        vo.setSystemName(job.getSystemName());
        vo.setScale(job.getScale());
        vo.setDeploy(job.getDeploy());
        vo.setTotalBatch(nz(job.getTotalBatch()));
        vo.setDoneBatch(nz(job.getDoneBatch()));
        vo.setFailedBatch(nz(job.getFailedBatch()));
        vo.setTotalTable(nz(job.getTotalTable()));
        vo.setError(job.getError());
        vo.setResultReady(StringUtils.hasText(job.getResult()));

        int running = 0;
        for (GenAiBuildJobBatch b : batches) {
            GenAiBuildJobVO.Batch bv = new GenAiBuildJobVO.Batch();
            bv.setId(b.getId());
            bv.setSeq(b.getSeq());
            bv.setKind(b.getKind());
            bv.setModuleName(b.getModuleName());
            bv.setStatus(b.getStatus());
            bv.setTableCount(nz(b.getTableCount()));
            bv.setRetryCount(nz(b.getRetryCount()));
            bv.setError(b.getError());
            vo.getBatches().add(bv);
            if ("running".equals(b.getStatus())) {
                running++;
            }
        }
        vo.setRunningBatch(running);
        int total = nz(job.getTotalBatch());
        int finished = nz(job.getDoneBatch()) + nz(job.getFailedBatch());
        vo.setProgress(total <= 0 ? 0 : Math.min(100, finished * 100 / total));
        return vo;
    }

    @Override
    public GenAiBuildPlanVO result(Long jobId) {
        GenAiBuildJob job = mustGet(jobId);
        if (!StringUtils.hasText(job.getResult())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "任务尚未完成（当前状态：" + job.getStatus() + "），请稍后重试");
        }
        try {
            return objectMapper.readValue(job.getResult(), GenAiBuildPlanVO.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "任务结果解析失败：" + e.getMessage());
        }
    }

    @Override
    public void cancel(Long jobId) {
        GenAiBuildJob job = mustGet(jobId);
        if ("success".equals(job.getStatus()) || "failed".equals(job.getStatus())) {
            return;
        }
        jobMapper.update(null, new UpdateWrapper<GenAiBuildJob>().eq("id", jobId)
                .set("status", "canceled").set("update_time", LocalDateTime.now()));
        batchMapper.update(null, new UpdateWrapper<GenAiBuildJobBatch>()
                .eq("job_id", jobId).eq("status", "pending")
                .set("status", "skipped").set("error", "任务已取消")
                .set("finished_at", LocalDateTime.now()).set("update_time", LocalDateTime.now()));
        log.info("V9.6 任务已取消 jobId={}", jobId);
    }

    // ---------------- 调度 ----------------

    @Override
    public int dispatch(int max) {
        if (max <= 0) {
            return 0;
        }
        Long running = batchMapper.selectCount(new QueryWrapper<GenAiBuildJobBatch>().eq("status", "running"));
        int free = max - (running == null ? 0 : running.intValue());
        if (free <= 0) {
            return 0;
        }
        // 多取一些候选：并发下可能被别的实例抢走，抢不到就跳过
        List<GenAiBuildJobBatch> candidates = batchMapper.selectList(new QueryWrapper<GenAiBuildJobBatch>()
                .eq("status", "pending").orderByAsc("id").last("limit " + (free * 3)));
        int submitted = 0;
        for (GenAiBuildJobBatch b : candidates) {
            if (submitted >= free) {
                break;
            }
            int claimed = batchMapper.update(null, new UpdateWrapper<GenAiBuildJobBatch>()
                    .eq("id", b.getId()).eq("status", "pending")
                    .set("status", "running")
                    .set("started_at", LocalDateTime.now())
                    .set("update_time", LocalDateTime.now()));
            if (claimed != 1) {
                continue; // 已被其它 worker/实例抢走
            }
            submitted++;
            final Long batchId = b.getId();
            pool.submit(() -> runBatch(batchId));
        }
        return submitted;
    }

    @Override
    public void resetStuck() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(Math.max(60, props.getBatchTimeoutSeconds()));
        List<GenAiBuildJobBatch> stuck = batchMapper.selectList(new QueryWrapper<GenAiBuildJobBatch>()
                .eq("status", "running").lt("started_at", deadline).last("limit 20"));
        for (GenAiBuildJobBatch b : stuck) {
            int retried = nz(b.getRetryCount());
            if (retried >= props.getMaxRetry()) {
                batchMapper.update(null, new UpdateWrapper<GenAiBuildJobBatch>()
                        .eq("id", b.getId()).eq("status", "running")
                        .setSql("status = 'failed', error = '批次超时且重试 " + retried + " 次仍失败', "
                                + "finished_at = NOW(), update_time = NOW()"));
                bumpJob(b.getJobId(), 0, 1, 0);
                log.warn("V9.6 批次超时判失败 batchId={} jobId={}", b.getId(), b.getJobId());
            } else {
                batchMapper.update(null, new UpdateWrapper<GenAiBuildJobBatch>()
                        .eq("id", b.getId()).eq("status", "running")
                        .setSql("status = 'pending', retry_count = retry_count + 1, "
                                + "error = '批次超时，已重置等待重试', update_time = NOW()"));
                log.warn("V9.6 批次超时重置 batchId={} 第 {} 次重试", b.getId(), retried + 1);
            }
            finalizeIfDone(b.getJobId());
        }
    }

    @Override
    public void finalizeIfDone(Long jobId) {
        Long open = batchMapper.selectCount(new QueryWrapper<GenAiBuildJobBatch>()
                .eq("job_id", jobId).in("status", "pending", "running"));
        if (open != null && open > 0) {
            return;
        }
        GenAiBuildJob job = jobMapper.selectById(jobId);
        if (job == null || "canceled".equals(job.getStatus())
                || "success".equals(job.getStatus()) || "partial".equals(job.getStatus())
                || "failed".equals(job.getStatus())) {
            return;
        }
        try {
            List<GenAiBuildJobBatch> batches = batchMapper.selectList(new QueryWrapper<GenAiBuildJobBatch>()
                    .eq("job_id", jobId).orderByAsc("seq"));
            Payload p = payload(job);
            List<GenPipelineAiVO.TableModel> tables = new ArrayList<>();
            Map<String, List<String>> moduleTableNames = new LinkedHashMap<>();
            int failedBatches = 0;
            for (GenAiBuildJobBatch b : batches) {
                if ("failed".equals(b.getStatus())) {
                    failedBatches++;
                }
                if (!"module".equals(b.getKind()) || !"success".equals(b.getStatus())) {
                    continue;
                }
                List<GenPipelineAiVO.TableModel> part = parseTables(b.getTables());
                if (part.isEmpty()) {
                    continue;
                }
                tables.addAll(part);
                List<String> names = new ArrayList<>();
                for (GenPipelineAiVO.TableModel t : part) {
                    names.add(t.getTableName());
                }
                moduleTableNames.put(b.getModuleName(), names);
            }
            Set<String> existing = new LinkedHashSet<>(
                    p.getExisting() == null ? List.of() : p.getExisting());
            GenAiBuildPlanVO plan = recommendService.assemblePlan(p.getDto(), tables,
                    moduleTableNames, existing, p.getRelevanceWarning(), p.getRelevanceTip());

            String status;
            if (plan.getTableCount() == 0) {
                status = "failed";
            } else {
                status = failedBatches > 0 ? "partial" : "success";
            }
            GenAiBuildJob update = new GenAiBuildJob();
            update.setId(jobId);
            update.setStatus(status);
            update.setResult(toJson(plan));
            update.setTotalTable(plan.getTableCount());
            if ("failed".equals(status)) {
                update.setError("所有模块均未产出可用表（失败批次 " + failedBatches + "）");
            }
            jobMapper.updateById(update);
            log.info("V9.6 任务完成 jobId={} status={} 表={} 模块={} 失败批={}",
                    jobId, status, plan.getTableCount(), plan.getModuleCount(), failedBatches);
        } catch (Exception e) {
            log.error("V9.6 任务收口失败 jobId={}", jobId, e);
            jobMapper.update(null, new UpdateWrapper<GenAiBuildJob>().eq("id", jobId)
                    .set("status", "failed").set("error", trim(e.getMessage(), 990))
                    .set("update_time", LocalDateTime.now()));
        }
    }

    // ---------------- 批次执行 ----------------

    private void runBatch(Long batchId) {
        GenAiBuildJobBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        Long jobId = batch.getJobId();
        try {
            GenAiBuildJob job = jobMapper.selectById(jobId);
            if (job == null) {
                return;
            }
            if ("canceled".equals(job.getStatus())) {
                markBatch(batch, "skipped", "任务已取消", 0);
                return;
            }
            Payload p = payload(job);
            if ("split".equals(batch.getKind())) {
                doSplit(job, batch, p);
            } else {
                doModule(job, batch, p);
            }
        } catch (Exception e) {
            log.error("V9.6 批次执行失败 batchId={} jobId={}", batchId, jobId, e);
            markBatch(batch, "failed", trim(e.getMessage(), 990), 0);
            bumpJob(jobId, 0, 1, 0);
        } finally {
            finalizeIfDone(jobId);
        }
    }

    /** 拆模块：一次模型调用，产出 N 个 module 批次 */
    private void doSplit(GenAiBuildJob job, GenAiBuildJobBatch batch, Payload p) {
        GenPipelineAiModulesVO mv = aiModelService.modules(
                p.getDto().getSystemName(), p.getDto().getSystemDesc());
        List<GenPipelineAiModulesVO.Module> mods = mv == null || mv.getModules() == null
                ? List.of() : mv.getModules();
        int cap = Math.min(mods.size(), Math.max(1, props.getModuleCap()));
        if (cap == 0) {
            markBatch(batch, "failed", "模型未返回任何业务模块", 0);
            bumpJob(batch.getJobId(), 0, 1, 0);
            return;
        }
        p.setRelevanceWarning(mv.isRelevanceWarning());
        p.setRelevanceTip(mv.getRelevanceTip());
        jobMapper.update(null, new UpdateWrapper<GenAiBuildJob>().eq("id", job.getId())
                .set("payload", toJson(p)).set("update_time", LocalDateTime.now()));

        int seq = 1;
        for (int i = 0; i < cap; i++) {
            GenAiBuildJobBatch mb = newBatch(job.getId(), seq++, "module", mods.get(i).getName());
            batchMapper.insert(mb);
        }
        markBatch(batch, "success", null, 0);
        // total_batch：1(拆模块) + N(模块)；done_batch：拆模块这批已完成
        jobMapper.update(null, new UpdateWrapper<GenAiBuildJob>().eq("id", job.getId())
                .setSql("total_batch = " + (cap + 1) + ", done_batch = done_batch + 1")
                .set("status", "running").set("update_time", LocalDateTime.now()));
        if (mods.size() > cap) {
            log.warn("V9.6 模块数 {} 超过上限 {}，其余未建模", mods.size(), cap);
        }
        log.info("V9.6 拆模块完成 jobId={} 模块={} 批次={}", job.getId(), cap, cap + 1);
    }

    /** 单个模块建模 */
    private void doModule(GenAiBuildJob job, GenAiBuildJobBatch batch, Payload p) {
        GenPipelineAiVO r = aiModelService.model(p.getDto().getSystemName(), p.getDescForAi(),
                batch.getModuleName());
        List<GenPipelineAiVO.TableModel> tables = r == null || r.getTables() == null
                ? List.of() : r.getTables();
        markBatch(batch, "success", tables.isEmpty() ? "模型未返回表" : null, tables.size());
        batchMapper.update(null, new UpdateWrapper<GenAiBuildJobBatch>().eq("id", batch.getId())
                .set("tables", toJson(tables)));
        bumpJob(job.getId(), 1, 0, tables.size());
    }

    private void markBatch(GenAiBuildJobBatch batch, String status, String error, int tableCount) {
        GenAiBuildJobBatch u = new GenAiBuildJobBatch();
        u.setId(batch.getId());
        u.setStatus(status);
        u.setError(error);
        u.setTableCount(tableCount);
        u.setFinishedAt(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(u);
    }

    /** 计数用 SQL 自增，避免多 worker 并发下「读-改-写」丢更新 */
    private void bumpJob(Long jobId, int done, int failed, int tables) {
        String sql = "done_batch = done_batch + " + done
                + ", failed_batch = failed_batch + " + failed
                + ", total_table = total_table + " + tables;
        jobMapper.update(null, new UpdateWrapper<GenAiBuildJob>().eq("id", jobId)
                .setSql(sql).set("update_time", LocalDateTime.now()));
    }

    // ---------------- 工具 ----------------

    private GenAiBuildJob mustGet(Long jobId) {
        GenAiBuildJob job = jobId == null ? null : jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "任务不存在：" + jobId);
        }
        return job;
    }

    private GenAiBuildJobBatch newBatch(Long jobId, int seq, String kind, String moduleName) {
        GenAiBuildJobBatch b = new GenAiBuildJobBatch();
        b.setJobId(jobId);
        b.setSeq(seq);
        b.setKind(kind);
        b.setModuleName(moduleName);
        b.setStatus("pending");
        b.setRetryCount(0);
        b.setTableCount(0);
        return b;
    }

    private Payload payload(GenAiBuildJob job) {
        try {
            Payload p = objectMapper.readValue(job.getPayload(), Payload.class);
            if (p.getDto() == null) {
                throw new IllegalStateException("payload 缺少 dto");
            }
            return p;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "任务参数解析失败：" + e.getMessage());
        }
    }

    private List<GenPipelineAiVO.TableModel> parseTables(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GenPipelineAiVO.TableModel>>() { });
        } catch (Exception e) {
            log.warn("V9.6 批次表结构解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "序列化失败：" + e.getMessage());
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 任务上下文：提交时固化，重试/续跑都用同一份输入 */
    @Data
    public static class Payload {
        private GenAiBuildPlanDTO dto;
        private List<String> existing;
        private String descForAi;
        private Boolean relevanceWarning;
        private String relevanceTip;
    }
}
