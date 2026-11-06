package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.dto.GenPipelineCreateDTO;
import com.codegen.dto.GenPipelinePageDTO;
import com.codegen.dto.GenPipelinePrepareDTO;
import com.codegen.entity.GenPipeline;
import com.codegen.entity.GenPipelineStep;
import com.codegen.entity.SysProject;
import com.codegen.mapper.GenPipelineMapper;
import com.codegen.mapper.GenPipelineStepMapper;
import com.codegen.service.GenDdlService;
import com.codegen.service.GenPipelineService;
import com.codegen.service.SysProjectService;
import com.codegen.support.GenAuditColumns;
import com.codegen.support.GenDdlPatcher;
import com.codegen.support.PipelineAssembler;
import com.codegen.support.PipelineSseRegistry;
import com.codegen.vo.GenPipelineAiVO;
import com.codegen.vo.GenPipelineAuditReportVO;
import com.codegen.vo.GenPipelineLintFixVO;
import com.codegen.vo.GenPipelineLintVO;
import com.codegen.vo.GenPipelinePrepareVO;
import com.codegen.vo.GenPipelineVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 流水线编排实现。
 *
 * <p>并发策略：单线程执行器 + 运行标记，天然满足 M1「同一时刻仅 1 条运行中」；
 * run 接口只负责置状态和投递，执行体在后台线程，HTTP 请求立即返回。
 */
