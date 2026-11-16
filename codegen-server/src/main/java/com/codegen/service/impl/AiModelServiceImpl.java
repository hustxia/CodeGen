package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenPipelineProperties;
import com.codegen.service.AiModelService;
import com.codegen.support.TableSkeletonExpander;
import com.codegen.vo.GenPipelineAiModulesVO;
import com.codegen.vo.GenPipelineAiVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 建模实现：OpenAI 兼容 /chat/completions，响应要求纯 JSON。
 *
 * <p>为什么不引 SDK：只发一个 chat 请求，JDK 自带 HttpClient 就够，
 * 少一个依赖就少一个升级坑。超时按配置（默认 600s，本地 CPU 推理一次 3-5 分钟），
 * 超时即失败可重试。
 *
 * <p>Prompt 硬约束：snake_case 命名、只给业务列（审计列服务端补）、
 * 输出纯 JSON——任何多余的话都会让解析挂掉。
 *
 * <p>qwen3 适配（2026-10-25，见坑库 N+6）：/no_think 放 user 消息尾部才生效
 * （放 system 无效）；思考 token 在纯 CPU 上会把请求拖到超时。
 */
@Slf4j
@Service
public class AiModelServiceImpl implements AiModelService {

    private static final String SYSTEM_PROMPT = """
            你是资深的领域建模专家。根据用户给出的需求，设计 MySQL 表结构。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"tables":[{"tableName":"xxx","tableComment":"中文注释","columns":[{"name":"xxx","type":"varchar(64)","comment":"中文注释","pk":false}]}]}
            3. 表名/列名用小写 snake_case；每张表恰好一个主键列（pk=true，类型 bigint）。
            4. 只给业务列。不要输出 id 之外的通用列（create_time/update_time/create_by/update_by/deleted 由系统自动补）。
            5. 类型只用：bigint、int、varchar(N)、decimal(M,N)、text、datetime、date、tinyint。
            6. 所有 comment 用中文。
            7. 直接给出结果，不要逐步推理。/no_think
            """;

    /**
     * V9.7 两段式骨架 prompt：只要表名/注释/字段名+注释，不要类型与 pk。
     * 输出量是完整建模的 1/3 左右——本地 4B 模型一次模块建模从 3 分钟压到 1 分钟内；
     * 类型/主键由 {@code TableSkeletonExpander} 按命名语义规则补全（规则比小模型猜得稳）。
     */
    private static final String SKELETON_SYSTEM_PROMPT = """
            你是资深的领域建模专家。根据用户给出的需求，列出 MySQL 表的字段骨架。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"tables":[{"tableName":"xxx","tableComment":"中文注释","fields":[{"name":"xxx","comment":"中文注释"}]}]}
            3. 表名/字段名用小写 snake_case；只列业务字段，不要输出 id 及审计列（create_time/update_time/create_by/update_by/deleted 由系统自动补）。
            4. 不要输出 type/pk/长度等任何类型信息——字段类型由系统按命名规则自动补全。
            5. 每张表 6-15 个业务字段；所有 comment 用中文。
            6. 直接给出结果，不要逐步推理。/no_think
            """;

