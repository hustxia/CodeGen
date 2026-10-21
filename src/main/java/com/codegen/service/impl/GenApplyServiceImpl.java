package com.codegen.service.impl;

import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenApplyDTO;
import com.codegen.dto.GenMenuRegisterDTO;
import com.codegen.dto.GenWriteDTO;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenApplyService;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenMenuService;
import com.codegen.service.GenSelfCheckService;
import com.codegen.vo.GenApplyStageVO;
import com.codegen.vo.GenApplyVO;
import com.codegen.vo.GenCheckIssueVO;
import com.codegen.vo.GenSelfCheckVO;
import com.codegen.vo.GenWriteVO;
import com.codegen.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一键生成实现。
 *
 * <p>把「自检 → 备份 → 写盘 →（编译）→ 注册菜单 → 重建路由」串成一条流水线。
 * 顺序是硬约束：路由里 import 的 {@code views/<module>/index.vue} 必须先落盘，
 * 否则菜单点进去 404 —— V1.1 演示时踩过这个坑。
 *
 * <p>自检不通过直接中止（此时一个文件都没写），是这条流水线最划算的地方。
 */
@Service
public class GenApplyServiceImpl implements GenApplyService {

    private static final Logger log = LoggerFactory.getLogger(GenApplyServiceImpl.class);

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private GenSelfCheckService selfCheckService;

    @Autowired
    private GenCodeService genCodeService;

