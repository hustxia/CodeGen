package com.codegen.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线并发运行闸门（V9.8）：允许多条流水线同时跑，但守住两条安全线。
 *
 * <p>为什么不能无脑放开并发：
 * <ul>
 *   <li>打包/部署是重 IO（Maven、npm），无上限并发会把宿主机拖死——
 *       全局上限由 {@code gen.pipeline.max-running} 控制；</li>
 *   <li>同一系统（projectId）的两条流水线共用同一个生成目录（backendRoot/frontendRoot）
 *       与目标库，并发跑会互相覆盖产物——同系统串行、跨系统并行。</li>
 * </ul>
 *
 * <p>状态记在内存集合（与旧的单机 AtomicBoolean 语义一致）：进程重启后 DB 里残留的
 * running 状态交给启动恢复/人工处理，闸门只管本进程生命周期。
 */
public class PipelineRunGate {

    private final Set<Long> runningIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> runningProjectIds = ConcurrentHashMap.newKeySet();
    private final int maxRunning;

    public PipelineRunGate(int maxRunning) {
        this.maxRunning = Math.max(1, maxRunning);
    }

    /**
     * 占坑：超过全局上限或同系统已有运行中任务时抛 {@link IllegalStateException}，
     * 消息可直接透给前端。projectId 为 null（尚未建库）的流水线不做同系统互斥。
     */
    public synchronized void acquire(Long id, Long projectId) {
        if (runningIds.contains(id)) {
            throw new IllegalStateException("该流水线已在运行中，请勿重复启动");
        }
        if (runningIds.size() >= maxRunning) {
            throw new IllegalStateException("已有 " + runningIds.size() + " 条流水线在运行"
                    + "（上限 " + maxRunning + "，可调 gen.pipeline.max-running），请稍后再试");
        }
        if (projectId != null && runningProjectIds.contains(projectId)) {
            throw new IllegalStateException("该系统已有流水线在运行：同一系统的两条流水线会共用生成目录与目标库，"
                    + "请等它结束或先停止");
        }
        runningIds.add(id);
        if (projectId != null) {
            runningProjectIds.add(projectId);
        }
    }

    /** 释放：流水线执行结束（成功/失败/被停止后的兜底）时调用，幂等 */
    public synchronized void release(Long id, Long projectId) {
        runningIds.remove(id);
        if (projectId != null) {
            runningProjectIds.remove(projectId);
        }
    }

    public int runningCount() {
        return runningIds.size();
    }

    public int maxRunning() {
        return maxRunning;
    }
}
