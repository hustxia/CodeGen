package com.codegen.testcenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V9.2 测试中心「检查项注册表」。
 *
 * <p>这是执行器唯一允许的命令来源：请求体只能提交 suiteId，命令模板在服务端硬编码，
 * 因此不存在把前端字符串拼进 ProcessBuilder 的可能（FR-01 安全红线）。
 *
 * <p>所有套件统一走 {@code node scripts/testcenter-suites.mjs <suiteId>} 这个驱动：
 * 一是 Windows 上直接 spawn mvn/npm 的 .cmd 会踩 Node 18+ 的 EINVAL（见坑库「测试与基线 #6」），
 * 二是 shell 语法差异（NODE_OPTIONS 清空、路径分隔符）只在一个脚本里处理，
 * 三是 CI 与本地执行器可以调用同一份套件定义，不产生「两套事实」。
 */
public enum TcSuite {

    S1("S1", "后端单测", "312/312 用例", 15, List.of(), true, "P0", "mvn -o test", "JDK21 / 本地 m2"),
    S2("S2", "i18n 快照契约", "9/9 模式，OFF-ON 零 diff", 3, List.of(), true, "P0", "离线 Freemarker harness", "freemarker-2.3.32.jar"),
    S3("S3", "i18n-core 回落链", "9 passed", 3, List.of(), true, "P0", "vitest tests/i18n-core.test.mjs", "Node / npm ci"),
    S4("S4", "206 语言键覆盖", "206/206，8 精译逐键一致，12 RTL", 2, List.of(), true, "P0", "node scripts/check-all-locales.mjs", "Node"),
    S5("S5", "生成物 JS 语法", "1024 组合 0 坏组合", 5, List.of("S1"), true, "P0", "node scripts/check-generated-js.mjs", "S1 产出的 target/gen-matrix"),
    S6("S6", "控制台前端构建", "build success", 8, List.of(), true, "P0", "NODE_OPTIONS= npm run build", "Node / codegen-web/node_modules"),
    S7("S7", "空库冒烟", "schema 全量脚本 + 幂等重跑", 5, List.of(), true, "P0", "node scripts/smoke-fresh-db.mjs", "本机 MySQL / CG_DB_PASSWORD"),
    S8("S8", "Vue3 产物构建与断言", "40/40 + vite build + 14 张截图", 20, List.of(), false, "P1", "人工双服务链路", "两个服务端口 + Chrome/Puppeteer"),
    S9("S9", "产物等价性", "119 + 62 文件 diff -r 为空", 25, List.of(), false, "P1", "人工新旧 jar 双变体对比", "两个 jar / 两个端口");

    /**
     * 唯一的命令模板。占位符只能是 suiteId，且替换值来自本枚举，不接受外部输入。
     */
    public static final List<String> COMMAND_TEMPLATE = List.of("node", "scripts/testcenter-suites.mjs", "{suiteId}");

    /** 驱动脚本所在的工作目录：后端工程根（脚本内部自行切到 codegen-web） */
    public static final String WORK_DIR = "serverRoot";

    private final String id;
    private final String name;
    private final String baseline;
    private final int timeoutMinutes;
    private final List<String> dependsOn;
    private final boolean defaultSelected;
    private final String level;
    private final String entryHint;
    private final String requires;

    TcSuite(String id, String name, String baseline, int timeoutMinutes, List<String> dependsOn,
            boolean defaultSelected, String level, String entryHint, String requires) {
        this.id = id;
        this.name = name;
        this.baseline = baseline;
        this.timeoutMinutes = timeoutMinutes;
        this.dependsOn = List.copyOf(dependsOn);
        this.defaultSelected = defaultSelected;
        this.level = level;
        this.entryHint = entryHint;
        this.requires = requires;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBaseline() { return baseline; }
    public int getTimeoutMinutes() { return timeoutMinutes; }
    public long getTimeoutMillis() { return timeoutMinutes * 60_000L; }
    public List<String> getDependsOn() { return dependsOn; }
    public boolean isDefaultSelected() { return defaultSelected; }
    public String getLevel() { return level; }
    public String getEntryHint() { return entryHint; }
    public String getRequires() { return requires; }

    /** 实际交给 ProcessBuilder 的 argv；不含任何外部输入。 */
    public List<String> argv() {
        List<String> out = new ArrayList<>(COMMAND_TEMPLATE.size());
        for (String part : COMMAND_TEMPLATE) {
            out.add("{suiteId}".equals(part) ? id : part);
        }
        return List.copyOf(out);
    }

    public static List<TcSuite> all() {
        return List.of(values());
    }

    public static TcSuite of(String id) {
        if (id == null) {
            return null;
        }
        for (TcSuite s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** 默认勾选集合：仅 P0，S8/S9 这类重型套件必须显式选择（本机可用内存约 6GB）。 */
    public static List<String> defaultSuiteIds() {
        return Arrays.stream(values())
                .filter(TcSuite::isDefaultSelected)
                .map(TcSuite::getId)
                .collect(Collectors.toList());
    }

    /** 按注册表顺序归一化，去重并拒绝未知 id。 */
    public static List<TcSuite> resolve(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("suiteIds 不能为空");
        }
        Set<String> wanted = new LinkedHashSet<>(ids);
        List<TcSuite> out = new ArrayList<>();
        for (TcSuite s : values()) {
            if (wanted.remove(s.id)) {
                out.add(s);
            }
        }
        if (!wanted.isEmpty()) {
            throw new IllegalArgumentException("未知套件: " + String.join(",", wanted));
        }
        return out;
    }

    /**
     * 注册表自检：由单测与启动时校验调用，防止新增套件时写错依赖/超时/命令。
     * 返回错误清单，空列表代表健康。
     */
    public static List<String> validate() {
        List<String> errors = new ArrayList<>();
        Map<String, TcSuite> byId = Arrays.stream(values())
                .collect(Collectors.toMap(TcSuite::getId, Function.identity(), (a, b) -> a));
        if (byId.size() != values().length) {
            errors.add("套件 id 重复");
        }
        for (TcSuite s : values()) {
            if (s.timeoutMinutes <= 0) {
                errors.add(s.id + " 超时必须为正数");
            }
            if (!"P0".equals(s.level) && !"P1".equals(s.level)) {
                errors.add(s.id + " 级别只能是 P0/P1");
            }
            if ("P1".equals(s.level) && s.defaultSelected) {
                errors.add(s.id + " 是 P1，不允许默认勾选");
            }
            for (String dep : s.dependsOn) {
                if (!byId.containsKey(dep)) {
                    errors.add(s.id + " 依赖了不存在的套件 " + dep);
                }
            }
            // 命令模板必须固定为 3 段，且不含 shell 元字符（防命令注入）
            if (s.argv().size() != COMMAND_TEMPLATE.size()) {
                errors.add(s.id + " argv 长度异常");
            }
            for (String part : s.argv()) {
                if (part == null || part.isBlank()
                        || part.contains(";") || part.contains("&&") || part.contains("|")
                        || part.contains(">") || part.contains("<") || part.contains("$")) {
                    errors.add(s.id + " argv 含非法字符: " + part);
                }
            }
        }
        return errors;
    }
}
