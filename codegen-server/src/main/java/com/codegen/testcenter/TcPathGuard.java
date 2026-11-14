package com.codegen.testcenter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 产物读取的路径白名单。
 *
 * <p>tc_artifact.file_path 存在库里，但库记录本身也可能被误写/篡改，
 * 因此读取时仍要重新做一次「规范化后必须落在本次 run 的 workDir 内」的校验，
 * 挡住 {@code ../../}、绝对路径与符号链接逃逸（FR-01 / 验收第 6 条）。
 */
public final class TcPathGuard {

    private TcPathGuard() {
    }

    public static Path resolve(Path workDir, String storedPath) {
        if (workDir == null || storedPath == null || storedPath.isBlank()) {
            throw new SecurityException("产物路径或运行目录为空");
        }
        String raw = storedPath.trim();
        if (raw.contains("..") || raw.contains("\0")) {
            throw new SecurityException("产物路径含非法片段: " + raw);
        }
        Path root;
        try {
            root = workDir.toAbsolutePath().normalize();
            if (Files.exists(root)) {
                root = root.toRealPath();
            }
            Path target = Paths.get(raw).toAbsolutePath().normalize();
            Path real = Files.exists(target) ? target.toRealPath() : target;
            if (!real.startsWith(root)) {
                throw new SecurityException("产物路径越出运行目录: " + raw);
            }
            if (!Files.exists(real) || !Files.isRegularFile(real)) {
                throw new SecurityException("产物文件不存在或不是普通文件: " + raw);
            }
            return real;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("产物路径不可用: " + e.getMessage(), e);
        }
    }

    /** 只用于展示：把绝对路径转成相对 runNo 目录的短路径，避免把本机路径下发到前端。 */
    public static String relativeLogPath(Path workDir, Path logFile) {
        try {
            return workDir.getFileName().toString() + "/" + logFile.getFileName().toString();
        } catch (Exception e) {
            return logFile.getFileName().toString();
        }
    }
}