    @Autowired
    private GenMenuService genMenuService;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenOutputProperties outputProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public GenApplyVO apply(GenApplyDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException(400, "请先勾选要生成的表");
        }
        List<Long> ids = dto.getIds();
        long started = System.currentTimeMillis();
        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun());
        boolean overwrite = dto.getOverwrite() == null || Boolean.TRUE.equals(dto.getOverwrite());
        boolean registerMenu = dto.getRegisterMenu() == null || Boolean.TRUE.equals(dto.getRegisterMenu());
        boolean syncRoute = dto.getSyncRoute() == null || Boolean.TRUE.equals(dto.getSyncRoute());
        boolean runCompile = Boolean.TRUE.equals(dto.getCompile());
        boolean skipCheck = Boolean.TRUE.equals(dto.getSkipCheck());

        GenApplyVO result = new GenApplyVO();
        String batchNo = "GEN" + LocalDateTime.now().format(BATCH_FORMAT);
        result.setBatchNo(batchNo);

        // ---------- 1. 自检 ----------
        long t = System.currentTimeMillis();
        GenSelfCheckVO check;
        try {
            check = selfCheckService.check(ids);
        } catch (BusinessException e) {
            result.stage(new GenApplyStageVO("CHECK", "FAILED", e.getMessage(), elapsed(t)));
            result.setPassed(false);
            result.setElapsedMs(elapsed(started));
            return result;
        }
        result.setSelfCheck(check);
        GenApplyStageVO checkStage = new GenApplyStageVO("CHECK",
                Boolean.TRUE.equals(check.isPassed()) ? "PASS" : "FAILED",
                String.format("%d 文件 / %d Java，%d 错误 %d 告警，耗时 %dms",
                        check.getFileCount(), check.getJavaCount(), check.getErrorCount(),
                        check.getWarningCount(), check.getElapsedMs()),
                elapsed(t));
        for (GenCheckIssueVO issue : topIssues(check, 8)) {
            checkStage.detail(issue.getSeverity() + " " + issue.getFile()
                    + (issue.getLine() != null && issue.getLine() > 0 ? ":" + issue.getLine() : "")
                    + " " + issue.getMessage());
        }
        result.stage(checkStage);
        if (!Boolean.TRUE.equals(check.isPassed()) && !skipCheck) {
            checkStage.setMessage(checkStage.getMessage() + " —— 自检未通过，已中止，未写入任何文件");
            result.setPassed(false);
            result.setElapsedMs(elapsed(started));
            return result;
        }

        if (dryRun) {
            result.stage(new GenApplyStageVO("WRITE", "SKIPPED", "dryRun：只自检，不写盘不注册", 0L));
            result.setPassed(Boolean.TRUE.equals(check.isPassed()));
            result.setElapsedMs(elapsed(started));
            return result;
        }

        // ---------- 2. 写盘（含备份） ----------
        t = System.currentTimeMillis();
        GenWriteDTO writeDto = new GenWriteDTO();
        writeDto.setIds(ids);
        writeDto.setOverwrite(overwrite);
        List<GenWriteVO> files = genCodeService.writeToDisk(writeDto, batchNo);
        result.setFiles(files);
        int written = (int) files.stream().filter(f -> "WRITTEN".equals(f.getStatus())).count();
        int skipped = (int) files.stream().filter(f -> "SKIPPED".equals(f.getStatus())).count();
        int failed = (int) files.stream().filter(f -> "ERROR".equals(f.getStatus())).count();
        result.setWritten(written);
        result.setSkipped(skipped);
        result.setFailed(failed);
        result.setBackupDir(resolveBackupDir(files, batchNo));

        GenApplyStageVO writeStage = new GenApplyStageVO("WRITE",
                failed > 0 ? "FAILED" : "PASS",
                String.format("写入 %d，跳过 %d，失败 %d（备份批次 %s）", written, skipped, failed, batchNo),
                elapsed(t));
        files.stream().filter(f -> "ERROR".equals(f.getStatus())).limit(5)
                .forEach(f -> writeStage.detail(f.getPath() + " -> " + f.getMessage()));
        result.stage(writeStage);

        // ---------- 3. 编译（可选） ----------
        if (runCompile) {
            result.stage(compileStage(ids, System.currentTimeMillis()));
        } else {
            result.stage(new GenApplyStageVO("COMPILE", "SKIPPED", "未勾选编译，跳过（可在请求里开 compile=true）", 0L));
        }

        // ---------- 4. 注册菜单 ----------
        if (registerMenu) {
            t = System.currentTimeMillis();
            GenMenuRegisterDTO menuDto = new GenMenuRegisterDTO();
            menuDto.setIds(ids);
            menuDto.setParentId(dto.getParentId());
            menuDto.setRoleId(dto.getRoleId());
            menuDto.setSyncRoute(false);
            try {
                result.setMenu(genMenuService.register(menuDto));
                long registered = result.getMenu().getMenus().stream()
                        .filter(m -> "REGISTERED".equals(m.getStatus())).count();
                long menuFailed = result.getMenu().getMenus().stream()
                        .filter(m -> "ERROR".equals(m.getStatus())).count();
                result.stage(new GenApplyStageVO("MENU", menuFailed > 0 ? "FAILED" : "PASS",
                        String.format("新注册 %d，其余已存在（角色 %s）", registered, result.getMenu().getRoleId()),
                        elapsed(t)));
            } catch (Exception e) {
                result.stage(new GenApplyStageVO("MENU", "FAILED", "菜单注册失败：" + e.getMessage(), elapsed(t)));
            }
        } else {
            result.stage(new GenApplyStageVO("MENU", "SKIPPED", "未勾选注册菜单", 0L));
        }

        // ---------- 5. 重建路由 ----------
        if (syncRoute) {
            t = System.currentTimeMillis();
            try {
                com.codegen.vo.GenMenuRegisterVO routeResult = genMenuService.syncRoute(false);
                if (result.getMenu() == null) {
                    result.setMenu(routeResult);
                }
                result.stage(new GenApplyStageVO("ROUTE",
                        Boolean.TRUE.equals(routeResult.isRouteSynced()) ? "PASS" : "FAILED",
                        "路由文件：" + routeResult.getRouteFile()
                                + (StringUtils.hasText(routeResult.getMessage())
                                ? "（" + routeResult.getMessage() + "）" : ""),
                        elapsed(t)));
            } catch (Exception e) {
                result.stage(new GenApplyStageVO("ROUTE", "FAILED", "路由重建失败：" + e.getMessage(), elapsed(t)));
            }
        } else {
            result.stage(new GenApplyStageVO("ROUTE", "SKIPPED", "未勾选重建路由", 0L));
        }

        result.setPassed(result.getStages().stream().noneMatch(s -> "FAILED".equals(s.getStatus())));
        result.setElapsedMs(elapsed(started));
        return result;
    }

    @Override
    public Map<String, Object> rollback(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            throw new BusinessException(400, "批次号不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT table_name, file_path, backup_path, status FROM gen_history WHERE batch_no = ? "
                        + "ORDER BY id DESC", batchNo);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "批次不存在：" + batchNo);
        }
        int restored = 0;
        int removed = 0;
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String filePath = str(row.get("file_path"));
            String backupPath = str(row.get("backup_path"));
            String status = str(row.get("status"));
            if (!"WRITTEN".equals(status) || !StringUtils.hasText(filePath)) {
                continue;
            }
            try {
                Path target = Paths.get(filePath);
                if (StringUtils.hasText(backupPath) && Files.exists(Paths.get(backupPath))) {
                    Files.createDirectories(target.getParent());
                    Files.copy(Paths.get(backupPath), target, StandardCopyOption.REPLACE_EXISTING);
                    restored++;
                } else if (Files.exists(target)) {
                    // 首次生成的文件：备份为空，回滚即删除
                    Files.delete(target);
                    removed++;
                }
            } catch (Exception e) {
                problems.add(filePath + " -> " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("total", rows.size());
        result.put("restored", restored);
        result.put("removed", removed);
        result.put("problems", problems);
        log.info("rollback batch {}: restored={}, removed={}", batchNo, restored, removed);
        return result;
    }

    @Override
    public List<Map<String, Object>> recentBatches(int limit) {
        int size = limit <= 0 ? 10 : Math.min(limit, 50);
        return jdbcTemplate.queryForList(
                "SELECT batch_no, COUNT(*) AS files, MAX(create_time) AS last_time, "
                        + "MIN(operator) AS operator, SUM(status = 'ERROR') AS errors "
                        + "FROM gen_history GROUP BY batch_no ORDER BY MAX(create_time) DESC LIMIT " + size);
    }

    /**
     * 可选编译：在后端仓库根目录执行 mvn compile。
     * 输出重定向到临时文件，避免大输出把管道写满导致 waitFor 死锁。
     */
    private GenApplyStageVO compileStage(List<Long> ids, long started) {
        String root = resolveBackendRoot(ids);
        if (!StringUtils.hasText(root)) {
            return new GenApplyStageVO("COMPILE", "SKIPPED", "未配置后端根目录，跳过编译", elapsed(started));
        }
        Path dir = Paths.get(root);
        if (!Files.exists(dir.resolve("pom.xml"))) {
            return new GenApplyStageVO("COMPILE", "SKIPPED",
                    "后端根目录没有 pom.xml，跳过编译：" + dir, elapsed(started));
        }
        String mvn = resolveMaven();
        if (mvn == null) {
            return new GenApplyStageVO("COMPILE", "SKIPPED",
                    "未找到 maven（配置 gen.output.maven 或设置 MAVEN_HOME），跳过编译", elapsed(started));
        }
        Path logFile = null;
        try {
            logFile = Files.createTempFile("gen-compile-", ".log");
            List<String> command = new ArrayList<>();
            if (isWindows()) {
                command.add("cmd");
                command.add("/c");
            }
            command.add(mvn);
            command.add("-o");
            command.add("-q");
            command.add("-DskipTests");
            command.add("compile");
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            Process process = pb.start();
            boolean finished = process.waitFor(outputProperties.getCompileTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GenApplyStageVO("COMPILE", "FAILED",
                        "编译超时（>" + outputProperties.getCompileTimeoutSeconds() + "s）", elapsed(started));
            }
            int exit = process.exitValue();
            String output = Files.readString(logFile, StandardCharsets.UTF_8);
            GenApplyStageVO stage = new GenApplyStageVO("COMPILE",
                    exit == 0 ? "PASS" : "FAILED",
                    exit == 0 ? "mvn compile 通过" : "mvn compile 失败（exit=" + exit + "），文件已写入，可修正后重跑或回滚",
                    elapsed(started));
            tail(output, 15).forEach(stage::detail);
            return stage;
        } catch (Exception e) {
            return new GenApplyStageVO("COMPILE", "FAILED", "编译执行失败：" + e.getMessage(), elapsed(started));
        } finally {
            if (logFile != null) {
                try {
                    Files.deleteIfExists(logFile);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响结果
                }
            }
        }
    }

    private String resolveBackendRoot(List<Long> ids) {
        for (Long id : ids) {
            GenTable table = genTableMapper.selectById(id);
            if (table != null && StringUtils.hasText(table.getBackendRoot())) {
                return table.getBackendRoot();
            }
        }
        return outputProperties.getBackendRoot();
    }

    /**
     * 找 maven：配置 > MAVEN_HOME > PATH
     */
    private String resolveMaven() {
        if (StringUtils.hasText(outputProperties.getMaven())) {
            return outputProperties.getMaven();
        }
        String mavenHome = System.getenv("MAVEN_HOME");
        if (!StringUtils.hasText(mavenHome)) {
            mavenHome = System.getenv("M2_HOME");
        }
        if (StringUtils.hasText(mavenHome)) {
            Path candidate = Paths.get(mavenHome, "bin", isWindows() ? "mvn.cmd" : "mvn");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return "mvn";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private List<String> tail(String text, int lines) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String[] all = text.split("\n");
        List<String> out = new ArrayList<>();
        for (int i = Math.max(0, all.length - lines); i < all.length; i++) {
            String line = all[i].trim();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private List<GenCheckIssueVO> topIssues(GenSelfCheckVO check, int limit) {
        List<GenCheckIssueVO> issues = check.getIssues() == null ? List.of() : check.getIssues();
        List<GenCheckIssueVO> sorted = new ArrayList<>(issues);
        sorted.sort((a, b) -> {
            boolean ea = "ERROR".equals(a.getSeverity());
            boolean eb = "ERROR".equals(b.getSeverity());
            return ea == eb ? 0 : (ea ? -1 : 1);
        });
        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }

    /**
     * 从写盘结果里还原本批次备份根目录（{@code <root>/.gen-backup/<batchNo>}）
     */
    private String resolveBackupDir(List<GenWriteVO> files, String batchNo) {
        AtomicReference<String> dir = new AtomicReference<>();
        files.stream().map(GenWriteVO::getBackupPath).filter(StringUtils::hasText).findFirst().ifPresent(bp -> {
            String normalized = bp.replace('\\', '/');
            int idx = normalized.indexOf("/.gen-backup/");
            if (idx > 0) {
                dir.set(normalized.substring(0, idx) + "/.gen-backup/" + batchNo);
            }
        });
        return dir.get();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long elapsed(long from) {
        return System.currentTimeMillis() - from;
    }
}
