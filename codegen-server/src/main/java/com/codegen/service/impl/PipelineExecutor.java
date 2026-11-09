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
import java.util.Map;
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

    /** 步骤 key 常量（与 gen_pipeline_step 种子一致）。V5.2 起 db 后插入 seed；V7.0 起 db 后插入 ai_recommend */
    public static final String[] STEP_KEYS = {
            "model", "db", "ai_recommend", "seed", "scaffold", "gen",
            "package_backend", "deploy_backend",
            "package_frontend", "package_mobile", "deploy_frontend",
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
    private final com.codegen.support.GenSeedGenerator seedGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.codegen.support.GenBpmnBuilder bpmnBuilder;
    private final com.codegen.service.FlowDefBpmnService flowDefBpmnService;
    private final com.codegen.service.AiRecommendService aiRecommendService;
    private final com.codegen.service.AiModelService aiModelService;
    private final com.codegen.service.GenSqlDeliverService sqlDeliverService;

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
                            com.codegen.support.GenSeedGenerator seedGenerator,
                            JdbcTemplate jdbcTemplate,
                            com.codegen.support.GenBpmnBuilder bpmnBuilder,
                            com.codegen.service.FlowDefBpmnService flowDefBpmnService,
                            com.codegen.service.AiRecommendService aiRecommendService,
                            com.codegen.service.AiModelService aiModelService,
                            com.codegen.service.GenSqlDeliverService sqlDeliverService) {
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
        this.seedGenerator = seedGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.bpmnBuilder = bpmnBuilder;
        this.flowDefBpmnService = flowDefBpmnService;
        this.aiRecommendService = aiRecommendService;
        this.aiModelService = aiModelService;
        this.sqlDeliverService = sqlDeliverService;
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
                        case "ai_recommend" -> stepAiRecommend(pipeline);
                        case "seed" -> stepSeed(pipeline);
                        case "scaffold" -> stepScaffold(pipeline);
                        case "gen" -> stepGen(pipeline);
                        case "package_backend" -> stepPackageBackend(pipeline);
                        case "deploy_backend" -> stepDeployBackend(pipeline);
                        case "package_frontend" -> stepPackageFrontend(pipeline);
                        case "package_mobile" -> stepPackageMobile(pipeline);
                        case "deploy_frontend" -> stepDeployFrontend(pipeline);
                        case "health" -> stepHealth(pipeline);
                        default -> "未知步骤，跳过";
                    };
                    mark(step, "success", message, null);
                } catch (AiRecommendDegradedException e) {
                    // V7.0 降级不阻断：AI 推荐是增强步骤，失败/未启用只标记本步，
                    // pipeline 用 V6.6 规则推断继续，整体仍可为成功（带降级标记）
                    log.warn("AI 推荐降级 {}#{}: {}", pipeline.getPipelineNo(), step.getStepKey(), e.getMessage());
                    mark(step, e.getStatus(), e.getMessage(), null);
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
            // V7.2：SQL 交付物落盘到目标工程 db/（失败只告警，不影响流水线成功状态）
            deliverSqlArtifacts(done);
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

    // ==================== SQL 交付物落盘（V7.2） ====================

    /**
     * 流水线成功后，把业务库三份 SQL 交付物复制到目标工程 {@code db/} 目录：
     * <ul>
     *   <li>{@code init_tables.sql} ← 平台日志目录的 db.sql（业务表 DDL）</li>
     *   <li>{@code init_seed.sql} ← 平台日志目录的 seed.sql（种子数据，可人工重放）</li>
     *   <li>{@code init_menu.sql} ← {backendRoot}/.gen-artifacts/&lt;最新时间戳&gt;/menu.sql（菜单注册）</li>
     * </ul>
     * 此前三份产物只分别留在平台日志目录与 .gen-artifacts，目标工程 db/ 只有
     * 基础架构脚本（dict/init_sys_user/init_sys_flow），交付后无法从零初始化业务库。
     * .gen-artifacts 取 GenMenuProperties.artifactDir 的默认值（.gen-artifacts）。
     * 全程不阻断：任一落盘失败只 warn，流水线仍算成功。
     */
    private void deliverSqlArtifacts(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        if (project == null) {
            log.warn("SQL 交付物落盘跳过：系统台账不存在 pipeline={}", pipeline.getPipelineNo());
            return;
        }
        Path logDir = logFile(pipeline, "db").getParent();
        List<com.codegen.service.GenSqlDeliverService.SqlArtifact> artifacts = new ArrayList<>();
        readArtifact(logDir.resolve("db.sql"), "init_tables.sql", artifacts);
        readArtifact(logDir.resolve("seed.sql"), "init_seed.sql", artifacts);
        readArtifact(latestMenuSql(Paths.get(project.getBackendRoot(), ".gen-artifacts")),
                "init_menu.sql", artifacts);
        if (artifacts.isEmpty()) {
            return;
        }
        // V7.3 P0-2：落盘/备份逻辑收敛到 GenSqlDeliverService（与手动写盘、下载 ZIP 共用）
        var vo = sqlDeliverService.deliver(pipeline.getProjectId(), pipeline.getPipelineNo(), artifacts);
        if (vo.getErrors() > 0) {
            log.warn("SQL 交付物部分落盘失败 {}: {} 个错误，目标目录 {}", pipeline.getPipelineNo(),
                    vo.getErrors(), vo.getDbDir());
        } else if (vo.getWritten() > 0) {
            log.info("SQL 交付物已落盘到目标工程 db/: {} -> {} ({} 个文件)", pipeline.getPipelineNo(),
                    vo.getDbDir(), vo.getWritten());
        }
    }

    /**
     * 读取平台侧 SQL 交付源文件；不存在或读取失败只告警，不影响流水线成功状态。
     */
    private void readArtifact(Path from, String fileName,
                              List<com.codegen.service.GenSqlDeliverService.SqlArtifact> out) {
        if (from == null || !Files.exists(from)) {
            return;
        }
        try {
            out.add(new com.codegen.service.GenSqlDeliverService.SqlArtifact(
                    fileName, Files.readString(from, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("SQL 交付源读取失败 {}: {}", from, e.getMessage());
        }
    }

    /** 取 .gen-artifacts 下最新时间戳目录里的 menu.sql（目录名 yyyyMMddHHmmss，字典序即时间序）。 */
    private Path latestMenuSql(Path artifactRoot) {
        if (!Files.isDirectory(artifactRoot)) {
            return null;
        }
        try (var dirs = Files.list(artifactRoot)) {
            return dirs.filter(Files::isDirectory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(d -> d.resolve("menu.sql"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("menu.sql 检索失败 {}: {}", artifactRoot, e.getMessage());
            return null;
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

    /**
     * ai_recommend（V7.0）：表结构就绪后、模板生成前，AI 推荐三层页面配置。
     *
     * <p>纯增强步骤，产物只落 ai_recommend.json（不动 gen_table），
     * 用户在前端 diff 预览勾选应用后才写库。两种降级都不阻断流水线：
     * ai.enabled=false → skipped；AI 超时/失败/JSON 截断 → failed-degraded，
     * 后续步骤照走 V6.6 规则推断，产物与 V6.8 行为一致。
     */
    String stepAiRecommend(GenPipeline pipeline) {
        if (!props.getAi().isEnabled()) {
            throw new AiRecommendDegradedException("skipped", "AI 未启用，按 V6.6 规则推断继续");
        }
        try {
            com.codegen.vo.GenAiRecommendVO vo = aiRecommendService.recommend(pipeline.getProjectId());
            Path artifact = logFile(pipeline, "ai_recommend").getParent().resolve("ai_recommend.json");
            Files.writeString(artifact,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vo),
                    StandardCharsets.UTF_8);
            int columnSuggests = vo.getTables().stream().mapToInt(t -> t.getColumns().size()).sum();
            return "建议：列级 " + columnSuggests + " 项 / 表级 " + vo.getTables().size()
                    + " 表 / 菜单 " + vo.getMenus().size() + " 项"
                    + (vo.getDrops().isEmpty() ? "" : "，丢弃非法项 " + vo.getDrops().size())
                    + "，产物 ai_recommend.json";
        } catch (AiRecommendDegradedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 推荐失败降级 {}: {}", pipeline.getPipelineNo(), e.getMessage());
            throw new AiRecommendDegradedException("failed-degraded",
                    "AI 推荐失败，已降级规则推断：" + abbreviate(e.getMessage(), 300));
        }
    }

    /**
     * V7.0 ai_recommend 步骤的降级信号：携带步骤终态（skipped / failed-degraded），
     * 执行器捕获后标记本步并继续流水线，不把增强步骤的失败升级成整线失败。
     */
    static final class AiRecommendDegradedException extends RuntimeException {
        private final String status;

        AiRecommendDegradedException(String status, String message) {
            super(message);
            this.status = status;
        }

        String getStatus() {
            return status;
        }
    }

    /**
     * seed（V5.2）：按 seed_mode 规则生成示例数据；off/NULL 直接成功跳过。
     * 失败不阻断整条流水线是错误设计——示例数据缺失会让生成系统开箱即空，
     * 但插数失败通常是脏数据/权限问题，重跑流水线可恢复，所以这里照常抛出。
     */
    private String stepSeed(GenPipeline pipeline) {
        if (!"rule".equals(pipeline.getSeedMode()) && !"ai".equals(pipeline.getSeedMode())) {
            return "已跳过（未启用数据初始化）";
        }
        // V7.1：ai 模式先用 AI 生成业务值池；任何失败都回落规则词库（不换种子行数/结构约束）
        com.codegen.support.SeedValuePools pools = com.codegen.support.SeedValuePools.defaults();
        String suffix = "";
        if ("ai".equals(pipeline.getSeedMode())) {
            try {
                var metas = seedGenerator.loadTableMeta(pipeline.getDbName());
                pools = aiModelService.seedPools(pipeline.getSystemName(), pipeline.getSystemDesc(), metas);
                suffix = "（AI 值池已应用）";
                log.info("seed AI 值池生成成功: pipeline={} tables={}", pipeline.getPipelineNo(), metas.size());
            } catch (Exception e) {
                // 降级原则同 V7.0 ai_recommend：AI 失败不阻断 seed，产物与 rule 模式一致
                log.warn("seed AI 值池失败，回落规则词库: pipeline={} {}: {}",
                        pipeline.getPipelineNo(), e.getClass().getSimpleName(), e.getMessage());
                suffix = "；AI 增强失败已回落规则词库（" + abbreviate(e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage(), 80) + "）";
            }
        }
        var vo = seedGenerator.seedPipeline(pipeline, pools);
        return vo.getSummary() + suffix;
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
        // V6.1：流程引擎与定义下发——流程定义在平台库，产物库不直连，靠 scaffold 的 db 脚本带过去
        dto.setFlowEngine(project.getFlowEngine());
        dto.setFlowDefs(collectFlowDefs(project.getId()));
        // V6.3：支付能力包开关（回落唯一实现 resolvePay）
        dto.setPayEnabled(com.codegen.util.GenTemplateModel.resolvePay(project.getPayEnabled()));
        // V6.5：移动端工程开关（回落唯一实现 resolveMobile）
        dto.setMobileEnabled(com.codegen.util.GenTemplateModel.resolveMobile(project.getMobileEnabled()));
        // V6.6：文件能力包开关（回落唯一实现 resolveFile）
        dto.setFileEnabled(com.codegen.util.GenTemplateModel.resolveFile(project.getFileEnabled()));
        // V6.8：品牌三项原样下发（回落统一在 GenTemplateModel.resolveAppName/resolveThemeColor，
        // 这里只搬运，不重复实现回落逻辑）
        dto.setAppName(project.getAppName());
        dto.setAppLogo(project.getAppLogo());
        dto.setAppThemeColor(project.getAppThemeColor());
        // V6.7：测试骨架开关（回落唯一实现 resolveTest，缺省开——只写 src/test 零运行时侵入）
        dto.setTestEnabled(com.codegen.util.GenTemplateModel.resolveTest(project.getTestEnabled()));
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
        // V6.1：流程表 DDL + 定义 INSERT 导入产物库（幂等；activiti 定义带 bpmn_xml，产物启动时自动部署）
        Path flowSql = Paths.get(project.getBackendRoot(), dto.getBackendModule(), "db", "init_sys_flow.sql");
        if (Files.exists(flowSql)) {
            try {
                GenDdlExecuteDTO flowDto = new GenDdlExecuteDTO();
                flowDto.setDdl(Files.readString(flowSql, StandardCharsets.UTF_8));
                flowDto.setSchema(pipeline.getDbName());
                flowDto.setAutoImport(false);
                var fr = ddlService.execute(flowDto);
                log.info("流程表 SQL 已导入 {}: {} 条语句", pipeline.getDbName(), fr.getSuccessCount());
            } catch (Exception e) {
                log.warn("流程表 SQL 导入失败（不阻断）: {}", e.getMessage());
            }
        }
        // V6.3：pay_order 支付单表导入产物库（幂等）
        Path paySql = Paths.get(project.getBackendRoot(), dto.getBackendModule(), "db", "pay.sql");
        if (Files.exists(paySql)) {
            try {
                GenDdlExecuteDTO payDto = new GenDdlExecuteDTO();
                payDto.setDdl(Files.readString(paySql, StandardCharsets.UTF_8));
                payDto.setSchema(pipeline.getDbName());
                payDto.setAutoImport(false);
                var pr = ddlService.execute(payDto);
                log.info("支付单表 SQL 已导入 {}: {} 条语句", pipeline.getDbName(), pr.getSuccessCount());
            } catch (Exception e) {
                log.warn("支付单表 SQL 导入失败（不阻断）: {}", e.getMessage());
            }
        }
        // V6.6：sys_file 文件元数据表导入产物库（幂等）。
        // 缺这张表时上传接口插库即 500——比支付单表更「一用就炸」，失败要把原因带回步骤结果
        String fileNote = "";
        Path fileSql = Paths.get(project.getBackendRoot(), dto.getBackendModule(), "db", "file.sql");
        if (Files.exists(fileSql)) {
            try {
                GenDdlExecuteDTO fileDto = new GenDdlExecuteDTO();
                fileDto.setDdl(Files.readString(fileSql, StandardCharsets.UTF_8));
                fileDto.setSchema(pipeline.getDbName());
                fileDto.setAutoImport(false);
                var fr = ddlService.execute(fileDto);
                log.info("文件表 SQL 已导入 {}: {} 条语句", pipeline.getDbName(), fr.getSuccessCount());
            } catch (Exception e) {
                log.warn("文件表 SQL 导入失败: {}", e.getMessage());
                fileNote = "（sys_file 建表失败：" + abbreviate(e.getMessage(), 80) + "，上传功能将不可用）";
            }
        } else if (Boolean.TRUE.equals(dto.getFileEnabled())) {
            fileNote = "（未找到 db/file.sql，sys_file 缺失时上传功能不可用）";
        }
        return "写入 " + vo.getWritten() + " 个文件，跳过 " + vo.getSkipped() + " " + seedNote + fileNote;
    }

    /**
     * V6.1：收集本系统绑定流程的定义（gen_table.flow_code → sys_flow_def），
     * activiti 引擎时顺带用 GenBpmnBuilder 翻译 bpmn_xml 一起下发。
     * 组装查询唯一实现在 {@link FlowDefBpmnService#listForProject}，这里只补 bpmn 现译。
     */
    private List<com.codegen.dto.GenScaffoldDTO.FlowDef> collectFlowDefs(Long projectId) {
        List<Map<String, Object>> rows = flowDefBpmnService.listForProject(projectId);
        boolean activiti = "activiti".equalsIgnoreCase(
                projectService.getById(projectId).getFlowEngine() == null ? ""
                        : projectService.getById(projectId).getFlowEngine().trim());
        List<com.codegen.dto.GenScaffoldDTO.FlowDef> defs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            var fd = new com.codegen.dto.GenScaffoldDTO.FlowDef();
            fd.setFlowCode((String) r.get("flowCode"));
            fd.setFlowName((String) r.get("flowName"));
            fd.setTableName((String) r.get("tableName"));
            fd.setStatusCol((String) r.get("statusCol"));
            fd.setNodes((List<Map<String, Object>>) r.get("nodes"));
            if (activiti && fd.getNodes() != null && !fd.getNodes().isEmpty() && r.get("bpmnXml") == null) {
                try {
                    fd.setBpmnXml(bpmnBuilder.build(fd.getFlowCode(), fd.getFlowName(), fd.getNodes()));
                } catch (Exception e) {
                    log.warn("流程 {} BPMN 翻译失败（定义仍下发，产物启动时缺 xml 不部署）: {}",
                            fd.getFlowCode(), e.getMessage());
                }
            } else if (r.get("bpmnXml") != null) {
                fd.setBpmnXml((String) r.get("bpmnXml"));
            }
            defs.add(fd);
        }
        return defs;
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

    /**
     * package_mobile：V6.5 移动端出包（build:h5 静态包 + build:mp-weixin 小程序目录）。
     * 未开启 mobile_enabled 时优雅跳过（步骤恒在种子清单里，开关决定它干活还是让路）；
     * CI 只出包不发布——小程序真机预览/上传由用户在微信开发者工具完成（诚实边界）。
     */
    private String stepPackageMobile(GenPipeline pipeline) {
        var project = projectService.getById(pipeline.getProjectId());
        if (!com.codegen.util.GenTemplateModel.resolveMobile(project.getMobileEnabled())) {
            return "未开启 mobile_enabled，跳过移动端构建";
        }
        Path mobileDir = Paths.get(project.getFrontendRoot(), project.getProjectCode() + "-web-mobile");
        if (!Files.exists(mobileDir.resolve("package.json"))) {
            throw new IllegalStateException("移动端工程不存在：" + mobileDir + "（请检查脚手架步骤是否成功）");
        }
        if (!Files.exists(mobileDir.resolve("node_modules"))) {
            CommandResult r = runCommand(pipeline, "package_mobile", mobileDir.toFile(),
                    "cmd", "/c", props.getNpm(), "install", "--no-audit", "--no-fund");
            if (r.exitCode != 0) {
                throw new IllegalStateException("npm install 退出码 " + r.exitCode + "，详见日志 " + r.logFile);
            }
        }
        CommandResult h5 = runCommand(pipeline, "package_mobile", mobileDir.toFile(),
                "cmd", "/c", props.getNpm(), "run", "build:h5");
        if (h5.exitCode != 0) {
            throw new IllegalStateException("build:h5 退出码 " + h5.exitCode + "，详见日志 " + h5.logFile);
        }
        CommandResult mp = runCommand(pipeline, "package_mobile", mobileDir.toFile(),
                "cmd", "/c", props.getNpm(), "run", "build:mp-weixin");
        if (mp.exitCode != 0) {
            throw new IllegalStateException("build:mp-weixin 退出码 " + mp.exitCode + "，详见日志 " + mp.logFile);
        }
        if (!Files.exists(mobileDir.resolve(Paths.get("dist", "build", "mp-weixin", "app.json")))) {
            throw new IllegalStateException("小程序包缺 app.json，出包可能不完整：" + mobileDir + "/dist/build/mp-weixin");
        }
        return "移动端出包完成：H5 静态包 + mp-weixin 目录（" + (h5.durationSeconds + mp.durationSeconds) + "s）";
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
