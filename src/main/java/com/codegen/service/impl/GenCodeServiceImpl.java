package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenDiffDTO;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.dto.GenWriteDTO;
import com.codegen.entity.GenOutput;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.entity.SysProject;
import com.codegen.mapper.GenOutputMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.monitor.MonitorMetrics;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenDriftService;
import com.codegen.service.GenSqlDeliverService;
import com.codegen.service.I18nTextRewriter;
import com.codegen.service.GenTableColumnService;
import com.codegen.support.GenPathResolver;
import com.codegen.util.GenDigestUtils;
import com.codegen.util.GenMergeUtils;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import com.codegen.vo.GenDiffVO;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenSqlDeliverVO;
import com.codegen.vo.GenWriteVO;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.Timer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成服务：ZIP 内存打包 / 预览 / 写盘（不落服务器临时文件）
 */
@Service
public class GenCodeServiceImpl implements GenCodeService {

    private static final List<String> INFO_FILES = List.of("router-snippet.txt", "README-使用说明.txt");

    private static final Logger log = LoggerFactory.getLogger(GenCodeServiceImpl.class);

    /**
     * 本次写盘中台账记录失败的条数（V4.2）。
     *
     * <p>写盘仍算成功——文件已经落盘，因为台账失败去回滚它反而更糟；但必须让调用方看得见，
     * 否则就是静默丢账。用 ThreadLocal 是因为 Service 是单例，计数不能跨请求串。
     */
    private static final ThreadLocal<Integer> LEDGER_ERRORS = ThreadLocal.withInitial(() -> 0);

    /**
     * V7.3 P0-2：本次写盘的 SQL 交付物结果（按系统分组）。与台账计数同样的单例隔离理由。
     */
    private static final ThreadLocal<List<GenSqlDeliverVO>> SQL_DELIVERIES =
            ThreadLocal.withInitial(ArrayList::new);

    @Autowired
    private GenSqlDeliverService sqlDeliverService;

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final DateTimeFormatter DIFF_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenOutputMapper genOutputMapper;

    @Autowired
    private GenTableColumnService columnService;

    @Autowired
    private GenTemplateEngine genTemplateEngine;

    @Autowired
    private GenOutputProperties outputProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysProjectMapper projectMapper;

    @Autowired
    private GenDriftService genDriftService;

    /** V6.10：可观测性指标采集器（写盘耗时 / 各状态文件数 / 管线步骤耗时）。 */
    @Autowired
    private MonitorMetrics monitorMetrics;

    /**
     * 单次生成内的系统配置缓存（projectId -> 系统）。
     * clearProjectCache() 在每次生成入口清空，避免用户在管理页改完配置后
     * 同一次进程生命周期里一直读到旧值。
     */
    private final Map<Long, SysProject> projectCache = new HashMap<>();

    /**
     * 渲染结果：表配置 + 相对路径 -> 内容
     */
    private record TableFiles(GenTable table, Map<String, String> files) {
    }

