package com.codegen.testcenter;

import java.util.Set;

/**
 * 测试中心状态机常量：QUEUED → RUNNING → PASSED | FAILED | CANCELLED | TIMEOUT。
 * 单个套件另有 SKIPPED（依赖缺失、无 MySQL、P1 未自动化）。
 */
public final class TcStatus {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String SKIPPED = "SKIPPED";

    private static final Set<String> TERMINAL = Set.of(PASSED, FAILED, CANCELLED, TIMEOUT);

    private TcStatus() {
    }

    public static boolean isTerminal(String status) {
        return TERMINAL.contains(status);
    }

    public static boolean isRunning(String status) {
        return QUEUED.equals(status) || RUNNING.equals(status);
    }

    /** SKIPPED 不参与通过率分母，也不把整次运行染红。 */
    public static boolean isSuccess(String status) {
        return PASSED.equals(status) || SKIPPED.equals(status);
    }
}
