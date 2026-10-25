package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenApplyDTO;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.entity.GenPipeline;
import com.codegen.entity.GenPipelineStep;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenPipelineMapper;
import com.codegen.mapper.GenPipelineStepMapper;
import com.codegen.service.GenApplyService;
import com.codegen.service.GenDdlService;
import com.codegen.service.GenScaffoldService;
import com.codegen.service.GenTableService;
import com.codegen.service.SysProjectService;
import com.codegen.support.PipelineAssembler;
import com.codegen.support.PipelineSseRegistry;
import com.codegen.vo.GenApplyStageVO;
import com.codegen.vo.GenScaffoldVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 流水线执行器：9 步串行，一步一行落库，状态变更即推 SSE。
 *
 * <p>踩坑固化（来自 2026-10-23 财务系统接入）：
 * 1) 子进程启动必须显式 --server.port 且清理 SERVER__PORT 环境变量，
 *    否则残留环境变量会让 Spring 随机选端口，健康检查永远等不到；
 * 2) 部署日志落文件而不是内存——排错要的是 tail，不是整个 stdout 堆在堆里；
 * 3) 脚手架/生成/DDL 全部走幂等接口，整线重跑是安全的兜底重试方式。
 */
@Slf4j
@Component
public class PipelineExecutor {

    /** 步骤 key 常量（与 gen_pipeline_step 种子一致） */
    public static final String[] STEP_KEYS = {
            "model", "db", "scaffold", "gen",
            "package_backend", "deploy_backend",
            "package_frontend", "deploy_frontend",
            "health"
    };

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GenPipelineMapper pipelineMapper;
    private final GenPipelineStepMapper stepMapper;
    private final GenScaffoldService scaffoldService;
    private final GenDdlService ddlService;
    private final GenApplyService applyService;
    private final GenTableService tableService;
    private final SysProjectService projectService;
    private final GenPipelineProperties props;
    private final GenOutputProperties outputProps;
    private final DataSourceProperties dataSourceProps;
    private final PipelineSseRegistry sseRegistry;
    private final PipelineAssembler assembler;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PipelineExecutor(GenPipelineMapper pipelineMapper,
                            GenPipelineStepMapper stepMapper,
                            GenScaffoldService scaffoldService,
                            GenDdlService ddlService,
                            GenApplyService applyService,
                            GenTableService tableService,
                            SysProjectService projectService,
                            GenPipelineProperties props,
                            GenOutputProperties outputProps,
                            DataSourceProperties dataSourceProps,
                            PipelineSseRegistry sseRegistry,
                            PipelineAssembler assembler,
                            JdbcTemplate jdbcTemplate) {
        this.pipelineMapper = pipelineMapper;
        this.stepMapper = stepMapper;
        this.scaffoldService = scaffoldService;
        this.ddlService = ddlService;
        this.applyService = applyService;
        this.tableService = tableService;
        this.projectService = projectService;
        this.props = props;
        this.outputProps = outputProps;
        this.dataSourceProps = dataSourceProps;
        this.sseRegistry = sseRegistry;
        this.assembler = assembler;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行整条流水线（阻塞，调用方负责放线程池）。步骤失败即停，异常不外抛。
     */
    public void execute(Long pipelineId) {
        GenPipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            return;
        }
        // 重跑：全部步骤重置为 pending（所有落盘动作幂等，整线重跑安全）。
        // failed 也要重置——否则上一次的失败消息会一直挂在进度条上误导人
        List<GenPipelineStep> steps = stepsOf(pipelineId);
        for (GenPipelineStep s : steps) {
            if ("pending".equals(s.getStatus())) {
                continue;
            }
            s.setStatus("pending");
            s.setMessage(null);
            s.setLogExcerpt(null);
            s.setStartedAt(null);
            s.setFinishedAt(null);
            stepMapper.updateById(s);
        }

        pipeline.setStatus("running");
        pipeline.setFailReason(null);
        pipeline.setCurrentStep("model");
        pipelineMapper.updateById(pipeline);
        push(pipeline);

        try {
            for (GenPipelineStep step : stepsOf(pipelineId)) {
                pipeline = pipelineMapper.selectById(pipelineId);
                pipeline.setCurrentStep(step.getStepKey());
                pipelineMapper.updateById(pipeline);

                mark(step, "running", null, null);
                push(pipeline);
                try {
                    String message = switch (step.getStepKey()) {
                        case "model" -> stepModel(pipeline);
                        case "db" -> stepDb(pipeline);
                        case "scaffold" -> stepScaffold(pipeline);
                        case "gen" -> stepGen(pipeline);
                        case "package_backend" -> stepPackageBackend(pipeline);
                        case "deploy_backend" -> stepDeployBackend(pipeline);
                        case "package_frontend" -> stepPackageFrontend(pipeline);
                        case "deploy_frontend" -> stepDeployFrontend(pipeline);
                        case "health" -> stepHealth(pipeline);
                        default -> "未知步骤，跳过";
                    };
                    mark(step, "success", message, null);
                } catch (Exception e) {
                    log.error("流水线步骤失败 {}#{}: {}", pipeline.getPipelineNo(), step.getStepKey(), e.getMessage(), e);
                    mark(step, "failed", e.getMessage(), tailOf(logFile(pipeline, step.getStepKey()), 40));
                    GenPipeline failed = pipelineMapper.selectById(pipelineId);
                    failed.setStatus("failed");
                    failed.setFailReason(step.getStepName() + "：" + abbreviate(e.getMessage(), 900));
                    pipelineMapper.updateById(failed);
                    push(failed);
                    return;
                }
                push(pipelineMapper.selectById(pipelineId));
            }

            GenPipeline done = pipelineMapper.selectById(pipelineId);
            done.setStatus("success");
            done.setFrontendUrl("http://localhost:" + done.getFrontendPort());
            pipelineMapper.updateById(done);
            push(done);
        } catch (Exception e) {
            log.error("流水线执行器异常 {}: {}", pipeline.getPipelineNo(), e.getMessage(), e);
            GenPipeline failed = pipelineMapper.selectById(pipelineId);
            if (failed != null) {
                failed.setStatus("failed");
                failed.setFailReason(abbreviate(e.getMessage(), 900));
                pipelineMapper.updateById(failed);
                push(failed);
            }
        }
    }

