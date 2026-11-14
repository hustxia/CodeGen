package com.codegen.testcenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 默认子进程启动器：ProcessBuilder + 单套件超时 kill + 取消检查 + 日志落盘。
 *
 * <p>日志一律写到 run 自己的 workDir，不再写 {@code /tmp/verify-*.log}——
 * Windows 上 /tmp 不是通用路径，且重启即丢，无法作为版本验收证据。
 */
@Component
public class SystemProcessLauncher implements TcProcessLauncher {

    private final TcProperties props;
    private final ObjectMapper mapper;

    public SystemProcessLauncher(TcProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public TcProcessResult run(TcProcessSpec spec) throws Exception {
        Path workDir = spec.workDir();
        Files.createDirectories(workDir);
        Path logFile = spec.logFile();

        List<String> argv = new ArrayList<>(spec.suite().argv());
        // node 可执行文件允许由配置覆盖（本机 node 不一定在 Java 进程的 PATH 里），
        // 但其余参数永远来自 TcSuite.argv()，不接受请求体。
        argv.set(0, props.getNodeBin());

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(props.serverRoot().toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        Map<String, String> env = pb.environment();
        // 坑库「测试与基线 #6」：AI 宿主注入的 NODE_OPTIONS fs shim 会让 vite/rimraf 失败
        env.remove("NODE_OPTIONS");
        env.put("TC_SUITE_ID", spec.suite().getId());
        env.put("TC_SERVER_ROOT", props.serverRoot().toString());
        env.put("TC_WEB_ROOT", props.webRoot().toString());
        env.put("TC_WORK_DIR", workDir.toString());
        if (spec.env() != null) {
            for (Map.Entry<String, String> e : spec.env().entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    env.put(e.getKey(), e.getValue());
                }
            }
        }

        long start = System.currentTimeMillis();
        Process process = null;
        boolean cancelled = false;
        boolean timedOut = false;
        int exit = -1;
        try {
            process = pb.start();
            long deadline = start + spec.suite().getTimeoutMillis();
            while (true) {
                if (spec.cancelled() != null && spec.cancelled().getAsBoolean()) {
                    cancelled = true;
                    break;
                }
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    exit = process.exitValue();
                    break;
                }
                if (System.currentTimeMillis() >= deadline) {
                    timedOut = true;
                    break;
                }
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }

        long duration = System.currentTimeMillis() - start;
        String tail = readTail(logFile, props.getTailLines());
        if (cancelled) {
            return TcProcessResult.failed(TcStatus.CANCELLED, -1, duration, "运行被取消");
        }
        if (timedOut) {
            return TcProcessResult.failed(TcStatus.TIMEOUT, -1, duration,
                    "套件超时（" + spec.suite().getTimeoutMinutes() + " min）\n" + tail);
        }
        if (exit != 0) {
            return TcProcessResult.failed(TcStatus.FAILED, exit, duration, tail);
        }
        Map<String, Object> metrics = TcMetrics.parse(tail, mapper);
        // 驱动脚本显式声明 SKIPPED 时不算 PASSED，也不算 FAILED
        if ("SKIPPED".equals(metrics.get("status"))) {
            return new TcProcessResult(TcStatus.SKIPPED, 0, duration,
                    String.valueOf(metrics.getOrDefault("reason", "环境不满足")), metrics);
        }
        return TcProcessResult.passed(duration, metrics);
    }

    private String readTail(Path logFile, int lines) {
        try {
            if (!Files.exists(logFile)) {
                return "";
            }
            String text = Files.readString(logFile, StandardCharsets.UTF_8);
            return TcMetrics.tail(text, lines);
        } catch (IOException e) {
            return "日志读取失败: " + e.getMessage();
        }
    }
}
