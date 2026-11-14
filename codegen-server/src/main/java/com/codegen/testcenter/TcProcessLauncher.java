package com.codegen.testcenter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 子进程启动抽象。抽成接口是为了让执行器的状态机、超时、取消、落库逻辑
 * 能在纯单测里用假进程验证，不必真的跑一次 15 分钟的 mvn。
 */
@FunctionalInterface
public interface TcProcessLauncher {

    TcProcessResult run(TcProcessSpec spec) throws Exception;

    /** 一次套件执行的输入，全部由服务端构造。 */
    record TcProcessSpec(TcSuite suite, Path workDir, Path logFile,
                         BooleanSupplier cancelled, Map<String, String> env) {
    }

    /** 一次套件执行的结果。metrics 是结构化基线值，会写进 tc_case_result.metrics_json。 */
    record TcProcessResult(String status, int exitCode, long durationMs,
                           String errorExcerpt, Map<String, Object> metrics) {

        public static TcProcessResult passed(long durationMs, Map<String, Object> metrics) {
            return new TcProcessResult(TcStatus.PASSED, 0, durationMs, null,
                    metrics == null ? new LinkedHashMap<>() : metrics);
        }

        public static TcProcessResult failed(String status, int exitCode, long durationMs,
                                             String errorExcerpt) {
            return new TcProcessResult(status, exitCode, durationMs, errorExcerpt, new LinkedHashMap<>());
        }
    }
}
