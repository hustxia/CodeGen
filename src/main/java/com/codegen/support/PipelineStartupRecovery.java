package com.codegen.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codegen.entity.GenPipeline;
import com.codegen.mapper.GenPipelineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 流水线启动恢复：服务重启后清理由进程内孤儿导致的卡死状态。
 *
 * <p>背景（2026-11-16 实际事故）：流水线执行线程在 JVM 内存里，重启后线程消失，
 * 但 DB 状态仍是 running/current_step=ai_recommend，永远无人推进——前端一直转圈，
 * 且 create() 的「同名库活流水线阻断」会连带把该库堵死。
 *
 * <p>策略：启动时把所有 status=running 的流水线置为 failed（fail_reason 注明是服务重启中断）。
 * 不做"断点续跑"：ai_recommend 依赖进程内上下文，续跑成本远高于用户重新点一次构建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineStartupRecovery implements ApplicationRunner {

    private final GenPipelineMapper pipelineMapper;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<GenPipeline> uw = new LambdaUpdateWrapper<GenPipeline>()
                .eq(GenPipeline::getStatus, "running")
                .set(GenPipeline::getStatus, "failed")
                .set(GenPipeline::getFailReason, "服务重启中断：运行线程随进程终止，请重新构建")
                .set(GenPipeline::getUpdateTime, now);
        int n = pipelineMapper.update(null, uw);
        if (n > 0) {
            log.warn("流水线启动恢复：{} 条 running 流水线因服务重启置为 failed", n);
        }
    }
}