    @Override
    public GenZipResult generateZip(GenDownloadDTO dto) {
        List<TableFiles> rendered = renderTables(dto == null ? null : dto.getIds());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<String> files;
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            // 全量条目（含共享文件），manifest 基于它生成（F15）
            Map<String, String> all = new LinkedHashMap<>();
            boolean snippetWritten = false;
            for (TableFiles tf : rendered) {
                for (Map.Entry<String, String> entry : tf.files().entrySet()) {
                    String path = sanitize(tf.table().getTableName(), entry.getKey());
                    all.putIfAbsent(path, entry.getValue());
                }
                if (!snippetWritten) {
                    all.putIfAbsent("router-snippet.txt",
                            "// 路由注册片段（追加到 src/router/index.js 对应菜单 children）\n"
                                    // V4.5 打印路由的挂载条件依赖系统默认配置，必须带 project 渲染
                                    + genTemplateEngine.renderRouterSnippet(tf.table(), projectConfigOf(tf.table())) + "\n");
                    snippetWritten = true;
                }
            }
            if (all.isEmpty()) {
                throw new BusinessException(404, "表配置不存在，无法生成");
            }
            // 共享文件（readme）：以 ZIP 首表解析表级覆盖，无覆盖回落全局；
            // V4.5 起传首表模型，readme 才能说明本 ZIP 是否含打印页
            for (Map.Entry<String, String> entry : genTemplateEngine.renderShared(
                    rendered.get(0).table(), projectConfigOf(rendered.get(0).table())).entrySet()) {
                all.putIfAbsent(entry.getKey(), entry.getValue());
            }
            // V7.3 P0-2：下载出口也带 SQL 交付物，与写盘/流水线同一套内容（db/ 下三个文件）
            addSqlEntries(all, rendered.get(0).table(), dto == null ? null : dto.getIds());
            for (Map.Entry<String, String> entry : all.entrySet()) {
                writeZipEntry(zip, entry.getKey(), entry.getValue());
            }
            // manifest.txt：逐文件 路径 + 字节数 + MD5（排除自身）
            StringBuilder manifest = new StringBuilder();
            for (Map.Entry<String, String> entry : all.entrySet()) {
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                manifest.append(entry.getKey()).append('\t').append(bytes.length).append('\t')
                        .append(GenDigestUtils.md5Hex(bytes)).append('\n');
            }
            writeZipEntry(zip, "manifest.txt", manifest.toString());
            // X-Gen-Files 清单与 ZIP 写入顺序一致，manifest.txt 计入
            files = new ArrayList<>(all.keySet());
            files.add("manifest.txt");
            // 注意：不能在 try 块内 return bos.toByteArray()——return 表达式先于
            // try-with-resources 的 close() 求值，EOCD（central directory）还没写，
            // 产物是截断 ZIP（V6.2 CLI 首次暴露，V5.0 起潜伏）
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "ZIP 打包失败：" + e.getMessage());
        }
        // 走到这里 close() 已完成（EOCD 已写入），此刻取字节才是完整 ZIP
        return new GenZipResult(bos.toByteArray(), files);
    }

    @Override
    public List<GenFileVO> preview(GenDownloadDTO dto) {
        List<TableFiles> rendered = renderTables(dto == null ? null : dto.getIds());
        List<GenFileVO> result = new ArrayList<>();
        for (TableFiles tf : rendered) {
            for (Map.Entry<String, String> entry : tf.files().entrySet()) {
                result.add(new GenFileVO(tf.table().getTableName(), entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    @Override
    public List<GenWriteVO> writeToDisk(GenWriteDTO dto) {
        // 直调写盘也生成批次号：三入口（直调 / apply / 向导）统一记 gen_output 台账，且天然可回滚
        return writeToDisk(dto, "GEN" + LocalDateTime.now().format(BATCH_FORMAT));
    }

    @Override
    public List<GenWriteVO> writeToDisk(GenWriteDTO dto, String batchNo) {
        // V6.10：写盘是「最会被追问」的慢点，记录耗时 + 各状态文件数
        Timer.Sample sample = monitorMetrics.start();
        boolean success = false;
        List<GenWriteVO> results = new ArrayList<>();
        try {
            LEDGER_ERRORS.set(0);
            SQL_DELIVERIES.set(new ArrayList<>());
            // 与 renderTables 同口径：写盘前清一次系统配置缓存，
            // 否则管理页改完 sys_project（root/path 语义修正）后直调写盘仍用旧配置（坑 51）
            clearProjectCache();
            List<Long> ids = dto == null ? null : dto.getIds();
            if (ids == null || ids.isEmpty()) {
                throw new BusinessException(400, "请先勾选要生成的表");
            }
            boolean overwrite = Boolean.TRUE.equals(dto.getOverwrite());
            boolean skipDrifted = Boolean.TRUE.equals(dto.getSkipDrifted());
            // V6.8：受保护文件（既有 + 无台账快照）默认跳过，只有显式 force 才覆盖
            boolean force = Boolean.TRUE.equals(dto.getForce());
            // 台账只取一次：逐文件查库会把「一个批次几十个文件」放大成几十次往返，
            // 而且同一批次内台账不会变，缓存一份没有一致性风险
            Map<String, GenDriftService.LedgerSnapshot> ledger = genDriftService.latestByPath();
            Map<Long, List<Long>> idsByProject = new LinkedHashMap<>();
            for (GenTable table : loadTablesInOrder(ids)) {
                if (table.getProjectId() != null) {
                    idsByProject.computeIfAbsent(table.getProjectId(), k -> new ArrayList<>()).add(table.getId());
                }
                int before = results.size();
                results.addAll(writeTable(table, overwrite, skipDrifted, force, batchNo, ledger));
                // 只看本表新增的结果：用累计 results 判断会让「第一张表写过」之后
                // 每张表都被打上生成时间，last_gen_time 直接失去意义
                boolean wrote = results.subList(before, results.size()).stream()
                        .anyMatch(r -> "WRITTEN".equals(r.getStatus()) || "MERGED".equals(r.getStatus()));
                if (wrote) {
                    jdbcTemplate.update("UPDATE gen_table SET last_gen_time = NOW(), last_gen_by = ? WHERE id = ?",
                            UserContext.getUserId(), table.getId());
                }
            }
            if (results.isEmpty()) {
                throw new BusinessException(404, "表配置不存在，无法写盘");
            }
            results.forEach(r -> r.setBatchNo(batchNo));
            // V7.3 P0-2：写盘出口同步交付 SQL（db/init_tables.sql、init_seed.sql、init_menu.sql），
            // 失败只记录不抛出——代码已经写好了，交付物失败不应该让整个写盘回滚。
            deliverSqlForProjects(idsByProject, batchNo);
            success = true;
            return results;
        } finally {
            // 文件数从结果集统计，不额外遍历磁盘；异常的批次也会留下 fail 样本
            Map<String, Integer> statusCounts = new HashMap<>();
            for (GenWriteVO r : results) {
                if (r.getStatus() != null) {
                    statusCounts.merge(r.getStatus(), 1, Integer::sum);
                }
            }
            monitorMetrics.finishWrite(sample, success, statusCounts);
        }
    }

    @Override
    public List<GenSqlDeliverVO> lastSqlDeliveries() {
        return new ArrayList<>(SQL_DELIVERIES.get());
    }

    /**
     * 按系统逐组交付 SQL；单个系统失败不影响其它系统，也不影响写盘结果。
     */
    private void deliverSqlForProjects(Map<Long, List<Long>> idsByProject, String batchNo) {
        for (Map.Entry<Long, List<Long>> entry : idsByProject.entrySet()) {
            try {
                List<GenSqlDeliverService.SqlArtifact> artifacts =
                        sqlDeliverService.buildProjectArtifacts(entry.getKey(), entry.getValue());
                SQL_DELIVERIES.get().add(sqlDeliverService.deliver(entry.getKey(), batchNo, artifacts));
            } catch (Exception e) {
                log.warn("SQL 交付物生成失败 projectId={}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * ZIP 出口：SQL 交付物以 {@code db/} 前缀追加到 manifest（首表所属系统为本包的系统）。
     */
    private void addSqlEntries(Map<String, String> all, GenTable first, List<Long> ids) {
        if (first == null || first.getProjectId() == null || ids == null || ids.isEmpty()) {
            return;
        }
        try {
            for (GenSqlDeliverService.SqlArtifact artifact
                    : sqlDeliverService.buildProjectArtifacts(first.getProjectId(), ids)) {
                all.putIfAbsent("db/" + artifact.fileName(), artifact.content());
            }
        } catch (Exception e) {
            log.warn("ZIP 追加 SQL 交付物失败 projectId={}: {}", first.getProjectId(), e.getMessage());
        }
    }

    /**
     * 差异预览：渲染结果 vs 磁盘现状，逐文件给出行级 diff
     */
    @Override
    public List<GenDiffVO> diff(GenDiffDTO dto) {
        List<Long> ids = dto == null ? null : dto.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要对比的表");
        }
        boolean onlyChanged = dto.getOnlyChanged() == null || Boolean.TRUE.equals(dto.getOnlyChanged());
        // overwrite 不参与差异计算：差异是「渲染结果 vs 磁盘现状」的事实，
        // 写不写由策略决定（见 GenDiffDTO#overwrite 注释）。保留参数只为兼容既有调用。
        // 漂移同理：它是「磁盘 vs 上次生成」的事实，不因用户选了哪个策略而变。
        Map<String, GenDriftService.LedgerSnapshot> ledger = genDriftService.latestByPath();
        List<GenDiffVO> result = new ArrayList<>();
        for (GenTable table : loadTablesInOrder(ids)) {
            // 同 writeTable：先解析目标模块，保证后端文件判定用的是最终输出路径
            applyOutputDefaults(table);
            String backendBase = backendBaseOf(table);
            for (Map.Entry<String, String> entry : renderOne(table).entrySet()) {                String relPath = entry.getKey();
                if (INFO_FILES.contains(relPath)) {
                    continue;
                }
                // V5.5 多模块：模块目录前缀 <stem>-domain/ 在 relPath 最前面，backendBase 段
                // 不再顶头，改 contains 判定（前端路径不会含 backendBase 包路径段）
                // V6.5 移动端：relPath 前缀 mobile/ = 第三侧 MOBILE
                boolean isMobile = relPath.startsWith("mobile/");
                boolean isBackend = !isMobile && GenPathResolver.isBackendRelPath(relPath, backendBase);
                String root = isBackend ? backendRootOf(table)
                        : isMobile ? mobileRootOf(table) : frontendRootOf(table);
                if (!StringUtils.hasText(root)) {
                    continue;
                }
                Path rootPath = Paths.get(root).toAbsolutePath().normalize();
                if (!isBackend && !isMobile) {
                    // 前端产物路径形如 src/api/x.js，可能叠加 gen.output.frontend-path 子目录
                    rootPath = frontendBaseOf(table, rootPath);
                }
                Path target = rootPath.resolve(relPath).normalize();
                if (!target.startsWith(rootPath)) {
                    continue;
                }
                GenDiffVO vo = new GenDiffVO();
                vo.setTableName(table.getTableName());
                vo.setPath(relPath);
                vo.setAbsPath(target.toString());
                vo.setSide(isBackend ? "BACKEND" : isMobile ? "MOBILE" : "FRONTEND");
                String oldContent = null;
                String actualMd5 = null;
                if (Files.exists(target)) {
                    try {
                        // 一次读字节，两用：算漂移指纹 + 解成文本做行级 diff。
                        // 分成两次读会在中间被别的进程改动时出现「指纹和内容不是同一版」的怪结果。
                        byte[] raw = Files.readAllBytes(target);
                        actualMd5 = GenDigestUtils.md5Hex(raw);
                        oldContent = new String(raw, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        oldContent = null;
                    }
                }
                GenDriftService.LedgerSnapshot snap = ledger.get(target.toString());
                vo.setDrift(genDriftService.judge(snap == null ? null : snap.md5(), actualMd5));
                if (snap != null) {
                    vo.setLastModified(lastModifiedOf(target));
                }
                String newContent = entry.getValue();
                List<GenMergeUtils.DiffLine> lines = GenMergeUtils.diff(
                        oldContent == null ? "" : oldContent, newContent);
                int[] stats = GenMergeUtils.stats(lines);
                vo.setAdditions(stats[0]);
                vo.setDeletions(stats[1]);
                boolean isNew = oldContent == null;
                // status 只描述内容事实：这个文件是不是新的、内容有没有变。
                // 「会不会被写入」取决于用户在第③步选的 overwrite 策略，由调用方推导，
                // 不能混进 status——同一份差异不该因策略不同而变脸。
                vo.setStatus(isNew ? "NEW" : (stats[0] + stats[1] == 0 ? "UNCHANGED" : "MODIFIED"));
                // V6.8：既有文件但台账无快照 → 无法判断是否手改过，按受保护处理（与写盘侧同一判定）。
                // 内容一致的文件无需写盘，不算受保护，否则第二次 dryRun 会冒出无意义的「受保护 N」。
                vo.setProtectedFile(!isNew && snap == null && stats[0] + stats[1] > 0);
                if (onlyChanged && "UNCHANGED".equals(vo.getStatus())) {
                    continue;
                }
                List<GenDiffVO.Line> lineVOs = new ArrayList<>();
                for (GenMergeUtils.DiffLine line : lines) {
                    lineVOs.add(new GenDiffVO.Line(line.type(), line.oldLine(), line.newLine(), line.text()));
                }
                vo.setLines(lineVOs);
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 单表写盘：后端文件 -> backend_root，前端文件 -> frontend_root
     *
     * @param batchNo 非空时，覆盖前先备份到 {@code <root>/.gen-backup/<batchNo>/} 并把
     *                备份路径、覆盖前 md5 一并记入 {@code gen_output} 台账
     */
    private List<GenWriteVO> writeTable(GenTable table, boolean overwrite, boolean skipDrifted, boolean force,
                                        String batchNo,
                                        Map<String, GenDriftService.LedgerSnapshot> ledger) {
        // 先解析目标模块，否则 backendBase 还是库里的旧值，后端文件会被误判成前端文件
        applyOutputDefaults(table);
        String backendBase = backendBaseOf(table);
        List<GenWriteVO> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : renderOne(table).entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();
            if (INFO_FILES.contains(relPath)) {
                continue;
            }

            try {
                // V5.5 多模块：模块目录前缀在 relPath 最前面，同 diffTable 改 contains 判定
                // V6.5 移动端：relPath 前缀 mobile/ = 第三侧 MOBILE，落 frontendModule-mobile 工程
                boolean isMobile = relPath.startsWith("mobile/");
                boolean isBackend = !isMobile && GenPathResolver.isBackendRelPath(relPath, backendBase);
                String group = isBackend ? "backend" : isMobile ? "mobile" : "frontend";
                String root = isBackend ? backendRootOf(table)
                        : isMobile ? mobileRootOf(table) : frontendRootOf(table);
                if (!StringUtils.hasText(root)) {
                    String msg = (isBackend ? "后端" : isMobile ? "移动端" : "前端") + "根目录未配置：请在 gen.output."
                            + (isBackend ? "backend-root" : "frontend-root") + " 或表配置中补充";
                    recordOutput(batchNo, table, group, relPath, "", "ERROR", 0, "", msg, null, null);
                    results.add(new GenWriteVO(table.getTableName(), relPath, "ERROR", msg));
                    continue;
                }
                Path rootPath = Paths.get(root).toAbsolutePath().normalize();
                if (!isBackend && !isMobile) {
                    // 前端产物路径形如 src/api/x.js，可能叠加 gen.output.frontend-path 子目录
                    rootPath = frontendBaseOf(table, rootPath);
                }
                Path target = rootPath.resolve(relPath).normalize();
                if (!target.startsWith(rootPath)) {
                    recordOutput(batchNo, table, group, relPath, "", "ERROR", 0, "", "非法目标路径（越界）", null, null);
                    results.add(new GenWriteVO(table.getTableName(), relPath, "ERROR", "非法目标路径（越界）"));
                    continue;
                }
                boolean exists = Files.exists(target);
                if (exists && !overwrite) {
                    recordOutput(batchNo, table, group, relPath, target.toString(), "SKIPPED", 0, "",
                            "文件已存在，未勾选覆盖", null, null);
                    results.add(new GenWriteVO(table.getTableName(), relPath, "SKIPPED", "文件已存在，未勾选覆盖"));
                    continue;
                }
                // 覆盖前读一次原内容：备份、prevMd5、块合并、漂移判断都用这一份。
                // 分开读会有「判断看到的是 A 版、合并用的是 B 版」的窗口。
                byte[] originalBytes = exists ? Files.readAllBytes(target) : null;
                String oldContent = originalBytes == null ? null : new String(originalBytes, StandardCharsets.UTF_8);
                String currentMd5 = originalBytes == null ? null : GenDigestUtils.md5Hex(originalBytes);
                // 只写未漂移：磁盘已被手改则让路，避免把用户块外的逻辑覆盖掉
                if (exists && skipDrifted) {
                    GenDriftService.LedgerSnapshot snap = ledger.get(target.toString());
                    String drift = genDriftService.judge(snap == null ? null : snap.md5(), currentMd5);
                    if (GenDriftService.DRIFTED.equals(drift)) {
                        String msg = "磁盘内容已手改（漂移），按「只写未漂移」策略跳过";
                        recordOutput(batchNo, table, group, relPath, target.toString(), "SKIPPED", 0, "",
                                msg, null, currentMd5);
                        results.add(new GenWriteVO(table.getTableName(), relPath, "SKIPPED", msg));
                        continue;
                    }
                }
                // V6.8 受保护跳过：文件已存在但没有台账快照，说明它可能是用户手写的，
                // 也可能台账丢过——两种情况都不该静默覆盖。要覆盖必须显式 force。
                if (exists && overwrite && !force) {
                    GenDriftService.LedgerSnapshot snap = ledger.get(target.toString());
                    if (snap == null) {
                        String msg = "既有文件且无台账快照（视为手改资产），按受保护跳过；确认要覆盖请传 force=true";
                        recordOutput(batchNo, table, group, relPath, target.toString(), "SKIPPED", 0, "",
                                msg, null, currentMd5);
                        results.add(new GenWriteVO(table.getTableName(), relPath, "SKIPPED", msg));
                        continue;
                    }
                }
                // 覆盖前备份：只备份真正会被覆盖的已有文件
                String backupPath = exists ? backup(rootPath, batchNo, relPath, target) : null;
                String finalContent = content;
                String status = "WRITTEN";
                String message = target.toString();
                // 覆盖前原文件 md5（V4.2）：prevMd5 是「回滚后应该长什么样」的凭据，
                // 必须在写入之前算——写完再算就是新内容了
                String prevMd5 = currentMd5;
                if (exists) {
                    // 标记块合并：块内替换、块外（手写代码）原样保留
                    GenMergeUtils.MergeResult merged = GenMergeUtils.merge(oldContent, content);
                    finalContent = merged.content();
                    if ("MERGED".equals(merged.mode())) {
                        status = "MERGED";
                        message = target + "（合并块：" + String.join(",", merged.keys()) + "）";
                    }
                }
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, finalContent, StandardCharsets.UTF_8);
                // 文件级台账（F13）：动作粒度比 VO 状态更细——覆盖已有文件记 OVERWRITTEN
                byte[] writtenBytes = finalContent.getBytes(StandardCharsets.UTF_8);
                boolean merged = "MERGED".equals(status);
                String action = merged ? "MERGED" : (exists ? "OVERWRITTEN" : "WRITTEN");
                recordOutput(batchNo, table, group, relPath, target.toString(),
                        action, writtenBytes.length, GenDigestUtils.md5Hex(writtenBytes), merged ? message : "",
                        backupPath, prevMd5);
                results.add(new GenWriteVO(table.getTableName(), relPath, status, message, backupPath));
            } catch (Exception e) {
                recordOutput(batchNo, table, relPath.startsWith("mobile/") ? "mobile"
                                : relPath.contains(backendBase + "/") ? "backend" : "frontend",
                        relPath, "", "ERROR", 0, "", e.getMessage(), null, null);
                results.add(new GenWriteVO(table.getTableName(), relPath, "ERROR", e.getMessage()));
            }
        }
        return results;
    }

    /**
     * 备份到 {@code <root>/.gen-backup/<batchNo>/<relPath>}，失败不阻断写盘（返回 null）
     */
    private String backup(Path rootPath, String batchNo, String relPath, Path target) {
        if (!StringUtils.hasText(batchNo)) {
            return null;
        }
        try {
            Path backupRoot = rootPath.resolve(".gen-backup").resolve(batchNo);
            Path dest = backupRoot.resolve(relPath).normalize();
            if (!dest.startsWith(backupRoot)) {
                return null;
            }
            if (dest.getParent() != null) {
                Files.createDirectories(dest.getParent());
            }
            Files.copy(target, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 台账合一（V4.2）：不再写 {@code gen_history}。
     *
     * <p>原来同一个事实写两张表：{@code gen_history} 有 backup_path 没 project_id/md5，
     * {@code gen_output} 有 project_id/md5 没 backup_path，两表 collation 还不一致（连 UNION 都报 1271）。
     * 字段互补的双实现迟早对不上，所以回滚/漂移要用的列全部并入 {@code gen_output}，
     * 这里只留一个写入口。历史数据已由 {@code V4.2__unify_gen_ledger.sql} 回填，
     * {@code gen_history} 保留只读一个版本，下一版删除。
     */

    private String operator() {
        String name = UserContext.getUsername();
        if (StringUtils.hasText(name)) {
            return name;
        }
        Long userId = UserContext.getUserId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    /**
     * 写盘文件级台账（F13 / V4.2）：逐文件一行，失败不影响写盘主流程，但不再静默。
     *
     * <p>V4.2 之前这里 {@code catch (Exception e) { }} 全吞，台账写失败时接口返回「成功」、
     * 库里却没有记录，等于静默丢账。现在改为 {@code log.warn} + 计数，由调用方放进响应头
     * {@code X-Gen-Ledger-Errors}，写盘本身仍不因台账失败而失败（文件已经落盘，回滚它更糟）。
     *
     * @param backupPath 覆盖前原文件备份路径，无备份传 null
     * @param prevMd5    覆盖前原文件 MD5，无原文件传 null
     */
    private void recordOutput(String batchNo, GenTable table, String fileGroup, String relPath,
                              String absPath, String action, long fileSize, String md5,
                              String message, String backupPath, String prevMd5) {
        try {
            GenOutput row = new GenOutput();
            row.setBatchNo(StringUtils.hasText(batchNo) ? batchNo : "");
            row.setTableId(table.getId());
            row.setTableName(table.getTableName());
            // 记下当时的所属系统：表后续被迁到别的系统时，历史台账仍应反映生成当时的归属
            row.setProjectId(table.getProjectId());
            row.setFileGroup(fileGroup);
            row.setRelPath(relPath);
            row.setAbsPath(StringUtils.hasText(absPath) ? absPath : "");
            row.setAction(action);
            row.setFileSize(fileSize);
            row.setMd5(StringUtils.hasText(md5) ? md5 : "");
            row.setMessage(message == null ? "" : message);
            row.setOperator(operator());
            row.setBackupPath(backupPath);
            row.setPrevMd5(prevMd5);
            genOutputMapper.insert(row);
        } catch (Exception e) {
            LEDGER_ERRORS.set(LEDGER_ERRORS.get() + 1);
            log.warn("写盘台账记录失败（文件已落盘，不影响写盘结果）：batch={} path={} action={} 原因={}",
                    batchNo, relPath, action, e.getMessage());
        }
    }

    @Override
    public int ledgerErrorCount() {
        return LEDGER_ERRORS.get();
    }

    /**
     * 磁盘文件最后修改时间（只在有台账时附带，供用户判断手改发生在什么时候）
     */
    private String lastModifiedOf(Path target) {
        try {
            return DIFF_TIME_FORMAT.format(Files.getLastModifiedTime(target).toInstant()
                    .atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 渲染多表（含校验）：列配置按 {@code IN (ids)} 一次查完，避免每表一次 SQL
     */
    private List<TableFiles> renderTables(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要生成的表");
        }
        // 每个批次开始时清一次系统缓存，保证读到的是管理页最新的配置
        clearProjectCache();
        List<GenTable> tables = loadTablesInOrder(ids);
        if (tables.size() != ids.size()) {
            throw new BusinessException(404, "表配置不存在，无法生成");
        }
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(GenTableColumn::getTableId, ids);
        wrapper.orderByAsc(GenTableColumn::getTableId).orderByAsc(GenTableColumn::getSort);
        Map<Long, List<GenTableColumn>> columnsByTable = new HashMap<>();
        for (GenTableColumn col : columnService.list(wrapper)) {
            columnsByTable.computeIfAbsent(col.getTableId(), k -> new ArrayList<>()).add(col);
        }
        List<TableFiles> rendered = new ArrayList<>();
        for (GenTable table : tables) {
            applyOutputDefaults(table);
            List<GenTableColumn> cols = columnsByTable.getOrDefault(table.getId(), List.of());
            autoDetectRefs(table, cols);
            rendered.add(new TableFiles(table,
                    rewriteForI18n(table,
                            genTemplateEngine.renderWithProject(table, cols, projectConfigOf(table)))));
        }
        return rendered;
    }

    /**
     * 按入参顺序批量取表配置。
     *
     * <p>逐个 {@code selectById} 会把「勾 30 张表」放大成 30 次数据库往返，
     * 而这三次调用（写盘 / 差异 / 渲染）本来就是同一批 id。
     * 不能直接用 {@code selectBatchIds} 的返回顺序：它按主键排序，
     * 而下游产出文件、台账、回滚都按用户勾选顺序，顺序变了对不上。
     *
     * <p>缺失的 id 直接跳过——调用方按需要自行判断（写盘忽略、渲染报 404）。
     */
    private List<GenTable> loadTablesInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, GenTable> byId = new HashMap<>();
        for (GenTable table : genTableMapper.selectBatchIds(ids)) {
            byId.put(table.getId(), table);
        }
        List<GenTable> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            GenTable table = byId.get(id);
            if (table != null) {
                ordered.add(table);
            }
        }
        return ordered;
    }

    /**
     * 把系统配置转成模板层可用的轻量 record。
     *
     * <p>分两个字段传而不是一律用 rootPackage：
     * {@code packageName} 是业务包（如 com.demo.order），根包（如 com.demo）只用于
     * import 基座类。两者混用会让生成的 entity 跑到错误的包下。
     * 系统上没有单独的「业务默认包」概念，所以回落顺序是：
     * 系统 rootPackage → 全局 packageName（GenTemplateModel 内部处理）。
     */
    private GenTemplateModel.ProjectConfig projectConfigOf(GenTable table) {
        SysProject project = projectOf(table);
        if (project == null) {
            return null;
        }
        return new GenTemplateModel.ProjectConfig(
                project.getRootPackage(), project.getGatewayPrefix(),
                project.getRootPackage(), project.getProjectCode(),
                project.getDefaultDeleteCascadeMode(), project.getDefaultGenPrint(),
                project.getProjectStructure(), project.getPayEnabled(), project.getMobileEnabled(),
                project.getFileEnabled(), project.getTestEnabled(), project.getDefaultLanguage(),
                project.getI18nEnabled());
    }

    /**
     * 渲染单表文件
     */
    private Map<String, String> renderOne(GenTable table) {
        applyOutputDefaults(table);
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTableColumn::getTableId, table.getId());
        wrapper.orderByAsc(GenTableColumn::getSort);
        List<GenTableColumn> cols = columnService.list(wrapper);
        autoDetectRefs(table, cols);
        // V8.0：渲染结果统一过文案重写器；开关关闭时原样返回（存量产物逐字节零 diff）。
        // 放在 renderOne 出口，写盘 / 预览 / 差异 / 下载四条路径自动一致。
        return rewriteForI18n(table, genTemplateEngine.renderWithProject(table, cols, projectConfigOf(table)));
    }

    /** V8.0 单一判定点：读系统台账的 i18n 开关，回落实现在 GenTemplateModel.resolveI18n */
    private Map<String, String> rewriteForI18n(GenTable table, Map<String, String> files) {
        SysProject project = projectOf(table);
        boolean on = project != null && GenTemplateModel.resolveI18n(project.getI18nEnabled());
        return I18nTextRewriter.rewrite(files, on);
    }

    /**
     * 外键列自动识别：{@code xxx_id} 且没配 ref_table 的列，按同库同名表猜一个关联表 + 名称列。
     *
     * <p>为什么必须要有这一步：真实业务表的关联列往往是裸 {@code product_id}，
     * 没人会手工给几十张表逐个配 ref_table。不识别的话列表页只能显示一串数字 ID，
     * 用户根本看不出这行是哪个产品/哪个客户。</p>
     *
     * <p>只在本次渲染生效（不落库）：自动推导会有猜错的可能，
     * 猜错了用户在表配置里显式填 ref_table 即可覆盖，不需要先擦掉一份错误的落库结果。</p>
     */
    private void autoDetectRefs(GenTable table, List<GenTableColumn> cols) {
        if (table == null || cols == null || cols.isEmpty()) {
            return;
        }
        for (GenTableColumn col : cols) {
            if (col.getRefTable() != null && !col.getRefTable().isBlank()) {
                continue;
            }
            String column = col.getColumnName() == null ? "" : col.getColumnName().toLowerCase(Locale.ROOT);
            if (!column.endsWith("_id") || "id".equals(column)) {
                continue;
            }
            String base = column.substring(0, column.length() - 3);
            if (base.isBlank()) {
                continue;
            }
            String refTable = findRefTable(table.getTableName(), base);
            if (refTable == null) {
                continue;
            }
            col.setRefTable(refTable);
            col.setRefLabel(findRefLabel(refTable, base));
        }
    }

    /**
     * 找关联表：先精确同名，再找 {@code xxx_product} 这类带业务前缀的表，最后试复数形式。
     * 同名表自己排除掉，否则 {@code parent_id} 会关联到自己。
     */
    private String findRefTable(String self, String base) {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name <> ? AND (table_name = ? OR table_name = ? OR table_name LIKE ?) "
                + "ORDER BY LENGTH(table_name), table_name LIMIT 1";
        try {
            List<String> found = jdbcTemplate.queryForList(sql, String.class,
                    self, base, base + "s", "%\\_" + base);
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 找名称列：优先 name，其次 {@code xxx_name}、title、code。
     * 都找不到返回 null，模板会退回显示原始 ID。
     */
    private String findRefLabel(String refTable, String base) {
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = ? AND column_name IN ('name', ?, 'title', 'code', ?) "
                + "ORDER BY FIELD(column_name, 'name', ?, 'title', 'code', ?) LIMIT 1";
        try {
            List<String> found = jdbcTemplate.queryForList(sql, String.class,
                    refTable, base + "_name", base + "_code", base + "_name", base + "_code");
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析输出参数（不落库，仅本次渲染生效）。
     * 优先级：目标模块（gen.output.modules[targetModule]） &gt; 表级配置 &gt; 全局默认。
     * 目标模块排最前，是因为从库里导入表时 backend-path/package-name 会被回填成默认模块的值，
     * 此时只有让模块覆盖，才能把同一套生成器指到 product / order 等其它微服务。
     */
    private void applyOutputDefaults(GenTable table) {
        // V3.0 多系统：系统配置排在最前，作为「表级为空时」的兜底来源。
        // 注意顺序——表级字段前面已由用户配置，这里只在为空时补，不覆盖。
        // 目标模块（yml 里的 modules）仍然优先于系统：它是更细的一层。
        SysProject project = projectOf(table);
        if (project != null) {
            if (!StringUtils.hasText(table.getBackendPath()) && StringUtils.hasText(project.getBackendPath())) {
                table.setBackendPath(project.getBackendPath());
            }
            if (!StringUtils.hasText(table.getPackageName()) && StringUtils.hasText(project.getRootPackage())) {
                table.setPackageName(project.getRootPackage());
            }
            if (!StringUtils.hasText(table.getGatewayPrefix()) && StringUtils.hasText(project.getGatewayPrefix())) {
                table.setGatewayPrefix(project.getGatewayPrefix());
            }
            // 注意：backendRoot / frontendRoot 不在这里回填。
            // 这两个字段只服务于落盘路径，模板根本不读，回填进实体只会让
            // 「表级为空 → 系统层生效」这条链看不出来（V3.0 的写盘 bug 就藏在这）。
            // 落盘时统一走 GenPathResolver，那里能看到真实来源。
        }

        GenOutputProperties.ModuleConfig module = moduleOf(table);
        if (module != null) {
            if (StringUtils.hasText(module.getBackendPath())) {
                table.setBackendPath(module.getBackendPath());
            }
            if (StringUtils.hasText(module.getPackageName())) {
                table.setPackageName(module.getPackageName());
            }
            if (StringUtils.hasText(module.getGatewayPrefix())) {
                table.setGatewayPrefix(module.getGatewayPrefix());
            }
        }
        if (!StringUtils.hasText(table.getBackendPath()) && StringUtils.hasText(outputProperties.getBackendPath())) {
            table.setBackendPath(outputProperties.getBackendPath());
        }
        if (!StringUtils.hasText(table.getPackageName()) && StringUtils.hasText(outputProperties.getPackageName())) {
            table.setPackageName(outputProperties.getPackageName());
        }
        if (!StringUtils.hasText(table.getGatewayPrefix()) && StringUtils.hasText(outputProperties.getGatewayPrefix())) {
            table.setGatewayPrefix(outputProperties.getGatewayPrefix());
        }
    }

    /**
     * 表所属的系统配置。
     *
     * <p>批量生成时同一系统会被查很多次，用请求内的 Map 缓存挡住：
     * 一个批次通常几十张表，直查会变成几十次重复 select。
     * 缓存是实例级而非静态，跟着 Bean 生命周期走，不跨请求残留脏数据。
     */
    private SysProject projectOf(GenTable table) {
        Long projectId = table.getProjectId();
        if (projectId == null) {
            // 存量表未迁移：返回 null，后面自然回落到全局 yml
            return null;
        }
        // 不用 computeIfAbsent：系统被删时 selectById 返回 null，
        // computeIfAbsent 不缓存 null，会把每次调用都打成一次查库
        if (projectCache.containsKey(projectId)) {
            return projectCache.get(projectId);
        }
        SysProject project = projectMapper.selectById(projectId);
        projectCache.put(projectId, project);
        return project;
    }

    /**
     * 清空系统配置缓存。生成入口先调一次，保证拿到的是最新配置。
     */
    private void clearProjectCache() {
        projectCache.clear();
    }

    /**
     * 表配置的目标模块（gen_table.target_module），未配置或未命中返回 null
     */
    private GenOutputProperties.ModuleConfig moduleOf(GenTable table) {
        return GenPathResolver.moduleOf(table, outputProperties);
    }

    /**
     * 后端文件相对路径前缀，回落链见 {@link GenPathResolver#backendBase}。
     *
     * <p>现在表级字段已不再被 applyOutputDefaults 回填，所以这里必须自己走完四级；
     * 四个入口（预览 / 写盘 / 差异 / ZIP）都调本方法，保证路径只有一份实现。
     */
    private String backendBaseOf(GenTable table) {
        return GenPathResolver.backendBase(table, projectOf(table), outputProperties);
    }

    /**
     * 后端仓库根目录：表级配置 &gt; 所属系统 &gt; 目标模块 &gt; gen.output.backend-root
     */
    private String backendRootOf(GenTable table) {
        return GenPathResolver.backendRoot(table, projectOf(table), outputProperties);
    }

    /**
     * 前端仓库根目录：表级配置 &gt; 所属系统 &gt; gen.output.frontend-root
     */
    private String frontendRootOf(GenTable table) {
        return GenPathResolver.frontendRoot(table, projectOf(table), outputProperties);
    }

    /**
     * 前端写盘基准目录 = frontend-root [+ gen.output.frontend-path]。
     *
     * <p>模板产物路径固定是 {@code src/api/..}、{@code src/views/..}，这是「前端工程根」的相对路径；
     * 目标前端位于 monorepo 子目录（如 demo-web/）时，只配 frontend-path 即可，
     * 模板不用跟着目录形态改。
     */
    private Path frontendBaseOf(GenTable table, Path rootPath) {
        String sub = frontendPathOf(table);
        if (!StringUtils.hasText(sub)) {
            return rootPath;
        }
        Path base = rootPath.resolve(sub).normalize();
        // 越界（.. 逃出前端根）直接回落根目录，交给上层 startsWith 校验兜住
        return base.startsWith(rootPath) ? base : rootPath;
    }

    /**
     * 前端工程相对子目录：所属系统 &gt; 目标模块 &gt; 全局，都没有则为空（前端工程就在 frontend-root）
     *
     * <p>表级没有 frontendPath 字段（一张表不可能换前端工程），所以第一个有资格说话的就是系统。
     */
    /**
     * V6.5 移动端工程根：与前端工程平级的同级目录 {@code <前端工程目录名>-mobile}。
     * 与 scaffold 侧 resolveRoot(dtoFrontendRoot, configRoot, frontendModule + "-mobile") 必须算出同一路径——
     * 同一份 frontend 配置，scaffold 写骨架、生成写表配置，落进同一个 mobile 工程。
     */
    private String mobileRootOf(GenTable table) {
        Path frontendBase = frontendBaseOf(table,
                Paths.get(frontendRootOf(table)).toAbsolutePath().normalize());
        Path parent = frontendBase.getParent();
        String dirName = frontendBase.getFileName() == null ? "mobile" : frontendBase.getFileName().toString();
        return (parent == null ? frontendBase : parent).resolve(dirName + "-mobile").toString();
    }

    private String frontendPathOf(GenTable table) {
        return GenPathResolver.frontendPath(table, projectOf(table), outputProperties);
    }

    /**
     * ZIP 相对路径安全校验，禁止路径穿越
     */
    private String sanitize(String tableName, String path) {
        if (path == null || path.isBlank()
                || path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            throw new BusinessException(400, "非法生成路径：" + path);
        }
        String normalized = Paths.get(path.replace('\\', '/')).normalize().toString().replace('\\', '/');
        if (normalized.startsWith("..") || normalized.startsWith("/")) {
            throw new BusinessException(400, "非法生成路径：" + path);
        }
        return normalized;
    }

    private void writeZipEntry(ZipOutputStream zip, String path, String content) {
        try {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new BusinessException(500, "写入 ZIP 条目失败：" + path);
        }
    }
}
