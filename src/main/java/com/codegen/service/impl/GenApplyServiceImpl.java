package com.codegen.service.impl;

import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenApplyDTO;
import com.codegen.dto.GenDiffDTO;
import com.codegen.dto.GenMenuRegisterDTO;
import com.codegen.dto.GenRollbackDTO;
import com.codegen.dto.GenWriteDTO;
import com.codegen.entity.GenOutput;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenOutputMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenApplyService;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenMenuService;
import com.codegen.service.GenSelfCheckService;
import com.codegen.util.GenDigestUtils;
import com.codegen.vo.GenApplyStageVO;
import com.codegen.vo.GenApplyVO;
import com.codegen.vo.GenCheckIssueVO;
import com.codegen.vo.GenDiffVO;
import com.codegen.vo.GenImpactVO;
import com.codegen.vo.GenRollbackVO;
import com.codegen.vo.GenSelfCheckVO;
import com.codegen.vo.GenWriteVO;
import com.codegen.common.context.UserContext;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

    /**
     * 回滚批次精确到毫秒。
     *
     * <p>为什么不能和写盘批次一样只到秒：回滚的「回滚前备份」放在
     * {@code .gen-backup/<批次号>/<相对路径>}，批次号相同就是同一个目录。
     * 用户点一次回滚、紧接着点一次「撤销回滚」，两次落在同一秒时，
     * 第二次的备份会**覆盖掉它正要用的还原源**——回滚看起来成功，实际什么都没变，
     * 而且备份链被永久破坏。契约测试（rollbackRestoresAndCanRollbackAgain）钉住了这一条。
     */
    private static final DateTimeFormatter ROLLBACK_BATCH_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private GenSelfCheckService selfCheckService;

    @Autowired
    private GenCodeService genCodeService;

    @Autowired
    private GenMenuService genMenuService;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenOutputMapper genOutputMapper;

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
        // V6.8：受保护文件（既有 + 无台账快照）默认跳过，force=true 才覆盖
        boolean force = Boolean.TRUE.equals(dto.getForce());

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
            result.setWritten(0);
            result.setSkipped(0);
            result.setFailed(0);
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
            result.setWritten(0);
            result.setSkipped(0);
            result.setFailed(0);
            result.setElapsedMs(elapsed(started));
            return result;
        }

        if (dryRun) {
            // V6.8：dryRun 不再只回一句「不写盘」，要给文件级影响面——
            // 新增/修改/未变/受保护四类计数 + 逐文件明细，重生成前先看清会动什么
            GenImpactVO impact = impactOf(ids);
            result.setImpact(impact);
            result.stage(new GenApplyStageVO("WRITE", "SKIPPED",
                    String.format("dryRun：新增 %d，修改 %d，未变 %d，受保护跳过 %d（共 %d 个文件），不写盘不注册",
                            impact.getNewCount(), impact.getModifiedCount(), impact.getUnchangedCount(),
                            impact.getProtectedCount(), impact.getTotal()), 0L));
            result.setPassed(Boolean.TRUE.equals(check.isPassed()));
            result.setWritten(0);
            result.setSkipped(0);
            result.setFailed(0);
            result.setElapsedMs(elapsed(started));
            return result;
        }

        // ---------- 2. 写盘（含备份） ----------
        t = System.currentTimeMillis();
        GenWriteDTO writeDto = new GenWriteDTO();
        writeDto.setIds(ids);
        writeDto.setOverwrite(overwrite);
        writeDto.setForce(force);
        List<GenWriteVO> files = genCodeService.writeToDisk(writeDto, batchNo);
        result.setFiles(files);
        int written = (int) files.stream().filter(f -> "WRITTEN".equals(f.getStatus())).count();
        int skipped = (int) files.stream().filter(f -> "SKIPPED".equals(f.getStatus())).count();
        int failed = (int) files.stream().filter(f -> "ERROR".equals(f.getStatus())).count();
        // 写盘结果里「受保护」被记为 SKIPPED，影响面用同一份判定回填，避免两处口径不一致
        result.setImpact(impactOf(ids));
        result.setWritten(written);
        result.setSkipped(skipped);
        result.setFailed(failed);
        result.setBackupDir(resolveBackupDir(files, batchNo));
        // 台账失败不静默（V4.2）：文件已落盘、写盘不算失败，但账没记上要让调用方看见
        result.setLedgerErrors(genCodeService.ledgerErrorCount());

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
        GenRollbackVO vo = rollback(batchNo, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("total", vo.getTotal());
        result.put("restored", vo.getRestore());
        result.put("removed", vo.getRemove());
        result.put("skipped", vo.getSkip());
        result.put("rollbackBatch", vo.getRollbackBatch());
        result.put("problems", vo.getProblems());
        return result;
    }

    @Override
    public GenRollbackVO rollbackPreview(String batchNo, GenRollbackDTO dto) {
        return rollbackPlan(batchNo, dto, false);
    }

    @Override
    public GenRollbackVO rollback(String batchNo, GenRollbackDTO dto) {
        return rollbackPlan(batchNo, dto, true);
    }

    /**
     * 回滚的计划与执行合在一起：预览和执行必须是同一份判定逻辑。
     *
     * <p>分成两个方法各写一遍，就会出现「预览说会还原 3 个，执行却删了 1 个」——
     * 这种不一致比没有预览更危险，因为用户是照着预览点的确认。
     */
    private GenRollbackVO rollbackPlan(String batchNo, GenRollbackDTO dto, boolean execute) {
        if (!StringUtils.hasText(batchNo)) {
            throw new BusinessException(400, "批次号不能为空");
        }
        // 同一路径取该批次最后一条：一个批次内同一文件理论上只写一次，
        // 但批处理重试会留下多行，取最新那行才对应磁盘现状
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT o.id, o.batch_no, o.table_id, o.table_name, o.file_group, o.rel_path, o.abs_path, "
                        + "o.action, o.file_size, o.md5, o.backup_path, o.prev_md5, o.message "
                        + "FROM gen_output o INNER JOIN ("
                        + "  SELECT abs_path, MAX(id) AS max_id FROM gen_output"
                        + "  WHERE batch_no = ? AND deleted = 0 AND abs_path <> ''"
                        + "    AND action IN ('WRITTEN', 'OVERWRITTEN', 'MERGED', 'ROLLBACK')"
                        + "  GROUP BY abs_path"
                        + ") t ON o.id = t.max_id ORDER BY o.id DESC", batchNo);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "批次不存在或无需回滚的文件：" + batchNo);
        }

        Set<String> ledgerFilter = toSet(dto == null ? null : dto.getLedgerIds());
        Set<String> pathFilter = toSet(dto == null ? null : dto.getPaths());
        boolean skipDrifted = dto != null && Boolean.TRUE.equals(dto.getSkipDrifted());

        GenRollbackVO vo = new GenRollbackVO();
        vo.setBatchNo(batchNo);
        vo.setExecuted(execute);
        for (Map<String, Object> row : rows) {
            if (!matched(row, ledgerFilter, pathFilter)) {
                continue;
            }
            vo.getItems().add(classify(row));
        }
        if (vo.getItems().isEmpty()) {
            throw new BusinessException(404, "所选文件不在该批次台账中：" + batchNo);
        }
        vo.setTotal(vo.getItems().size());
        vo.setDrifted((int) vo.getItems().stream().filter(GenRollbackVO.Item::isDrifted).count());

        if (!execute) {
            vo.setRestore(count(vo, "RESTORE"));
            vo.setRemove(count(vo, "DELETE"));
            vo.setSkip(count(vo, "SKIP"));
            return vo;
        }

        // 回滚本身也记台账（新批次），否则「回滚」这个动作不可再回滚：
        // 覆盖模式回滚后如果又后悔，用户需要能回到回滚前的样子
        String rollbackBatch = "RBK" + LocalDateTime.now().format(ROLLBACK_BATCH_FORMAT);
        vo.setRollbackBatch(rollbackBatch);
        int restored = 0;
        int removed = 0;
        int skipped = 0;
        for (GenRollbackVO.Item item : vo.getItems()) {
            if ("SKIP".equals(item.getOp())) {
                skipped++;
                continue;
            }
            if (skipDrifted && item.isDrifted()) {
                item.setMessage("磁盘已被手改，按「跳过漂移文件」不处理");
                skipped++;
                continue;
            }
            try {
                Path target = Paths.get(item.getAbsPath());
                boolean exists = Files.exists(target);
                String beforeMd5 = exists ? GenDigestUtils.md5Hex(target) : null;
                // 回滚前先备份当前内容，回滚才可逆
                String beforeBackup = exists
                        ? backupBeforeRollback(target, item.getRelPath(), rollbackBatch,
                                "RESTORE".equals(item.getOp()) ? item.getReasonPath() : null) : null;
                if ("RESTORE".equals(item.getOp())) {
                    Path from = Paths.get(item.getReasonPath());
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(from, target, StandardCopyOption.REPLACE_EXISTING);
                    item.setApplied(true);
                    item.setMessage(exists ? "已还原为覆盖前内容" : "已从备份恢复（文件此前被删除）");
                    writeRollbackLedger(rollbackBatch, batchNo, item, target, "已还原为覆盖前内容",
                            beforeMd5, beforeBackup);
                    restored++;
                } else {
                    Files.delete(target);
                    item.setApplied(true);
                    item.setMessage("已删除（首次生成的文件）");
                    writeRollbackLedger(rollbackBatch, batchNo, item, target, "回滚删除首次生成的文件",
                            beforeMd5, beforeBackup);
                    removed++;
                }
            } catch (Exception e) {
                item.setMessage("失败：" + e.getMessage());
                vo.getProblems().add(item.getRelPath() + " -> " + e.getMessage());
                skipped++;
            }
        }
        vo.setRestore(restored);
        vo.setRemove(removed);
        vo.setSkip(skipped);
        log.info("rollback batch {} -> {}: restored={}, removed={}, skipped={}",
                batchNo, rollbackBatch, restored, removed, skipped);
        return vo;
    }

    /**
     * 按台账行判定该文件的回滚动作。只判定事实，不落盘。
     */
    private GenRollbackVO.Item classify(Map<String, Object> row) {
        GenRollbackVO.Item item = new GenRollbackVO.Item();
        String ledgerId = str(row.get("id"));
        String relPath = str(row.get("rel_path"));
        String absPath = str(row.get("abs_path"));
        String action = str(row.get("action"));
        String md5 = str(row.get("md5"));
        String backupPath = str(row.get("backup_path"));
        item.setLedgerId(ledgerId);
        item.setTableId(str(row.get("table_id")));
        item.setTableName(str(row.get("table_name")));
        item.setFileGroup(str(row.get("file_group")));
        item.setRelPath(relPath);
        item.setAbsPath(absPath);
        item.setAction(action);

        if (!StringUtils.hasText(absPath)) {
            item.setOp("SKIP");
            item.setReason("台账没有绝对路径（可能是失败记录），无法定位文件");
            return item;
        }
        Path target = Paths.get(absPath);
        boolean exists = Files.exists(target);
        String currentMd5 = null;
        if (exists) {
            try {
                currentMd5 = GenDigestUtils.md5Hex(target);
            } catch (Exception e) {
                item.setOp("SKIP");
                item.setReason("磁盘文件读取失败：" + e.getMessage());
                return item;
            }
        }
        boolean drifted = StringUtils.hasText(md5) && currentMd5 != null
                && !md5.equalsIgnoreCase(currentMd5);
        item.setDrifted(drifted);
        boolean backupOk = StringUtils.hasText(backupPath) && Files.exists(Paths.get(backupPath));
        Decision decision = decideOp(action, backupOk, exists, drifted);
        item.setOp(decision.op());
        item.setReason(decision.reason());
        if (backupOk && "RESTORE".equals(decision.op())) {
            item.setReasonPath(backupPath);
        }
        return item;
    }

    /**
     * 回滚动作判定（纯函数，便于单测覆盖全部分支）。
     *
     * <p>规则只有四条，但每条都对应一次真实的数据风险：
     * 有备份就还原（覆盖模式也能救回来）；首次生成的文件删除（但被手改的不删）；
     * 覆盖类记录备份丢了就只能跳过——没有备份还硬还原，等于用当前内容覆盖当前内容，
     * 看起来"成功"了实际什么都没做，比报错更误导人。
     *
     * @param action       台账动作
     * @param backupOk     备份文件存在
     * @param targetExists 目标文件存在
     * @param drifted      磁盘内容与台账 md5 不一致
     */
    static Decision decideOp(String action, boolean backupOk, boolean targetExists, boolean drifted) {
        if (backupOk) {
            if (!targetExists) {
                return new Decision("RESTORE", "文件已被删除，可从备份恢复");
            }
            return new Decision("RESTORE", drifted
                    ? "还原为覆盖前内容（注意：会覆盖生成后的手改）"
                    : "还原为覆盖前内容");
        }
        if ("WRITTEN".equals(action)) {
            if (!targetExists) {
                return new Decision("SKIP", "首次生成的文件已不在磁盘，无需删除");
            }
            if (drifted) {
                return new Decision("SKIP", "首次生成的文件已被手改，删除会丢内容");
            }
            return new Decision("DELETE", "首次生成的文件，回滚即删除");
        }
        return new Decision("SKIP", "覆盖类记录但没有可用备份（备份已丢失或被清理），不能贸然还原");
    }

    record Decision(String op, String reason) {
    }

    /**
     * 回滚前备份当前文件到 {@code <root>/.gen-backup/<rollbackBatch>/<relPath>}
     *
     * @param avoidPath 本次回滚将要读取的还原源；与备份目标同一路径时改名，
     *                  否则「备份」会把「还原源」覆盖掉，回滚变成空操作
     */
    private String backupBeforeRollback(Path target, String relPath, String batchNo, String avoidPath) {
        try {
            Path root = inferRoot(target, relPath);
            if (root == null) {
                return null;
            }
            Path backupRoot = root.resolve(".gen-backup").resolve(batchNo);
            Path dest = backupRoot.resolve(relPath.replace('\\', '/')).normalize();
            if (!dest.startsWith(backupRoot)) {
                return null;
            }
            if (avoidPath != null && !avoidPath.isBlank()
                    && dest.equals(Paths.get(avoidPath).toAbsolutePath().normalize())) {
                dest = dest.resolveSibling(dest.getFileName() + ".before");
                log.warn("回滚前备份与还原源同路径，已改名避免覆盖：{}", dest);
            }
            if (dest.getParent() != null) {
                Files.createDirectories(dest.getParent());
            }
            Files.copy(target, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest.toString();
        } catch (Exception e) {
            log.warn("回滚前备份失败（继续回滚，但该次回滚不可逆）：{} 原因={}", target, e.getMessage());
            return null;
        }
    }

    /**
     * 由「绝对路径 + 相对路径」反推工程根目录：relPath 有几段就往上退几层
     */
    private Path inferRoot(Path target, String relPath) {
        if (!StringUtils.hasText(relPath)) {
            return null;
        }
        int depth = Paths.get(relPath.replace('\\', '/')).getNameCount();
        Path root = target;
        for (int i = 0; i < depth; i++) {
            root = root.getParent();
            if (root == null) {
                return null;
            }
        }
        return root;
    }

    /**
     * 回滚动作写台账：action=ROLLBACK，让下一轮回滚能把这次回滚再撤回去
     */
    private void writeRollbackLedger(String rollbackBatch, String sourceBatch, GenRollbackVO.Item item,
                                     Path target, String message, String prevMd5, String beforeBackup) {
        try {
            GenOutput row = new GenOutput();
            row.setBatchNo(rollbackBatch);
            row.setTableId(parseLong(item.getTableId()));
            row.setTableName(item.getTableName());
            row.setRelPath(item.getRelPath());
            row.setAbsPath(item.getAbsPath());
            row.setAction("ROLLBACK");
            row.setFileGroup(StringUtils.hasText(item.getFileGroup()) ? item.getFileGroup() : "backend");
            boolean exists = Files.exists(target);
            row.setFileSize(exists ? Files.size(target) : 0L);
            row.setMd5(exists ? GenDigestUtils.md5Hex(target) : "");
            row.setPrevMd5(prevMd5);
            row.setBackupPath(beforeBackup);
            row.setMessage(message + "（来源批次 " + sourceBatch + "）");
            row.setOperator(currentOperator());
            genOutputMapper.insert(row);
        } catch (Exception e) {
            log.warn("回滚台账写入失败：batch={} path={} 原因={}", rollbackBatch, item.getRelPath(), e.getMessage());
        }
    }

    private static int count(GenRollbackVO vo, String op) {
        return (int) vo.getItems().stream().filter(i -> op.equals(i.getOp())).count();
    }

    private static Set<String> toSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream().filter(StringUtils::hasText)
                .map(String::trim).collect(Collectors.toSet());
    }

    private static boolean matched(Map<String, Object> row, Set<String> ledgerFilter, Set<String> pathFilter) {
        if (ledgerFilter.isEmpty() && pathFilter.isEmpty()) {
            return true;
        }
        if (!ledgerFilter.isEmpty() && ledgerFilter.contains(str(row.get("id")))) {
            return true;
        }
        if (!pathFilter.isEmpty()) {
            String relPath = str(row.get("rel_path"));
            String absPath = str(row.get("abs_path"));
            return pathFilter.contains(relPath) || pathFilter.contains(absPath);
        }
        return false;
    }

    private String currentOperator() {
        String name = UserContext.getUsername();
        if (StringUtils.hasText(name)) {
            return name;
        }
        Long userId = UserContext.getUserId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    @Override
    public List<Map<String, Object>> recentBatches(int limit) {
        int size = limit <= 0 ? 10 : Math.min(limit, 50);
        return jdbcTemplate.queryForList(
                "SELECT batch_no, COUNT(*) AS files, MAX(create_time) AS last_time, "
                        + "MIN(operator) AS operator, SUM(action = 'ERROR') AS errors, "
                        + "SUM(action IN ('WRITTEN', 'OVERWRITTEN', 'MERGED')) AS written, "
                        + "SUM(action = 'ROLLBACK') AS rollback_files "
                        + "FROM gen_output WHERE deleted = 0 "
                        + "GROUP BY batch_no ORDER BY MAX(create_time) DESC LIMIT " + size);
    }

    /**
     * 可选编译：在后端仓库根目录执行 mvn compile。
     * 输出重定向到临时文件，避免大输出把管道写满导致 waitFor 死锁。
     */
    /**
     * V6.8 计算重生成影响面：复用差异引擎（预览/差异/ZIP 同一个渲染源），
     * onlyChanged=false 才能拿到「未变」计数，否则汇总永远缺一块。
     */
    private GenImpactVO impactOf(List<Long> ids) {
        GenDiffDTO diffDto = new GenDiffDTO();
        diffDto.setIds(ids);
        diffDto.setOverwrite(Boolean.TRUE);
        diffDto.setOnlyChanged(Boolean.FALSE);
        List<GenDiffVO> diffs = genCodeService.diff(diffDto);
        GenImpactVO impact = new GenImpactVO();
        impact.setTotal(diffs.size());
        for (GenDiffVO d : diffs) {
            GenImpactVO.File f = new GenImpactVO.File();
            f.setTableName(d.getTableName());
            f.setPath(d.getPath());
            f.setStatus(d.getStatus());
            f.setProtectedFile(Boolean.TRUE.equals(d.getProtectedFile()));
            f.setAdditions(d.getAdditions());
            f.setDeletions(d.getDeletions());
            impact.getFiles().add(f);
            switch (String.valueOf(d.getStatus())) {
                case "NEW" -> impact.setNewCount(impact.getNewCount() + 1);
                case "MODIFIED" -> impact.setModifiedCount(impact.getModifiedCount() + 1);
                default -> impact.setUnchangedCount(impact.getUnchangedCount() + 1);
            }
            if (f.isProtectedFile()) {
                impact.setProtectedCount(impact.getProtectedCount() + 1);
            }
        }
        return impact;
    }

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
        if (ids == null || ids.isEmpty()) {
            return outputProperties.getBackendRoot();
        }
        // 一次查回再按勾选顺序找第一个配了 backendRoot 的表：
        // 循环里 selectById 会让「批量应用 30 张表」变成 30 次往返
        Map<Long, GenTable> byId = new LinkedHashMap<>();
        for (GenTable table : genTableMapper.selectBatchIds(ids)) {
            byId.put(table.getId(), table);
        }
        for (Long id : ids) {
            GenTable table = byId.get(id);
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

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long parseLong(String value) {
        try {
            return StringUtils.hasText(value) ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long elapsed(long from) {
        return System.currentTimeMillis() - from;
    }
}