    // ==================== 各步骤实现 ====================

    /** model：建模在 create 前已完成（AI 或手动），这里只补一条记录语义 */
    private String stepModel(GenPipeline pipeline) {
        return "manual".equals(pipeline.getMode()) ? "手动 DDL 建模" : "AI 建模（已在前端确认）";
    }

    /** db：prepare-db 阶段已建库建表导入台账，这里做就绪校验；0 表时先重放 db.sql 再复检 */
    private String stepDb(GenPipeline pipeline) {
        Long count = tableService.lambdaQuery().eq(GenTable::getProjectId, pipeline.getProjectId()).count();
        Path replay = logFile(pipeline, "db").getParent().resolve("db.sql");
        if ((count == null || count == 0) && Files.exists(replay)) {
            // 重放 prepare-db 落盘的 DDL（V5.1.2）：DDL 是一次性请求参数，
            // 没有 db.sql 的旧流水线无法自动恢复，给出可读引导
            try {
                String ddl = Files.readString(replay, StandardCharsets.UTF_8);
                log.info("db 步 0 表，重放 db.sql: {} ({} chars)", replay, ddl.length());
                GenDdlExecuteDTO exec = new GenDdlExecuteDTO();
                exec.setDdl(ddl);
                exec.setSchema(pipeline.getDbName());
                exec.setAutoImport(true);
                exec.setAllowDestructive(false);
                exec.setProjectId(pipeline.getProjectId());
                var vo = ddlService.execute(exec);
                log.info("db.sql 重放完成: success={} skipped={} failed={} imported={}",
                        vo.getSuccessCount(), vo.getSkippedCount(), vo.getFailedCount(), vo.getImportedTables());
                count = tableService.lambdaQuery().eq(GenTable::getProjectId, pipeline.getProjectId()).count();
            } catch (IOException e) {
                throw new IllegalStateException("重放 db.sql 读取失败：" + e.getMessage());
            }
        }
        if (count == null || count == 0) {
            log.warn("stepDb 0 表且无重放可用: replay={} exists={}", replay.toAbsolutePath(), Files.exists(replay));
            throw new IllegalStateException("系统中没有表配置：请回到表结构确认步骤重新点「建库建表」"
                    + "（建库建表的 DDL 不随重试保留，本流水线没有可重放的 db.sql）");
        }
        return pipeline.getDbName() + " 共 " + count + " 张业务表已导入台账";
    }

