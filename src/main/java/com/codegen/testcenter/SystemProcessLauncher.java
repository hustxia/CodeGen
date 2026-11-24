package com.codegen.testcenter;

import com.codegen.verify.VerifyContractProperties;
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
    private final VerifyContractProperties contract;
    private final ObjectMapper mapper;

    public SystemProcessLauncher(TcProperties props, VerifyContractProperties contract, ObjectMapper mapper) {
        this.props = props;
        this.contract = contract;
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
        // S8/S9 产物（截图 / diff.json）统一落 run 目录，页面才能走 tc_artifact 白名单下载
        env.put("TC_ARTIFACT_DIR", workDir.resolve("artifacts").toString());
        // 验证契约：阈值只在后端配置里维护，页面/CLI/CI 三条入口共用
        env.put("VERIFY_CONTRACT_MAX_ADDED", String.valueOf(contract.getMaxAdded()));
        env.put("VERIFY_CONTRACT_MAX_REMOVED", String.valueOf(contract.getMaxRemoved()));
        env.put("VERIFY_CONTRACT_MAX_MODIFIED", String.valueOf(contract.getMaxModified()));
        env.put("VERIFY_CONTRACT_WHITELIST", contract.getWhitelist() == null ? "" : contract.getWhitelist());
        if (contract.getNewDir() != null && !contract.getNewDir().isBlank()) {
            env.put("TC_DIFF_NEW", contract.getNewDir().trim());
        }
        if (contract.getBaselineDir() != null && !contract.getBaselineDir().isBlank()) {
            env.put("TC_DIFF_BASELINE", contract.getBaselineDir().trim());
        }
        if (contract.getShotUrls() != null && !contract.getShotUrls().isBlank()) {
            env.put("TC_SHOT_URLS", contract.getShotUrls().trim());
        }
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
        Map<String, Object> metrics = TcMetrics.parseAll(tail, mapper);
        // 产物清单随 metrics 回传执行器，由执行器登记 tc_artifact 后剔除。
        // 失败也要带回来：S9 超阈值时 diff.json 正是排查差异的证据。
        List<String> artifacts = TcMetrics.parseArtifacts(tail, mapper);
        if (!artifacts.isEmpty()) {
            metrics.put(TcMetrics.ARTIFACTS_KEY, artifacts);
        }
        if (cancelled) {
            return new TcProcessResult(TcStatus.CANCELLED, -1, duration, "运行被取消", metrics);
        }
        if (timedOut) {
            return new TcProcessResult(TcStatus.TIMEOUT, -1, duration,
                    "套件超时（" + spec.suite().getTimeoutMinutes() + " min）\n" + tail, metrics);
        }
        if (exit != 0) {
            return new TcProcessResult(TcStatus.FAILED, exit, duration, tail, metrics);
        }
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