    /**
     * V9.4 修复「推荐模块与输入系统不相关」：
     * 旧版把「用户管理/账号管理/角色管理/权限管理」写成具体示例，本地 7B 模型在
     * response_format=json_object 的语法约束下极易直接照抄示例；又强制 8-12 个模块且
     * 必带「消息通知/操作日志/统计报表/系统配置」等通用支撑域，进一步稀释领域相关度。
     * 现在只给字段格式（示例值用占位符），并把「围绕该系统自己的业务对象」写成硬约束。
     */
    private static final String MODULES_SYSTEM_PROMPT = """
            你是资深的系统架构师。请只为「用户给出的那一个系统」拆分业务模块。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 字段格式（尖括号内必须换成该系统自己的业务对象，严禁照抄）：
               {"modules":[{"name":"<业务对象>管理","desc":"<该系统相关的一句话职责>","functions":["<业务动作1>","<业务动作2>","<业务动作3>"]}]}
               这里只描述字段结构，不是任何系统的答案；不要输出任何示例中的占位文字。
            3. 第一步先读懂系统名称与描述，识别该系统的核心业务对象与业务动作
               （例：「图书馆借阅系统」→ 图书、读者、借阅、归还、逾期；「宠物领养系统」→ 宠物、领养人、申请、回访），
               再围绕这些对象拆分 5-10 个模块。至少三分之二必须是核心业务模块，
               模块名、desc、functions 里必须出现该系统自己的业务名词，不能只写「XX管理」这种空壳词。
            4. 通用支撑模块最多 0-2 个，且必须写清它在该系统里的具体用途；
               严禁输出与本系统无关的通用后台模块：用户管理、角色权限、账号管理、菜单管理、
               数据字典、参数配置、系统配置、消息通知、操作日志、统计报表、登录日志、首页大盘。
            5. 系统描述为空时，只能按系统名称推断业务域，绝不回退成通用后台模板。
            6. name 用中文短词，desc 一句话中文，functions 列 3-6 条该模块的具体功能点（中文短词）。
            7. 直接给出结果，不要逐步推理。/no_think
            """;

    /** 通用后台模板词：命中过多说明模型没在为该领域建模，用于结果侧相关性护栏 */
    private static final Set<String> GENERIC_MODULE_WORDS = Set.of(
            "用户管理", "角色管理", "权限管理", "账号管理", "菜单管理", "部门管理", "岗位管理",
            "数据字典", "字典管理", "参数配置", "系统配置", "消息通知", "操作日志", "登录日志",
            "统计报表", "报表导出", "定时任务", "文件管理", "首页大盘", "系统监控");

    /** 系统名/描述里的通用词，提取领域词前先剔除，避免「管理系统」被当成领域特征 */
    private static final Set<String> DOMAIN_STOP_WORDS = Set.of(
            "管理系统", "系统", "平台", "管理", "信息", "服务", "业务", "应用", "软件", "模块",
            "一套", "一个", "支持", "实现", "包括", "以及", "进行", "相关", "功能");

    @Autowired
    private GenPipelineProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenPipelineAiVO model(String systemName, String systemDesc, String moduleName) {
        String userContent;
        if (StringUtils.hasText(moduleName)) {
            // 完整系统模式：只设计指定模块的表，单次输出预算可控（~1000 token），本地 CPU 3-4 分钟
            userContent = "系统名称：" + systemName
                    + "\n系统描述：" + (systemDesc == null ? systemName : systemDesc)
                    + "\n本次只设计「" + moduleName + "」这个模块的表：设计 6-8 张该模块的核心业务表，不要跨模块。"
                    + "\n/no_think";
        } else {
            userContent = "系统名称：" + systemName
                    + "\n一句话描述：" + (systemDesc == null ? systemName : systemDesc)
                    + "\n设计 3-5 张核心表。"
                    + "\n/no_think";
        }
        String json = chatJson(SYSTEM_PROMPT, userContent);
        return parseTables(json);
    }

    @Override
    public GenPipelineAiModulesVO modules(String systemName, String systemDesc) {
        String desc = StringUtils.hasText(systemDesc) ? systemDesc.trim() : "";
        String json = chatJson(MODULES_SYSTEM_PROMPT, buildModulesUserContent(systemName, desc));
        GenPipelineAiModulesVO vo = parseModules(json);
        assessRelevance(systemName, desc, vo);
        return vo;
    }

