package com.codegen.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 触发运行的请求体。
 *
 * <p>刻意用 {@code @JsonAnySetter} 把未知字段收集下来而不是让 Jackson 静默丢弃：
 * 客户端若试图夹带 command / filePath / workDir 之类的执行参数，必须被显式识别并拒绝，
 * 同时由 Controller 的 @Log 审计留下痕迹（FR-01 安全红线 / 验收第 6 条）。
 */
@Data
public class TcRunRequest {

    /** 命令与路径类参数一律禁止由请求体下发 */
    public static final Set<String> FORBIDDEN_KEYS = Set.of(
            "command", "cmd", "argv", "args", "shell", "script", "executable",
            "path", "filepath", "workdir", "cwd", "env", "environment",
            "timeout", "timeoutms", "suitecommand", "driver");

    private List<String> suiteIds;

    /** MANUAL / CI / SCHEDULE / RERUN；服务端会做白名单收敛 */
    private String trigger = "MANUAL";

    /** 关联的 commit，仅用于追溯，不参与命令构造 */
    private String gitCommit;

    @JsonIgnore
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void collectUnknown(String key, Object value) {
        unknownFields.put(key, value);
    }

    /** 返回第一个被拒绝的字段名，null 代表请求干净。大小写不敏感。 */
    public String firstForbiddenKey() {
        for (String key : unknownFields.keySet()) {
            String normalized = key == null ? "" : key.toLowerCase().replace("_", "").replace("-", "");
            if (FORBIDDEN_KEYS.contains(normalized)) {
                return key;
            }
        }
        return null;
    }

    public Map<String, Object> getUnknownFields() {
        return unknownFields;
    }
}
