package com.codegen.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 流水线 SSE 订阅注册表：pipelineId → 在线订阅者。
 *
 * <p>为什么不用 WebSocket：单向推送 + 断线由前端 EventSource 自动重连，
 * SSE 体积小、无需额外依赖。发送失败（前端已关页）直接摘除，不做重试。
 */
@Slf4j
@Component
public class PipelineSseRegistry {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long pipelineId) {
        // 0L = 不超时，由服务端在终态时 complete
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(pipelineId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    public void send(Long pipelineId, Object data) {
        List<SseEmitter> list = emitters.get(pipelineId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(data));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
        // 终态后 3 秒关闭，给前端留收尾时间
        if (data instanceof com.codegen.vo.GenPipelineVO vo && isTerminal(vo.getStatus())) {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                complete(pipelineId);
            });
        }
    }

    public void complete(Long pipelineId) {
        List<SseEmitter> list = emitters.remove(pipelineId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 已断开的连接 complete 会抛异常，忽略
                }
            }
        }
    }

    private boolean isTerminal(String status) {
        return "success".equals(status) || "failed".equals(status) || "stopped".equals(status);
    }
}