    /** scaffold：调既有 scaffold API 生成前后端骨架 + 执行种子 SQL（sys_user） */
    private String stepScaffold(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        if (project == null) {
            throw new IllegalStateException("系统台账不存在，请重跑 prepare-db");
        }
        GenScaffoldDTO dto = new GenScaffoldDTO();
        dto.setProjectName(project.getProjectCode());
        dto.setRootPackage(project.getRootPackage());
        dto.setBackendModule(project.getProjectCode() + "-server");
        dto.setFrontendModule(project.getProjectCode() + "-web");
        dto.setBackendRoot(project.getBackendRoot());
        dto.setFrontendRoot(project.getFrontendRoot());
        dto.setServerPort(pipeline.getBackendPort());
        dto.setWebPort(pipeline.getFrontendPort());
        dto.setDbName(pipeline.getDbName());
        dto.setDbHost(dbHost());
        dto.setDbUsername(dataSourceProps.getUsername());
        dto.setDbPassword(dataSourceProps.getPassword());
        dto.setWithFrontend(true);
        dto.setWithAuth(true);
        dto.setForce(false);
        GenScaffoldVO vo = scaffoldService.scaffold(dto);

        // 种子 SQL：脚手架带出 db/init_sys_user.sql（admin/admin123），导入到目标库
        Path seed = Paths.get(project.getBackendRoot(), dto.getBackendModule(), "db", "init_sys_user.sql");
        String seedNote = "（种子用户未写入：init_sys_user.sql 缺失）";
        if (Files.exists(seed)) {
            try {
                ensureLoginSeedColumns(pipeline.getDbName());
                GenDdlExecuteDTO seedDto = new GenDdlExecuteDTO();
                seedDto.setDdl(Files.readString(seed, StandardCharsets.UTF_8));
                seedDto.setSchema(pipeline.getDbName());
                seedDto.setAutoImport(false);
                var r = ddlService.execute(seedDto);
                seedNote = "（种子用户已写入，" + r.getSuccessCount() + " 条语句）";
            } catch (Exception e) {
                log.warn("种子 SQL 执行失败（不阻断）: {}", e.getMessage());
                seedNote = "（种子用户写入失败：" + abbreviate(e.getMessage(), 120) + "）";
            }
        }
        return "写入 " + vo.getWritten() + " 个文件，跳过 " + vo.getSkipped() + " " + seedNote;
    }