@Slf4j
@Service
public class GenPipelineServiceImpl implements GenPipelineService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern DB_NAME = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");

    @Autowired
    private GenPipelineMapper pipelineMapper;
    @Autowired
    private GenPipelineStepMapper stepMapper;
    @Autowired
    private SysProjectService projectService;
    @Autowired
    private GenDdlService ddlService;
    @Autowired
    private GenPipelineProperties props;
    @Autowired
    private GenOutputProperties outputProps;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PipelineSseRegistry sseRegistry;
    @Autowired
    private PipelineAssembler assembler;
    @Autowired
    private PipelineExecutor executor;

    @Autowired
    private com.codegen.support.GenSeedGenerator seedGenerator;

    /** 单线程执行器：M1 只允许一条流水线在跑，排队即拒绝 */
    private final ExecutorService runner = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pipeline-runner");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);

    // ==================== 查询 ====================

    @Override
    public GenPipelineVO create(GenPipelineCreateDTO dto) {
        String dbName = dto.getDbName() == null ? "" : dto.getDbName().trim();
        if (!DB_NAME.matcher(dbName).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "数据库名只能用小写字母开头的小写字母/数字/下划线（1~63 位）");
        }
        // 同名库的活流水线存在即阻断：静默覆盖别人的库是事故，不是便利
        Long active = pipelineMapper.selectCount(new LambdaQueryWrapper<GenPipeline>()
                .eq(GenPipeline::getDbName, dbName)
                .notIn(GenPipeline::getStatus, "failed", "stopped"));
        if (active != null && active > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "已存在目标库为 " + dbName + " 的进行中任务，请换库名或先停止旧任务");
        }
        if (projectIdOf(dbName) != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "系统台账已存在编码 " + dbName + "，同名系统禁止重复创建");
        }
        if ("manual".equals(dto.getMode()) && !StringUtils.hasText(dto.getDdlText())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "手动模式必须粘贴 DDL");
        }
        if (!"manual".equals(dto.getMode())
                && (dto.getTables() == null || dto.getTables().isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "AI 模式必须带回确认过的表结构");
        }

        GenPipeline pipeline = new GenPipeline();
        pipeline.setPipelineNo("PPL" + TS.format(LocalDateTime.now()));
        pipeline.setSystemName(dto.getSystemName().trim());
        pipeline.setSystemDesc(dto.getSystemDesc());
        pipeline.setDbName(dbName);
        pipeline.setMode("manual".equals(dto.getMode()) ? "manual" : "ai");
        // V5.2：数据初始化模式；V7.1 起支持 ai（AI 值池增强，失败自动回落 rule）
        // 其余含 NULL 一律 off，回落语义唯一出口在 PipelineExecutor.stepSeed
        pipeline.setSeedMode("rule".equals(dto.getSeedMode()) || "ai".equals(dto.getSeedMode())
                ? dto.getSeedMode() : "off");
        pipeline.setStatus("draft");
        pipelineMapper.insert(pipeline);

        seedSteps(pipeline.getId());
        return detail(pipeline.getId());
    }

    @Override
    public IPage<GenPipelineVO> page(GenPipelinePageDTO dto) {
        Page<GenPipeline> page = new Page<>(Math.max(1, dto.getPageNum()), Math.min(100, dto.getPageSize()));
        LambdaQueryWrapper<GenPipeline> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getStatus())) {
            wrapper.eq(GenPipeline::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(GenPipeline::getId);
        IPage<GenPipeline> result = pipelineMapper.selectPage(page, wrapper);
        return result.convert(p -> assembler.toVO(p, stepsOf(p.getId())));
    }

    @Override
    public GenPipelineVO detail(Long id) {
        GenPipeline pipeline = mustGet(id);
        return assembler.toVO(pipeline, stepsOf(id));
    }

    // ==================== 建库建表 ====================

    @Override
    public GenPipelinePrepareVO prepareDb(Long id, GenPipelinePrepareDTO dto) {
        GenPipeline pipeline = mustGet(id);
        if (!"draft".equals(pipeline.getStatus()) && !"await_confirm".equals(pipeline.getStatus())
                && !"failed".equals(pipeline.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "任务状态为 " + pipeline.getStatus() + "，不允许建库");
        }
        boolean manual = "manual".equals(pipeline.getMode());
        Long projectId = pipeline.getProjectId();
        boolean projectCreatedHere = false;
        try {
            // 1. 组 DDL：ai 模式服务端统一补审计列；manual 模式缺列改写补齐（V5.1：只增不改，明细进 auditReport）
            String ddl;
            List<GenPipelineAuditReportVO.Item> auditReport = new java.util.ArrayList<>();
            if (manual) {
                GenDdlPatcher.PatchResult patch = GenDdlPatcher.patch(dto.getDdlText());
                ddl = patch.getDdl();
                for (GenDdlPatcher.PatchItem item : patch.getItems()) {
                    GenPipelineAuditReportVO.Item ri = new GenPipelineAuditReportVO.Item();
                    ri.setTable(item.getTable());
                    ri.setAction(item.getPatchedCols().isEmpty() ? "SKIPPED" : "PATCHED");
                    ri.setPatchedCols(item.getPatchedCols());
                    auditReport.add(ri);
                }
                // 坑 38：必须语句级判断。原 contains("use ") 被 "mdm_warehouse (" 的子串
                // "use " 误命中 → CREATE DATABASE 没补 → USE 静默回落把表建进平台库
                if (!GenDdlPatcher.createsDatabase(ddl) && !GenDdlPatcher.hasUseStatement(ddl)) {
                    ddl = "CREATE DATABASE IF NOT EXISTS `" + pipeline.getDbName()
                            + "` DEFAULT CHARACTER SET utf8mb4;\n" + ddl;
                }
            } else {
                ddl = buildDdl(pipeline.getDbName(), dto.getTables());
            }

            // 2. 建系统台账（projectCode=库名；路径/包名由流水线约定推导）
            if (projectId == null) {
                projectId = createProject(pipeline);
                projectCreatedHere = true;
                pipeline.setProjectId(projectId);
                pipelineMapper.updateById(pipeline);
            }

            // 3. 执行 DDL + 导入表配置（挂到新系统）
            GenDdlExecuteDTO exec = new GenDdlExecuteDTO();
            exec.setDdl(ddl);
            exec.setSchema(pipeline.getDbName());
            exec.setAutoImport(true);
            exec.setAllowDestructive(false);
            exec.setProjectId(projectId);
            var result = ddlService.execute(exec);

            pipeline.setStatus("await_confirm");
            pipelineMapper.updateById(pipeline);

            // 落盘补齐后的 DDL：流水线重试（db 步发现 0 表）时由执行器自动重放，
            // 否则「重新执行」永远卡在 db 校验——DDL 只是本次请求的临时参数（V5.1.2）
            try {
                Path replay = Paths.get(props.getLogDir(), pipeline.getPipelineNo(), "db.sql");
                Files.createDirectories(replay.getParent());
                Files.writeString(replay, ddl, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // 落盘失败不阻断主流程；重试时 stepDb 会给出可读的报错引导
            }

            GenPipelinePrepareVO vo = new GenPipelinePrepareVO();
            vo.setProjectId(projectId);
            vo.setDbName(result.getSchema());
            vo.setTableCount(result.getCreatedTables().size());
            vo.setAuditReport(auditReport);
            return vo;
        } catch (RuntimeException e) {
            // 失败清残留：draft 留着会以「进行中任务」堵死同名库重试（死循环根因）
            cleanupDraft(pipeline, projectCreatedHere);
            throw e;
        }
    }

    /**
     * AI 表结构 → DDL：每张表固定补 id 主键 + 5 个审计列，从源头消灭「生成物跑起来才爆缺列」。
     */
    private String buildDdl(String dbName, List<GenPipelineAiVO.TableModel> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE DATABASE IF NOT EXISTS `").append(dbName)
                .append("` DEFAULT CHARACTER SET utf8mb4;\n");
        for (GenPipelineAiVO.TableModel t : tables) {
            String table = t.getTableName().trim().toLowerCase();
            if (!DB_NAME.matcher(table).matches()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "表名不合法：" + table + "（小写字母开头的 snake_case）");
            }
            sb.append("\nCREATE TABLE IF NOT EXISTS `").append(table).append("` (\n");
            sb.append("  ").append(GenAuditColumns.columnDef("id")).append(",\n");
            for (GenPipelineAiVO.Column c : t.getColumns()) {
                String name = c.getName() == null ? "" : c.getName().trim().toLowerCase();
                if (!DB_NAME.matcher(name).matches()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                            "列名不合法：" + table + "." + name);
                }
                if ("id".equals(name)) {
                    continue; // 主键由服务端统一生成
                }
                String type = c.getType() == null || c.getType().isBlank() ? "varchar(255)" : c.getType().trim();
                sb.append("  `").append(name).append("` ").append(type)
                        .append(" NULL COMMENT '").append(escape(c.getComment())).append("',\n");
            }
            sb.append("  ").append(GenAuditColumns.columnDef("create_time")).append(",\n");
            sb.append("  ").append(GenAuditColumns.columnDef("update_time")).append(",\n");
            sb.append("  ").append(GenAuditColumns.columnDef("create_by")).append(",\n");
            sb.append("  ").append(GenAuditColumns.columnDef("update_by")).append(",\n");
            sb.append("  ").append(GenAuditColumns.columnDef("deleted")).append(",\n");
            sb.append("  PRIMARY KEY (`id`)\n");
            sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='")
                    .append(escape(t.getTableComment())).append("';\n");
        }
        return sb.toString();
    }

    private Long createProject(GenPipeline pipeline) {
        SysProject project = new SysProject();
        project.setProjectCode(pipeline.getDbName());
        project.setProjectName(pipeline.getSystemName());
        project.setBackendRoot(outputProps.getBackendRoot());
        project.setFrontendRoot(outputProps.getFrontendRoot());
        String code = pipeline.getDbName();
        project.setRootPackage("com." + code);
        project.setBackendPath(code + "-server/src/main/java/com/" + code);
        project.setFrontendPath(code + "-web");
        // F-09：网关前缀空即空，生成物 apiBase 不再吞 /api/demo
        project.setGatewayPrefix("");
        if (StringUtils.hasText(props.getSelfcheckClasspath())) {
            project.setSelfcheckClasspath(props.getSelfcheckClasspath());
        }
        project.setEnabled(1);
        project.setIsDefault(0);
        project.setSort(99);
        project.setRemark("V5.0 一句话建系统创建（" + pipeline.getPipelineNo() + "）");
        return projectService.create(project);
    }

    // ==================== 运行 / 停止 ====================

    @Override
    public GenPipelineVO run(Long id) {
        GenPipeline pipeline = mustGet(id);
        if (!"await_confirm".equals(pipeline.getStatus()) && !"failed".equals(pipeline.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "只有待确认/失败状态的任务可以启动，当前：" + pipeline.getStatus());
        }
        if (pipeline.getProjectId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请先完成建库建表");
        }
        // 端口预分配并回填：失败时任务留在原状态，可改端口后重试
        if (pipeline.getBackendPort() == null) {
            pipeline.setBackendPort(allocatePort(props.getBackendPortBase()));
        }
        if (pipeline.getFrontendPort() == null) {
            pipeline.setFrontendPort(allocatePort(props.getFrontendPortBase()));
        }
        pipelineMapper.updateById(pipeline);

        // 先投递任务再置位标记的窗口里可能被并发钻空子，但 M1 单用户场景可接受；
        // 关键约束是：submit 失败必须回滚标记，否则运行标记永久卡死
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "已有流水线在运行中，请稍后再试");
        }
        pipeline.setStatus("running");
        pipelineMapper.updateById(pipeline);
        push(pipeline);
        try {
            runner.submit(() -> {
                try {
                    executor.execute(id);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            pipeline.setStatus("failed");
            pipeline.setFailReason("流水线投递失败：" + e.getMessage());
            pipelineMapper.updateById(pipeline);
            throw e;
        }
        return detail(id);
    }

    @Override
    public GenPipelineVO stop(Long id) {
        GenPipeline pipeline = mustGet(id);
        killPid(pipeline.getBackendPid());
        killPid(pipeline.getFrontendPid());
        pipeline.setStatus("stopped");
        pipelineMapper.updateById(pipeline);
        push(pipeline);
        return detail(id);
    }

    private void killPid(Long pid) {
        if (pid == null || pid <= 0) {
            return;
        }
        try {
            new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid))
                    .start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("停止进程 {} 失败（可能已退出）: {}", pid, e.getMessage());
        }
    }

    // ==================== lint / fix ====================

    @Override
    public GenPipelineLintVO lint(String dbName) {
        GenPipelineLintVO vo = new GenPipelineLintVO();
        vo.setTableCount(0);
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type='BASE TABLE'",
                String.class, dbName);
        vo.setTableCount(tables.size());
        for (String table : tables) {
            List<String> cols = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
                    String.class, dbName, table);
            List<String> missing = GenAuditColumns.missingIn(cols);
            if (!missing.isEmpty()) {
                vo.setOk(false);
                GenPipelineLintVO.Item item = new GenPipelineLintVO.Item();
                item.setTable(table);
                item.setMissingCols(missing);
                item.setFixSql("ALTER TABLE `" + table + "` " + fixSqlOf(missing) + ";");
                vo.getItems().add(item);
            }
        }
        return vo;
    }

    /**
     * 一键补齐（V5.1 P0-2）：对缺审计列的表逐表执行 ALTER（定义走 GenAuditColumns 单一实现）。
     * 幂等：执行前现查 information_schema，重复点击第二轮全 SKIPPED；并发撞上 Duplicate column
     * 也按跳过处理。单表失败记入报告并继续其余表，最后复检给结论。
     */
    @Override
    public GenPipelineLintFixVO fix(Long id) {
        GenPipeline pipeline = mustGet(id);
        String dbName = pipeline.getDbName();
        GenPipelineLintFixVO vo = new GenPipelineLintFixVO();
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type='BASE TABLE'",
                String.class, dbName);
        vo.setTableCount(tables.size());
        for (String table : tables) {
            GenPipelineLintFixVO.Item item = new GenPipelineLintFixVO.Item();
            item.setTable(table);
            List<String> cols = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
                    String.class, dbName, table);
            List<String> missing = GenAuditColumns.missingIn(cols);
            if (missing.isEmpty()) {
                item.setAction("SKIPPED");
                vo.getItems().add(item);
                continue;
            }
            item.setMissingCols(missing);
            // 注意 1：jdbc execute 不能带尾分号（冒烟实测 bad SQL grammar），分号只用于 lint 展示
            // 注意 2：jdbcTemplate 是平台默认连接，必须显式限定库名，否则 ALTER 打到平台库报「表不存在」
            String alterSql = "ALTER TABLE `" + dbName + "`.`" + table + "` " + fixSqlOf(missing);
            try {
                jdbcTemplate.execute(alterSql);
                item.setAction("PATCHED");
                item.setPatchedCols(missing);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("Duplicate column name")) {
                    // 并发双击窗口：列刚被另一请求加上，按幂等跳过而非失败
                    item.setAction("SKIPPED");
                    item.setError("并发重复执行：列已存在，按幂等跳过");
                } else {
                    // 一表失败不拖垮整批；原因如实进报告
                    item.setAction("FAILED");
                    item.setError(msg.isBlank() ? e.getClass().getSimpleName() : msg);
                    log.warn("lint fix 补列失败 {}.{}: {}", dbName, table, msg);
                }
            }
            vo.getItems().add(item);
        }
        vo.setOk(lint(dbName).isOk());
        return vo;
    }

    /**
     * V5.2 数据初始化：存量系统补跑 seed。只补台账中 0 行的表，幂等可重复调用；
     * seed.sql 落盘到本流水线日志目录，可人工重放。
     */
    @Override
    public com.codegen.vo.GenSeedVO seedExisting(Long id) {
        GenPipeline pipeline = mustGet(id);
        if (pipeline.getProjectId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "该任务尚未完成建库（prepare-db），没有表台账可补数");
        }
        return seedGenerator.seedFor(pipeline.getProjectId(), pipeline.getDbName(), pipeline.getPipelineNo());
    }

    /** ALTER 片段拼接（单一实现 GenAuditColumns.alterFragment） */
    private String fixSqlOf(List<String> missing) {
        StringBuilder sb = new StringBuilder();
        for (String col : missing) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(GenAuditColumns.alterFragment(col));
        }
        return sb.toString();
    }

    /** 建库失败后的残留清理：draft/步骤必删；本次新建的台账一并删（有表保护，删不掉就留着记日志） */
    private void cleanupDraft(GenPipeline pipeline, boolean projectCreatedHere) {
        try {
            stepMapper.delete(new LambdaQueryWrapper<GenPipelineStep>()
                    .eq(GenPipelineStep::getPipelineId, pipeline.getId()));
            pipelineMapper.deleteById(pipeline.getId());
            if (projectCreatedHere && pipeline.getProjectId() != null) {
                projectService.delete(pipeline.getProjectId());
            }
        } catch (Exception ex) {
            log.warn("清理失败流水线残留失败 pipelineId={}: {}", pipeline.getId(), ex.getMessage());
        }
    }

    // ==================== SSE ====================

    @Override
    public SseEmitter stream(Long id) {
        GenPipeline pipeline = mustGet(id);
        SseEmitter emitter = sseRegistry.register(id);
        // 立即推一帧当前状态：SseEmitter 不发数据就不提交响应头，
        // 客户端会干等到超时；同时满足「刷新后马上看到进度」（AC4）
        try {
            emitter.send(SseEmitter.event().name("progress")
                    .data(assembler.toVO(pipeline, stepsOf(id))));
        } catch (Exception e) {
            log.warn("SSE 初始帧推送失败: {}", e.getMessage());
        }
        return emitter;
    }

    // ==================== 私有 ====================

    private void push(GenPipeline pipeline) {
        try {
            sseRegistry.send(pipeline.getId(), assembler.toVO(pipeline, stepsOf(pipeline.getId())));
        } catch (Exception e) {
            log.warn("SSE 推送失败: {}", e.getMessage());
        }
    }

    private void seedSteps(Long pipelineId) {
        String[][] defs = {
                {"model", "AI 领域建模", "pipeline"},
                {"db", "建库建表+导表", "pipeline"},
                {"ai_recommend", "AI 页面与菜单推荐", "pipeline"},
                {"seed", "数据初始化", "pipeline"},
                {"scaffold", "前后端骨架", "pipeline"},
                {"gen", "代码生成+自检", "pipeline"},
                {"package_backend", "后端打包", "backend"},
                {"deploy_backend", "后端启动", "backend"},
                {"package_frontend", "前端依赖安装", "frontend"},
                {"package_mobile", "移动端构建", "mobile"},
                {"deploy_frontend", "前端启动", "frontend"},
                {"health", "健康检查", "pipeline"}
        };
        for (int i = 0; i < defs.length; i++) {
            GenPipelineStep step = new GenPipelineStep();
            step.setPipelineId(pipelineId);
            step.setStepKey(defs[i][0]);
            step.setStepName(defs[i][1]);
            step.setStepOrder(i + 1);
            step.setSide(defs[i][2]);
            step.setStatus("pending");
            stepMapper.insert(step);
        }
    }

    private GenPipeline mustGet(Long id) {
        GenPipeline pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流水线任务不存在：" + id);
        }
        return pipeline;
    }

    private List<GenPipelineStep> stepsOf(Long pipelineId) {
        return stepMapper.selectList(new LambdaQueryWrapper<GenPipelineStep>()
                .eq(GenPipelineStep::getPipelineId, pipelineId)
                .orderByAsc(GenPipelineStep::getStepOrder));
    }

    private Long projectIdOf(String projectCode) {
        SysProject p = projectService.getByCode(projectCode);
        return p == null ? null : p.getId();
    }

    /**
     * 端口分配：从起点顺延找第一个空闲端口，并避开平台自身端口。
     */
    private Integer allocatePort(int base) {
        int ownPort = 8120;
        for (int port = base; port < base + 200; port++) {
            final int candidate = port;
            if (candidate == ownPort) {
                continue;
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", candidate));
                socket.close();
                // 绑定成功即视为空闲；其他流水线已分配的端口也避开
                Long used = pipelineMapper.selectCount(new LambdaQueryWrapper<GenPipeline>()
                        .in(GenPipeline::getStatus, "draft", "await_confirm", "running")
                        .and(w -> w.eq(GenPipeline::getBackendPort, candidate)
                                .or().eq(GenPipeline::getFrontendPort, candidate)));
                if (used == null || used == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // 绑定失败 = 被占用，顺延
            }
        }
        throw new BusinessException(ResultCode.FAIL.getCode(), "端口分配失败：" + base + " 起连续 200 个端口均不可用");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
