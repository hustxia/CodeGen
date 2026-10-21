package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenDiffDTO;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.dto.GenWriteDTO;
import com.codegen.entity.GenOutput;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenOutputMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenCodeService;
import com.codegen.service.GenTableColumnService;
import com.codegen.util.GenMergeUtils;
import com.codegen.util.GenTemplateEngine;
import com.codegen.vo.GenDiffVO;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenWriteVO;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成服务：ZIP 内存打包 / 预览 / 写盘（不落服务器临时文件）
 */
@Service
public class GenCodeServiceImpl implements GenCodeService {

    private static final List<String> INFO_FILES = List.of("router-snippet.txt", "README-使用说明.txt");

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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

    /**
     * 渲染结果：表配置 + 相对路径 -> 内容
     */
    private record TableFiles(GenTable table, Map<String, String> files) {
    }

    @Override
    public GenZipResult generateZip(GenDownloadDTO dto) {
        List<TableFiles> rendered = renderTables(dto == null ? null : dto.getIds());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
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
                                    + genTemplateEngine.renderRouterSnippet(tf.table()) + "\n");
                    snippetWritten = true;
                }
            }
            if (all.isEmpty()) {
                throw new BusinessException(404, "表配置不存在，无法生成");
            }
            // 共享文件（readme）：以 ZIP 首表解析表级覆盖，无覆盖回落全局
            for (Map.Entry<String, String> entry : genTemplateEngine.renderShared(rendered.get(0).table().getId()).entrySet()) {
                all.putIfAbsent(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : all.entrySet()) {
                writeZipEntry(zip, entry.getKey(), entry.getValue());
            }
            // manifest.txt：逐文件 路径 + 字节数 + MD5（排除自身）
            StringBuilder manifest = new StringBuilder();
            for (Map.Entry<String, String> entry : all.entrySet()) {
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                manifest.append(entry.getKey()).append('\t').append(bytes.length).append('\t')
                        .append(md5Hex(bytes)).append('\n');
            }
            writeZipEntry(zip, "manifest.txt", manifest.toString());
            // X-Gen-Files 清单与 ZIP 写入顺序一致，manifest.txt 计入
            List<String> files = new ArrayList<>(all.keySet());
            files.add("manifest.txt");
            return new GenZipResult(bos.toByteArray(), files);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "ZIP 打包失败：" + e.getMessage());
        }
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
        List<Long> ids = dto == null ? null : dto.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要生成的表");
        }
        boolean overwrite = Boolean.TRUE.equals(dto.getOverwrite());
        List<GenWriteVO> results = new ArrayList<>();
        for (Long id : ids) {
            GenTable table = genTableMapper.selectById(id);
            if (table == null) {
                continue;
            }
            results.addAll(writeTable(table, overwrite, batchNo));
            boolean wrote = results.stream()
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
        return results;
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
        List<GenDiffVO> result = new ArrayList<>();
        for (Long id : ids) {
            GenTable table = genTableMapper.selectById(id);
            if (table == null) {
                continue;
            }
            // 同 writeTable：先解析目标模块，保证后端文件判定用的是最终输出路径
            applyOutputDefaults(table);
            String backendBase = backendBaseOf(table);
            for (Map.Entry<String, String> entry : renderOne(table).entrySet()) {                String relPath = entry.getKey();
                if (INFO_FILES.contains(relPath)) {
                    continue;
                }
                boolean isBackend = relPath.startsWith(backendBase + "/");
                String root = isBackend ? backendRootOf(table) : frontendRootOf(table);
                if (!StringUtils.hasText(root)) {
                    continue;
                }
                Path rootPath = Paths.get(root).toAbsolutePath().normalize();
                if (!isBackend) {
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
                String oldContent = null;
                if (Files.exists(target)) {
                    try {
                        oldContent = Files.readString(target, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        oldContent = null;
                    }
                }
                String newContent = entry.getValue();
                List<GenMergeUtils.DiffLine> lines = GenMergeUtils.diff(
                        oldContent == null ? "" : oldContent, newContent);
                int[] stats = GenMergeUtils.stats(lines);
                vo.setAdditions(stats[0]);
                vo.setDeletions(stats[1]);
                boolean isNew = oldContent == null;
                vo.setStatus(isNew ? "NEW" : (stats[0] + stats[1] == 0 ? "UNCHANGED" : "MODIFIED"));
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
     * @param batchNo 非空时，覆盖前先备份到 {@code <root>/.gen-backup/<batchNo>/} 并记录 gen_history
     */
    private List<GenWriteVO> writeTable(GenTable table, boolean overwrite, String batchNo) {
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
                boolean isBackend = relPath.startsWith(backendBase + "/");
                String group = isBackend ? "backend" : "frontend";
                String root = isBackend ? backendRootOf(table) : frontendRootOf(table);
                if (!StringUtils.hasText(root)) {
                    String msg = (isBackend ? "后端" : "前端") + "根目录未配置：请在 gen.output."
                            + (isBackend ? "backend-root" : "frontend-root") + " 或表配置中补充";
                    recordOutput(batchNo, table, group, relPath, "", "ERROR", 0, "", msg);
                    results.add(new GenWriteVO(table.getTableName(), relPath, "ERROR", msg));
                    continue;
                }
                Path rootPath = Paths.get(root).toAbsolutePath().normalize();
                if (!isBackend) {
                    // 前端产物路径形如 src/api/x.js，可能叠加 gen.output.frontend-path 子目录
                    rootPath = frontendBaseOf(table, rootPath);
                }
                Path target = rootPath.resolve(relPath).normalize();
                if (!target.startsWith(rootPath)) {
                    recordOutput(batchNo, table, group, relPath, "", "ERROR", 0, "", "非法目标路径（越界）");
                    results.add(new GenWriteVO(table.getTableName(), relPath, "ERROR", "非法目标路径（越界）"));
                    continue;
                }
                boolean exists = Files.exists(target);
                if (exists && !overwrite) {
                    recordOutput(batchNo, table, group, relPath, target.toString(), "SKIPPED", 0, "",
                            "文件已存在，未勾选覆盖");
                    results.add(new GenWriteVO(table.getTableName(), relPath, "SKIPPED", "文件已存在，未勾选覆盖"));
                    continue;
                }
                // 覆盖前备份：只备份真正会被覆盖的已有文件
                String backupPath = exists ? backup(rootPath, batchNo, relPath, target) : null;
                String finalContent = content;
                String status = "WRITTEN";
                String message = target.toString();
                if (exists) {
                    // 标记块合并：块内替换、块外（手写代码）原样保留
                    String oldContent = Files.readString(target, StandardCharsets.UTF_8);
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
                recordHistory(batchNo, table.getTableName(), target.toString(), backupPath, status);
                // 文件级台账（F13）：动作粒度比 VO 状态更细——覆盖已有文件记 OVERWRITTEN
                byte[] writtenBytes = finalContent.getBytes(StandardCharsets.UTF_8);
                boolean merged = "MERGED".equals(status);
                String action = merged ? "MERGED" : (exists ? "OVERWRITTEN" : "WRITTEN");
                recordOutput(batchNo, table, isBackend ? "backend" : "frontend", relPath, target.toString(),
                        action, writtenBytes.length, md5Hex(writtenBytes), merged ? message : "");
                results.add(new GenWriteVO(table.getTableName(), relPath, status, message, backupPath));
            } catch (Exception e) {
                recordHistory(batchNo, table.getTableName(), relPath, null, "ERROR");
                recordOutput(batchNo, table, relPath.startsWith(backendBase + "/") ? "backend" : "frontend",
                        relPath, "", "ERROR", 0, "", e.getMessage());
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

    private void recordHistory(String batchNo, String tableName, String filePath, String backupPath, String status) {
        if (!StringUtils.hasText(batchNo)) {
            return;
        }
        try {
            jdbcTemplate.update("INSERT INTO gen_history(batch_no, table_name, file_path, backup_path, status, operator) "
                            + "VALUES(?, ?, ?, ?, ?, ?)",
                    batchNo, tableName, filePath, backupPath, status, operator());
        } catch (Exception e) {
            // 历史记录失败不影响写盘
        }
    }

    private String operator() {
        String name = UserContext.getUsername();
        if (StringUtils.hasText(name)) {
            return name;
        }
        Long userId = UserContext.getUserId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    /**
     * 写盘文件级台账（F13）：逐文件一行，失败不影响写盘主流程
     */
    private void recordOutput(String batchNo, GenTable table, String fileGroup, String relPath,
                              String absPath, String action, long fileSize, String md5, String message) {
        try {
            GenOutput row = new GenOutput();
            row.setBatchNo(StringUtils.hasText(batchNo) ? batchNo : "");
            row.setTableId(table.getId());
            row.setTableName(table.getTableName());
            row.setFileGroup(fileGroup);
            row.setRelPath(relPath);
            row.setAbsPath(StringUtils.hasText(absPath) ? absPath : "");
            row.setAction(action);
            row.setFileSize(fileSize);
            row.setMd5(StringUtils.hasText(md5) ? md5 : "");
            row.setMessage(message == null ? "" : message);
            row.setOperator(operator());
            genOutputMapper.insert(row);
        } catch (Exception e) {
            // 台账记录失败不影响写盘
        }
    }

    private String md5Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
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
        List<GenTable> tables = new ArrayList<>();
        for (Long id : ids) {
            GenTable table = genTableMapper.selectById(id);
            if (table == null) {
                throw new BusinessException(404, "表配置不存在，无法生成");
            }
            tables.add(table);
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
            rendered.add(new TableFiles(table,
                    genTemplateEngine.render(table, columnsByTable.getOrDefault(table.getId(), List.of()))));
        }
        return rendered;
    }

    /**
     * 渲染单表文件
     */
    private Map<String, String> renderOne(GenTable table) {
        applyOutputDefaults(table);
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTableColumn::getTableId, table.getId());
        wrapper.orderByAsc(GenTableColumn::getSort);
        return genTemplateEngine.render(table, columnService.list(wrapper));
    }

    /**
     * 解析输出参数（不落库，仅本次渲染生效）。
     * 优先级：目标模块（gen.output.modules[targetModule]） &gt; 表级配置 &gt; 全局默认。
     * 目标模块排最前，是因为从库里导入表时 backend-path/package-name 会被回填成默认模块的值，
     * 此时只有让模块覆盖，才能把同一套生成器指到 product / order 等其它微服务。
     */
    private void applyOutputDefaults(GenTable table) {
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
     * 表配置的目标模块（gen_table.target_module），未配置或未命中返回 null
     */
    private GenOutputProperties.ModuleConfig moduleOf(GenTable table) {
        String key = table.getTargetModule();
        if (!StringUtils.hasText(key) || outputProperties.getModules() == null) {
            return null;
        }
        return outputProperties.getModules().get(key.trim());
    }

    /**
     * 后端文件相对路径前缀（与 GenTemplates.render 的 backendBase 一致）
     */
    private String backendBaseOf(GenTable table) {
        if (StringUtils.hasText(table.getBackendPath())) {
            return table.getBackendPath();
        }
        if (StringUtils.hasText(outputProperties.getBackendPath())) {
            return outputProperties.getBackendPath();
        }
        String pkg = StringUtils.hasText(table.getPackageName()) ? table.getPackageName() : "com.codegen";
        return "src/main/java/" + pkg.replace('.', '/');
    }

    /**
     * 后端仓库根目录：表级配置 > 目标模块配置 > gen.output.backend-root
     */
    private String backendRootOf(GenTable table) {
        if (StringUtils.hasText(table.getBackendRoot())) {
            return table.getBackendRoot();
        }
        GenOutputProperties.ModuleConfig module = moduleOf(table);
        if (module != null && StringUtils.hasText(module.getBackendRoot())) {
            return module.getBackendRoot();
        }
        return outputProperties.getBackendRoot();
    }

    /**
     * 前端仓库根目录：表级配置优先，留空回落 gen.output.frontend-root
     */
    private String frontendRootOf(GenTable table) {
        return StringUtils.hasText(table.getFrontendRoot())
                ? table.getFrontendRoot()
                : outputProperties.getFrontendRoot();
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
     * 前端工程相对子目录：表级 &gt; 目标模块 &gt; 全局，都没有则为空（前端工程就在 frontend-root）
     */
    private String frontendPathOf(GenTable table) {
        GenOutputProperties.ModuleConfig module = moduleOf(table);
        if (module != null && StringUtils.hasText(module.getFrontendPath())) {
            return module.getFrontendPath().trim();
        }
        return StringUtils.hasText(outputProperties.getFrontendPath())
                ? outputProperties.getFrontendPath().trim()
                : "";
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