    /**
     * 登录基座约定列补齐（坑 39）：目标库已有用户自带的 sys_user（如 password_hash /
     * created_at 命名风格）时，脚手架约定列 password/nickname 可能缺失——
     * 种子 INSERT (username, password, nickname) 报 Unknown column 且被容错吞掉，
     * 结果种子没写入、登录必 500（实测 SCM 系统）。执行种子前先按约定补列，
     * 并把已有口令哈希列（password_hash）的值迁移到 password。
     */
    private void ensureLoginSeedColumns(String schema) {
        Integer hasTable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'sys_user'",
                Integer.class, schema);
        if (hasTable == null || hasTable == 0) {
            return; // 表不存在 → 种子 SQL 自带 CREATE TABLE，走原路径
        }
        List<String> fixSql = new ArrayList<>();
        if (!columnExists(schema, "sys_user", "password")) {
            fixSql.add("ALTER TABLE `" + schema + "`.`sys_user` ADD COLUMN `password` varchar(64) NULL COMMENT '密码（SHA-256）'");
        }
        if (!columnExists(schema, "sys_user", "nickname")) {
            fixSql.add("ALTER TABLE `" + schema + "`.`sys_user` ADD COLUMN `nickname` varchar(64) NULL COMMENT '昵称'");
        }
        if (columnExists(schema, "sys_user", "password_hash")) {
            fixSql.add("UPDATE `" + schema + "`.`sys_user` SET `password` = LEFT(`password_hash`, 64)"
                    + " WHERE `password` IS NULL AND `password_hash` IS NOT NULL AND `password_hash` <> ''");
        }
        for (String sql : fixSql) {
            try {
                jdbcTemplate.execute(sql);
                log.info("登录基座补列: {}", sql);
            } catch (Exception e) {
                log.warn("登录基座补列失败（不阻断）: {} - {}", sql, e.getMessage());
            }
        }
    }

    private boolean columnExists(String schema, String table, String column) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, schema, table, column);
        return c != null && c > 0;
    }

    /** gen：一键生成（自检→备份→写盘→菜单→路由），自检不过直接失败 */
    private String stepGen(GenPipeline pipeline) {
        List<GenTable> tables = tableService.lambdaQuery()
                .eq(GenTable::getProjectId, pipeline.getProjectId()).list();
        if (tables.isEmpty()) {
            throw new IllegalStateException("没有可生成的表配置");
        }
        List<Long> ids = tables.stream().map(GenTable::getId).toList();
        GenApplyDTO dto = new GenApplyDTO();
        dto.setIds(ids);
        dto.setOverwrite(true);
        dto.setCompile(false);       // 打包步骤会整包编译，这里不做重复编译
        dto.setRegisterMenu(true);
        dto.setSyncRoute(true);
        dto.setSkipCheck(false);
        dto.setDryRun(false);
        var vo = applyService.apply(dto);
        // V5.0 修复：apply 在自检不通过时只返回 passed=false（不抛异常），
        // 此前这里从不校验，把「写盘 0 个文件」包装成自检通过继续往下跑，
        // 直到打包步才以 CreateProcess error=267 的形式炸出来。
        if (!Boolean.TRUE.equals(vo.getPassed())) {
            StringBuilder detail = new StringBuilder("自检未通过，未写盘：");
            var stages = vo.getStages() == null ? List.<GenApplyStageVO>of() : vo.getStages();
            for (GenApplyStageVO stage : stages) {
                if ("CHECK".equals(stage.getStage()) && stage.getMessage() != null) {
                    detail.append(stage.getMessage());
                    break;
                }
            }
            var check = vo.getSelfCheck();
            if (check != null && check.getIssues() != null) {
                long errors = check.getIssues().stream()
                        .filter(i -> "ERROR".equals(i.getSeverity())).count();
                detail.append("（").append(errors).append(" 个编译错误，首条：");
                check.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity()))
                        .findFirst().ifPresent(i -> detail.append(i.getFile()).append(" - ")
                                .append(abbreviate(i.getMessage(), 160)));
                detail.append("）");
            }
            throw new IllegalStateException(detail.toString());
        }
        return "生成 " + tables.size() + " 张表，写盘 " + vo.getWritten() + " 个文件，自检通过";
    }

    /** package_backend：mvn package */
    private String stepPackageBackend(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        Path backendDir = Paths.get(project.getBackendRoot(), project.getProjectCode() + "-server");
        // 前置校验：工程目录/pom 缺失时给可读报错。
        // 不校验的话 ProcessBuilder 会抛 CreateProcess error=267（目录名称无效），
        // 完全看不出是「代码没生成」还是「根目录配错」。
        if (!Files.exists(backendDir.resolve("pom.xml"))) {
            throw new IllegalStateException("后端工程不存在或缺 pom.xml：" + backendDir
                    + "（请检查脚手架/代码生成步骤是否成功，以及系统台账的 backend_root 配置）");
        }
        String mvn = resolveMaven();
        CommandResult r = runCommand(pipeline, "package_backend", backendDir.toFile(),
                mvn, "-f", backendDir.resolve("pom.xml").toString(), "-DskipTests", "package");
        if (r.exitCode != 0) {
            throw new IllegalStateException("mvn package 退出码 " + r.exitCode + "，详见日志 " + r.logFile);
        }
        return "BUILD SUCCESS（" + r.durationSeconds + "s）";
    }

    /** deploy_backend：java -jar，显式 --server.port + 清理 SERVER__PORT */
    private String stepDeployBackend(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        Path target = Paths.get(project.getBackendRoot(), project.getProjectCode() + "-server", "target");
        if (!Files.isDirectory(target)) {
            throw new IllegalStateException("后端构建产物目录不存在：" + target + "（打包步骤可能未成功）");
        }
        Path jar = findJar(target);
        String java = resolveJava();

        Path log = logFile(pipeline, "deploy_backend");
        try {
            ProcessBuilder pb = new ProcessBuilder(java, "-jar", jar.toString(),
                    "--server.port=" + pipeline.getBackendPort());
            pb.directory(target.toFile());
            // 坑 #5：残留的 SERVER__PORT 优先级高于命令行，必须清掉
            pb.environment().remove("SERVER__PORT");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            Process process = pb.start();
            pipeline.setBackendPid(process.pid());
            pipelineMapper.updateById(pipeline);
        } catch (IOException e) {
            throw new IllegalStateException("后端进程启动失败：" + e.getMessage(), e);
        }
        awaitPort("后端", pipeline.getBackendPort());
        return "pid=" + pipeline.getBackendPid() + " :" + pipeline.getBackendPort();
    }

    /** package_frontend：node_modules 缺失才 npm install（幂等） */
    private String stepPackageFrontend(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        Path webDir = Paths.get(project.getFrontendRoot(), project.getProjectCode() + "-web");
        if (!Files.exists(webDir.resolve("package.json"))) {
            throw new IllegalStateException("前端工程不存在：" + webDir);
        }
        if (Files.exists(webDir.resolve("node_modules"))) {
            return "node_modules 已就绪，跳过安装";
        }
        CommandResult r = runCommand(pipeline, "package_frontend", webDir.toFile(),
                "cmd", "/c", props.getNpm(), "install", "--no-audit", "--no-fund");
        if (r.exitCode != 0) {
            throw new IllegalStateException("npm install 退出码 " + r.exitCode + "，详见日志 " + r.logFile);
        }
        return "依赖安装完成（" + r.durationSeconds + "s）";
    }

    /** deploy_frontend：npm run serve -- --port X，dev server 常驻 */
    private String stepDeployFrontend(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        Path webDir = Paths.get(project.getFrontendRoot(), project.getProjectCode() + "-web");
        if (!Files.exists(webDir.resolve("package.json"))) {
            throw new IllegalStateException("前端工程不存在：" + webDir + "（请检查脚手架步骤是否成功）");
        }
        Path log = logFile(pipeline, "deploy_frontend");
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", props.getNpm(),
                    "run", "serve", "--", "--port", String.valueOf(pipeline.getFrontendPort()));
            pb.directory(webDir.toFile());
            pb.environment().remove("SERVER__PORT");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            Process process = pb.start();
            pipeline.setFrontendPid(process.pid());
            pipelineMapper.updateById(pipeline);
        } catch (IOException e) {
            throw new IllegalStateException("前端进程启动失败：" + e.getMessage(), e);
        }
        awaitPort("前端", pipeline.getFrontendPort());
        return "pid=" + pipeline.getFrontendPid() + " :" + pipeline.getFrontendPort();
    }

    /** health：双端端口就绪 + 前端 HTTP 可访问 */
    private String stepHealth(GenPipeline pipeline) throws Exception {
        boolean backendUp = portOpen(pipeline.getBackendPort());
        if (!backendUp) {
            throw new IllegalStateException("后端端口 " + pipeline.getBackendPort() + " 未就绪");
        }
        // V5.0 修复：必须显式 HTTP_1_1。HttpClient 默认 HTTP_2 会发 h2c Upgrade，
        // webpack-dev-server（Express）对这种升级请求挂死不响应（裸 Socket 同 URI 毫秒级 200），
        // 健康检查因此永远超时——dev server 编译背锅纯属冤案
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + pipeline.getFrontendPort()))
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        // dev server 首次请求会触发 webpack 全量编译（实测 2.5min+），
        // 一次超时不算失败，重试等编译完成——健康检查要验证「最终可用」，不是「瞬时可用」。
        // 12 次 × 20s ≈ 4min 预算，覆盖冷启动全量编译
        HttpResponse<Void> resp = null;
        IllegalStateException last = null;
        for (int i = 0; i < 12; i++) {
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                break;
            } catch (java.net.http.HttpTimeoutException e) {
                last = new IllegalStateException("前端首次编译超时，重试 " + (i + 1) + "/12");
                Thread.sleep(3000);
            }
        }
        if (resp == null) {
            throw last != null ? last : new IllegalStateException("前端请求失败");
        }
        if (resp.statusCode() >= 500) {
            throw new IllegalStateException("前端 dev server 异常：HTTP " + resp.statusCode());
        }
        return "后端 " + pipeline.getBackendPort() + " 就绪 / 前端 " + resp.statusCode();
    }

    // ==================== 通用工具 ====================

    private void mark(GenPipelineStep step, String status, String message, String logExcerpt) {
        step.setStatus(status);
        if (message != null) {
            step.setMessage(abbreviate(message, 1000));
        } else if ("running".equals(status)) {
            // 重跑进入 running 时清掉上一轮的失败消息，避免进度条挂着旧报错
            step.setMessage(null);
            step.setLogExcerpt(null);
        }
        if (logExcerpt != null) {
            step.setLogExcerpt(logExcerpt);
        }
        if ("running".equals(status)) {
            step.setStartedAt(LocalDateTime.now());
        } else {
            step.setFinishedAt(LocalDateTime.now());
        }
        stepMapper.updateById(step);
    }

    private void push(GenPipeline pipeline) {
        try {
            sseRegistry.send(pipeline.getId(), assembler.toVO(pipeline, stepsOf(pipeline.getId())));
        } catch (Exception e) {
            log.warn("SSE 推送失败: {}", e.getMessage());
        }
    }

    private List<GenPipelineStep> stepsOf(Long pipelineId) {
        return stepMapper.selectList(new LambdaQueryWrapper<GenPipelineStep>()
                .eq(GenPipelineStep::getPipelineId, pipelineId)
                .orderByAsc(GenPipelineStep::getStepOrder));
    }

    private Path logFile(GenPipeline pipeline, String stepKey) {
        Path dir = Paths.get(props.getLogDir(), pipeline.getPipelineNo());
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve(stepKey + ".log");
    }

    private CommandResult runCommand(GenPipeline pipeline, String stepKey, java.io.File workDir,
                                     String... command) {
        Path log = logFile(pipeline, stepKey);
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.environment().remove("SERVER__PORT");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            Process p = pb.start();
            p.waitFor(30, TimeUnit.MINUTES);
            int exit = p.isAlive() ? -1 : p.exitValue();
            if (p.isAlive()) {
                p.destroyForcibly();
            }
            return new CommandResult(exit,
                    (System.currentTimeMillis() - start) / 1000, log.toString());
        } catch (Exception e) {
            throw new IllegalStateException("命令执行失败：" + String.join(" ", command) + " - " + e.getMessage(), e);
        }
    }

    private void awaitPort(String label, int port) {
        int timeout = Math.max(30, props.getDeployTimeoutSeconds());
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (portOpen(port)) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待被中断");
            }
        }
        throw new IllegalStateException(label + "端口 " + port + " 在 " + timeout + "s 内未就绪，请看部署日志");
    }

    private boolean portOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Path findJar(Path targetDir) {
        try (Stream<Path> files = Files.list(targetDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jar")
                            && !p.getFileName().toString().endsWith(".original"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("target 下没有可运行 jar：" + targetDir));
        } catch (IOException e) {
            throw new IllegalStateException("读取 target 目录失败：" + targetDir, e);
        }
    }

    private String resolveMaven() {
        if (StringUtils.hasText(props.getMaven())) {
            return props.getMaven();
        }
        if (StringUtils.hasText(outputProps.getMaven())) {
            return outputProps.getMaven();
        }
        return "mvn";
    }

    private String resolveJava() {
        if (StringUtils.hasText(props.getJavaHome())) {
            return Paths.get(props.getJavaHome(), "bin", "java.exe").toString();
        }
        String cmd = ProcessHandle.current().info().command().orElse("java");
        // 平台自己跑在 JRE/JDK 上即可，但目标工程要 JDK 21 编译产物，运行用同族 java 足够
        return cmd;
    }

    private String dbHost() {
        // 从平台数据源 URL 反推 host:port，脚手架 application.yml 用同一台库
        String url = dataSourceProps.getUrl();
        int i = url.indexOf("//");
        int j = url.indexOf('/', i + 2);
        return (i >= 0 && j > i) ? url.substring(i + 2, j) : "127.0.0.1:3306";
    }

    private String tailOf(Path logFile, int lines) {
        if (!Files.exists(logFile)) {
            return null;
        }
        try {
            List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            return abbreviate(String.join("\n", all.subList(from, all.size())), 1800);
        } catch (IOException e) {
            return null;
        }
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 命令执行结果 */
    record CommandResult(int exitCode, long durationSeconds, String logFile) {
    }
}