    /** V9.4：把系统名/描述结构化、重复注入，避免小模型只读到 system prompt 后套通用模板 */
    static String buildModulesUserContent(String systemName, String desc) {
        String name = systemName == null ? "" : systemName.trim();
        String text = StringUtils.hasText(desc) ? desc.trim() : "";
        return "请只针对下面这一个系统拆分业务模块，不要输出通用后台模块。"
                + "\n系统名称：" + name
                + "\n系统描述：" + (text.isEmpty() ? "（未提供描述，请仅根据系统名称推断该系统的业务域）" : text)
                + "\n再次强调：所有模块名、职责说明与功能点都必须围绕「" + name + "」自己的业务对象展开；"
                + "与该系统无关的模块一律不要输出。"
                + "\n/no_think";
    }

    /**
     * 结果侧护栏：模型偶尔仍会套通用后台模板（尤其是本地 7B 模型）。
     * 领域词零命中且通用模板模块占比过高 → 直接拦截，绝不静默把无关清单展示给用户；
     * 命中率偏低 → 只标记告警，仍允许用户在第②步编辑、勾选或重新生成。
     */
    private void assessRelevance(String systemName, String desc, GenPipelineAiModulesVO vo) {
        List<String> tokens = domainTokens(systemName, desc);
        if (tokens.isEmpty() || vo.getModules().isEmpty()) {
            return; // 纯英文缩写等无法提取领域词时不做判断，避免误伤
        }
        int domainHits = 0;
        int genericHits = 0;
        for (GenPipelineAiModulesVO.Module m : vo.getModules()) {
            StringBuilder text = new StringBuilder(m.getName() == null ? "" : m.getName());
            if (m.getDesc() != null) {
                text.append(' ').append(m.getDesc());
            }
            for (String fn : m.getFunctions()) {
                text.append(' ').append(fn);
            }
            String joined = text.toString();
            if (tokens.stream().anyMatch(joined::contains)) {
                domainHits++;
            }
            boolean generic = GENERIC_MODULE_WORDS.contains(m.getName() == null ? "" : m.getName().trim())
                    || m.getFunctions().stream().anyMatch(f -> GENERIC_MODULE_WORDS.contains(f.trim()));
            if (generic) {
                genericHits++;
            }
        }
        int total = vo.getModules().size();
        if (domainHits == 0 && (double) genericHits / total >= 1.0 / 3) {
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 返回的模块与「" + systemName + "」不相关（疑似套用了通用后台模板），已拦截。"
                            + "请补充更具体的一句业务描述后重试，或改用「手动粘贴 DDL」");
        }
        if ((double) domainHits / total < 1.0 / 3) {
            vo.setRelevanceWarning(true);
            vo.setRelevanceTip("AI 生成的模块与「" + systemName
                    + "」关联度偏低，建议补充更具体的业务描述后重新生成，或直接编辑清单。");
        }
    }

    /** 从系统名/描述提取领域特征词；中文长词再拆 2 字连续片段，提升「图书馆借阅」→「图书/借阅」命中率 */
    private List<String> domainTokens(String systemName, String systemDesc) {
        String raw = (systemName == null ? "" : systemName) + " " + (systemDesc == null ? "" : systemDesc);
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : raw.split("[^\\u4e00-\\u9fa5A-Za-z0-9]+")) {
            String p = part;
            for (String stop : DOMAIN_STOP_WORDS) {
                p = p.replace(stop, "");
            }
            if (p.length() < 2) {
                continue;
            }
            tokens.add(p);
            if (p.matches("[\\u4e00-\\u9fa5]{3,}")) {
                for (int i = 0; i + 2 <= p.length(); i++) {
                    tokens.add(p.substring(i, i + 2));
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    // ==================== V7.1 种子数据 AI 增强 ====================

    private static final String SEED_SYSTEM_PROMPT = """
            你是业务数据专家。根据系统名称、描述和表结构，为示例种子数据生成业务贴合的候选值。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"persons":["..."],"companies":["..."],"cities":["..."],"streets":["..."],"depts":["..."],"products":["..."],"titles":["..."],"remarks":["..."],"specs":["..."],"columns":{"表名.列名":["值",...]}}
            3. products 是关键：填该系统业务域内真实可信的商品/物料/业务对象名称（商城出数码家电、医院出药品耗材、餐饮出菜品），绝不能用通用的"不锈钢板材"之类。
            4. companies/depts/titles/remarks/specs 也按该系统的行业习惯拟。
            5. columns 只给核心业务列（品名/标题/名称类），键必须用给出的"表名.列名"原文，每列 8-16 个值。
            6. 每个列表 8-16 项，简短中文；不要输出编号/金额/时间/手机号——这些由系统按格式规则生成。
            7. 直接给出结果，不要逐步推理。/no_think
            """;

    /** 单次调用的表数上限：控制输出预算，本地小模型也不截断 */
    private static final int SEED_BATCH_TABLES = 15;
    /** 提示词里每表最多列出的列数（列太多会稀释重点） */
    private static final int SEED_COLUMNS_PER_TABLE = 12;

    @Override
    public com.codegen.support.SeedValuePools seedPools(String systemName, String systemDesc,
                                                        List<com.codegen.support.GenSeedGenerator.SeedTableMeta> tables) {
        if (tables == null || tables.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "无表元数据，跳过 AI 值池");
        }
        // 分批调用并合并：各类别拼接去重，列级覆盖直接合并
        List<String> persons = new ArrayList<>(), companies = new ArrayList<>(), cities = new ArrayList<>(),
                streets = new ArrayList<>(), depts = new ArrayList<>(), products = new ArrayList<>(),
                titles = new ArrayList<>(), remarks = new ArrayList<>(), specs = new ArrayList<>();
        Map<String, List<String>> columns = new LinkedHashMap<>();

        for (int from = 0; from < tables.size(); from += SEED_BATCH_TABLES) {
            List<com.codegen.support.GenSeedGenerator.SeedTableMeta> batch =
                    tables.subList(from, Math.min(from + SEED_BATCH_TABLES, tables.size()));
            StringBuilder user = new StringBuilder();
            user.append("系统名称：").append(systemName == null ? "" : systemName)
                    .append("\n系统描述：").append(systemDesc == null || systemDesc.isBlank() ? systemName : systemDesc)
                    .append("\n表结构（表名(注释): 列名(注释), ...）：\n");
            for (var t : batch) {
                user.append("- ").append(t.table()).append("(")
                        .append(abbrev(t.tableComment(), 40)).append("): ");
                StringBuilder cols = new StringBuilder();
                for (var c : t.columns()) {
                    if (cols.length() > 0) {
                        cols.append(", ");
                    }
                    cols.append(c.name()).append("(").append(abbrev(c.comment(), 20)).append(")");
                    if (cols.length() > 300) {
                        break; // 列过多的表只取前若干列，保输出预算
                    }
                }
                user.append(cols).append("\n");
            }
            user.append("/no_think");
            String json = chatJson(SEED_SYSTEM_PROMPT, user.toString());
            JsonNode root;
            try {
                root = objectMapper.readTree(json);
            } catch (Exception e) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "AI 值池 JSON 解析失败：" + e.getMessage());
            }
            mergeAll(root, persons, companies, cities, streets, depts, products, titles, remarks, specs);
            JsonNode colsNode = root.path("columns");
            if (colsNode.isObject()) {
                colsNode.fields().forEachRemaining(e -> {
                    List<String> vals = new ArrayList<>();
                    e.getValue().forEach(v -> vals.add(v.asText("")));
                    columns.merge(e.getKey(), vals, (a, b) -> {
                        a.addAll(b);
                        return a;
                    });
                });
            }
        }
        return com.codegen.support.SeedValuePools.of(persons, companies, cities, streets, depts,
                products, titles, remarks, specs, columns);
    }

    /** 合并一批 AI 输出的九个类别池（逐字段拼接，去重交给 SeedValuePools.clean） */
    private void mergeAll(JsonNode root, List<String>... lists) {
        String[] keys = {"persons", "companies", "cities", "streets", "depts",
                "products", "titles", "remarks", "specs"};
        for (int i = 0; i < keys.length && i < lists.length; i++) {
            JsonNode arr = root.path(keys[i]);
            if (arr.isArray()) {
                List<String> target = lists[i];
                arr.forEach(v -> target.add(v.asText("")));
            }
        }
    }

    private static String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 发一次 chat 请求并提取回复中的纯 JSON 串。
     */
    private String chatJson(String systemPrompt, String userContent) {
        GenPipelineProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || !StringUtils.hasText(ai.getBaseUrl()) || !StringUtils.hasText(ai.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建模未启用（gen.pipeline.ai.enabled=false 或未配置 key），请改用「手动粘贴 DDL」");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ai.getModel());
        // 0.2 会在 JSON 语法约束下陷入重复循环（Ollama 报 token repeat limit aborted），0.6 实测稳定
        body.put("temperature", 0.6);
        // OpenAI 兼容层的 JSON 模式：Ollama 走 llama.cpp 语法约束，从根上避免小模型输出非法 JSON
        // （qwen2.5:7b 实测不加时数组里会混裸字符，Jackson 解析必挂）；云端 OpenAI 也接受该字段
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));

        String resp;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(ai.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, ai.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String detail = response.body() == null ? "" : response.body();
                if (detail.length() > 200) {
                    detail = detail.substring(0, 200);
                }
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "AI 请求失败：HTTP " + response.statusCode() + " " + detail);
            }
            resp = response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // ConnectException 等的 message 可能为 null，必须带上异常类名才有排查价值
            log.warn("AI 请求失败: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 请求失败（超时或网络异常），可重试或改用「手动粘贴 DDL」");
        }

        try {
            JsonNode root = objectMapper.readTree(resp);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("回复为空");
            }
            return extractJson(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 回复提取失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 回复提取失败，可重试");
        }
    }

    @Override
    public GenPipelineAiVO modelSkeleton(String systemName, String systemDesc, String moduleName) {
        String userContent;
        if (StringUtils.hasText(moduleName)) {
            userContent = "系统名称：" + systemName
                    + "\n系统描述：" + (systemDesc == null ? systemName : systemDesc)
                    + "\n本次只列「" + moduleName + "」这个模块的表骨架：6-8 张该模块的核心业务表，不要跨模块。"
                    + "\n/no_think";
        } else {
            userContent = "系统名称：" + systemName
                    + "\n一句话描述：" + (systemDesc == null ? systemName : systemDesc)
                    + "\n列 3-5 张核心表的字段骨架。"
                    + "\n/no_think";
        }
        String json = chatJson(SKELETON_SYSTEM_PROMPT, userContent);
        List<GenPipelineAiVO.TableModel> full = TableSkeletonExpander.expand(parseSkeleton(json));
        if (full.isEmpty()) {
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 骨架建模结果解析失败，可重试或改用「手动粘贴 DDL」");
        }
        GenPipelineAiVO vo = new GenPipelineAiVO();
        vo.setTables(full);
        return vo;
    }

    /** 解析骨架输出：fields 支持对象 {name,comment} 与纯字符串两种形态（小模型不守格式时兜底） */
    List<GenPipelineAiVO.TableModel> parseSkeleton(String json) {
        try {
            JsonNode tables = objectMapper.readTree(json).path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new IllegalStateException("tables 为空");
            }
            List<GenPipelineAiVO.TableModel> skeletons = new ArrayList<>();
            for (JsonNode t : tables) {
                GenPipelineAiVO.TableModel tm = new GenPipelineAiVO.TableModel();
                tm.setTableName(firstText(t, "tableName", "table_name"));
                tm.setTableComment(firstText(t, "tableComment", "table_comment", "comment"));
                JsonNode fields = t.has("fields") ? t.path("fields") : t.path("columns");
                if (fields.isArray()) {
                    for (JsonNode f : fields) {
                        GenPipelineAiVO.Column col = new GenPipelineAiVO.Column();
                        if (f.isTextual()) {
                            // 纯字符串形态："user_name 昵称" / "user_name" —— 名与注释同串时拆开
                            String text = f.asText().trim();
                            int sep = text.indexOf(' ');
                            if (sep > 0) {
                                col.setName(text.substring(0, sep));
                                col.setComment(text.substring(sep + 1).trim());
                            } else {
                                col.setName(text);
                                col.setComment("");
                            }
                        } else if (f.isObject()) {
                            col.setName(firstText(f, "name", "field", "column"));
                            col.setComment(firstText(f, "comment", "desc", "description"));
                        } else {
                            continue;
                        }
                        tm.getColumns().add(col);
                    }
                }
                if (StringUtils.hasText(tm.getTableName())) {
                    skeletons.add(tm);
                }
            }
            return skeletons;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 骨架建模结果解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 骨架建模结果解析失败，可重试或改用「手动粘贴 DDL」");
        }
    }

    private GenPipelineAiVO parseTables(String json) {
        try {
            JsonNode tables = objectMapper.readTree(json).path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new IllegalStateException("tables 为空");
            }
            GenPipelineAiVO vo = new GenPipelineAiVO();
            for (JsonNode t : tables) {
                GenPipelineAiVO.TableModel tm = new GenPipelineAiVO.TableModel();
                // 小模型不守 prompt 里的 camelCase 键名（qwen2.5:7b 实测输出 table_name），两种风格都收
                tm.setTableName(firstText(t, "tableName", "table_name"));
                tm.setTableComment(firstText(t, "tableComment", "table_comment", "comment"));
                for (JsonNode c : t.path("columns")) {
                    GenPipelineAiVO.Column col = new GenPipelineAiVO.Column();
                    col.setName(firstText(c, "name"));
                    col.setType(firstText(c, "type", "dataType"));
                    col.setComment(firstText(c, "comment", "columnComment"));
                    col.setPk(c.path("pk").asBoolean(false) || c.path("isPk").asBoolean(false));
                    tm.getColumns().add(col);
                }
                if (StringUtils.hasText(tm.getTableName()) && !tm.getColumns().isEmpty()) {
                    vo.getTables().add(tm);
                }
            }
            if (vo.getTables().isEmpty()) {
                throw new IllegalStateException("未解析出有效表");
            }
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 建模结果解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 建模结果解析失败，可重试或改用「手动粘贴 DDL」");
        }
    }

    private GenPipelineAiModulesVO parseModules(String json) {
        try {
            JsonNode modules = objectMapper.readTree(json).path("modules");
            if (!modules.isArray() || modules.isEmpty()) {
                throw new IllegalStateException("modules 为空");
            }
            GenPipelineAiModulesVO vo = new GenPipelineAiModulesVO();
            for (JsonNode m : modules) {
                String name = firstText(m, "name", "moduleName", "module_name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                GenPipelineAiModulesVO.Module mod = new GenPipelineAiModulesVO.Module();
                mod.setName(name);
                mod.setDesc(firstText(m, "desc", "description", "moduleDesc"));
                // V9.4：旧模型没有 functions / 类型不符时按空数组兼容；
                // 只收文本节点并过滤空白，避免把 null、数字、空串带进可编辑清单。
                JsonNode functions = m.path("functions");
                if (functions.isArray()) {
                    for (JsonNode function : functions) {
                        if (!function.isTextual()) {
                            continue;
                        }
                        String text = function.asText().trim();
                        if (StringUtils.hasText(text)) {
                            mod.getFunctions().add(text);
                        }
                    }
                }
                vo.getModules().add(mod);
            }
            if (vo.getModules().isEmpty()) {
                throw new IllegalStateException("未解析出有效模块");
            }
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 模块拆分解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 模块拆分解析失败，可重试");
        }
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 解析模型回复：剥掉可能的 markdown 围栏，提取第一个 JSON 对象。
     */
    private String extractJson(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("回复中找不到 JSON");
        }
        return s.substring(start, end + 1);
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
