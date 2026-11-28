package com.codegen.quality;

import com.codegen.quality.QualityRule.Category;
import com.codegen.quality.QualityRule.Detector;
import com.codegen.quality.QualityRule.Severity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.codegen.quality.QualityRule.det;
import static com.codegen.quality.QualityRule.llm;

/**
 * M2.2 规则库：从 80 条踩坑记录（{@code dev-doc/02-坑} 与本仓库代码注释）提炼出的
 * 52 条可执行规则。每条规则都能落到具体文件/行，命中 BLOCKER 时生成不落盘。
 *
 * <p>规则分两类引擎：
 * <ul>
 *   <li>{@code DET}：确定性扫描（正则/结构化），零依赖、可重复；</li>
 *   <li>{@code LLM}：语义审查，复用平台 OpenAI 兼容配置；不可用/超时自动回落 DET。</li>
 * </ul>
 */
public final class QualityRuleCatalog {

    private static final List<String> JAVA = List.of(".java");
    private static final List<String> VUE = List.of(".vue");
    private static final List<String> JS = List.of(".js", ".ts");
    private static final List<String> SQL = List.of(".sql");
    private static final List<String> CONFIG = List.of(".yml", ".yaml", ".properties");

    private QualityRuleCatalog() {
    }

    private static final List<QualityRule> RULES = List.of(
            // ==================== Java（18） ====================
            det("Q001", "@PathVariable 未显式命名", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.REGEX, "@PathVariable\\s+[A-Za-z]",
                    "@PathVariable 未指定参数名，依赖编译期 -parameters，运行期必 500",
                    "@PathVariable Long id", "@PathVariable(\"id\") Long id", "坑 1 / 微服务拆分 2"),
            det("Q002", "手写 SQL 缺少 deleted = 0", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.SQL_MISSING_DELETED, null,
                    "手写查询/更新未过滤逻辑删除列，已删除数据会混入业务结果",
                    "SELECT id FROM demo_goods WHERE status = 1",
                    "SELECT id FROM demo_goods WHERE status = 1 AND deleted = 0",
                    "2026-11-06 codegen 走查"),
            det("Q003", "Map.of 超过 10 组键值", Category.JAVA, Severity.WARN, JAVA,
                    Detector.MAP_OF_ARITY, null,
                    "Map.of 最多 10 组键值，超出直接编译失败",
                    "Map.of(\"a\",1,\"b\",2,\"c\",3,\"d\",4,\"e\",5,\"f\",6,\"g\",7,\"h\",8,\"i\",9,\"j\",10,\"k\",11)",
                    "Map.ofEntries(entry(\"a\",1), ...)", "坑 16"),
            det("Q004", "雪花 Long ID 未转字符串下发", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.LONG_ID_JSON, null,
                    "19 位雪花 ID 以 Long 下发会被 JS Number 截断，前端回传后查不到记录",
                    "result.put(\"id\", goods.getId());",
                    "result.put(\"id\", String.valueOf(goods.getId()));", "坑 2 / 坑 57"),
            det("Q005", "try-with-resources 内提前返回字节流", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.REGEX,
                    "try\\s*\\([^)]*\\)\\s*\\{[^}]*?return\\s+[\\w.]+\\s*\\.toByteArray\\s*\\(\\s*\\)",
                    "在 try-with-resources 内直接 return toByteArray()，流未关闭，ZIP 永远缺 EOCD",
                    "try (var out = new ByteArrayOutputStream()) { return out.toByteArray(); }",
                    "try (var out = new ByteArrayOutputStream()) { ... }\nreturn out.toByteArray();",
                    "坑 42"),
            det("Q006", "DATETIME 强转 Timestamp", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.REGEX, "\\(Timestamp\\)\\s*[\\w.]+\\s*\\.getObject\\s*\\(",
                    "MySQL 8 驱动 DATETIME 返回 LocalDateTime，强转 Timestamp 运行期 ClassCastException",
                    "(Timestamp) rs.getObject(\"create_time\")",
                    "rs.getObject(\"create_time\", LocalDateTime.class)", "坑 46"),
            det("Q007", "从 sys_role_menu 直接查 perms", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.REGEX, "(?i)\\brm\\s*\\.\\s*perms\\b|sys_role_menu[^;]{0,80}\\bperms\\b",
                    "perms 列在 sys_menu 上，sys_role_menu 只有 (role_id, menu_id)，直查必 Unknown column",
                    "SELECT rm.perms FROM sys_role_menu rm",
                    "SELECT m.perms FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id",
                    "坑 47"),
            det("Q008", "SELECT MAX(id) 回取自增主键", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.REGEX, "(?i)SELECT\\s+MAX\\s*\\(\\s*id\\s*\\)",
                    "并发下 SELECT MAX(id) 会张冠李戴，必须用 generated keys / last_insert_id 同一连接",
                    "SELECT MAX(id) FROM demo_order",
                    "jdbcTemplate.update(sql, ps -> { ... }, keyHolder)", "坑 60"),
            det("Q009", "空 catch 吞异常", Category.JAVA, Severity.WARN, JAVA,
                    Detector.REGEX, "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}",
                    "空 catch 会把真实错误吞成「静默无痕」，至少记录日志或向上抛出",
                    "catch (Exception e) {}",
                    "catch (Exception e) { log.warn(\"处理失败: {}\", e.getMessage(), e); }",
                    "坑 30 / 日志读取假象"),
            det("Q010", "System.out 调试输出", Category.JAVA, Severity.WARN, JAVA,
                    Detector.REGEX, "System\\.out\\.(print|println|printf)",
                    "生产代码使用 System.out 会绕过日志级别与脱敏，统一改用 SLF4J",
                    "System.out.println(\"debug\");", "log.debug(\"debug\");", "坑 10"),
            det("Q011", "硬编码口令或密钥", Category.JAVA, Severity.BLOCKER, JAVA,
                    Detector.HARDCODED_SECRET, null,
                    "口令/密钥写死在代码中会随产物泄露，必须走配置或环境变量",
                    "String password = \"admin123\";",
                    "@Value(\"${spring.datasource.password}\") String password;", "坑 20 / 安全基线"),
            det("Q012", "TODO/FIXME 残留", Category.JAVA, Severity.WARN, JAVA,
                    Detector.TODO_MARKER, null,
                    "生成产物中残留 TODO/FIXME，交付前应清理或转成明确任务",
                    "// TODO 待补", "// 已实现", "代码规范"),
            det("Q013", "@MapperScan 扫描根包", Category.JAVA, Severity.WARN, JAVA,
                    Detector.REGEX, "@MapperScan\\s*\\(\\s*\"com\\.[\\w]+\"\\s*\\)",
                    "整包扫描会把渠道抽象接口注册成 Mapper 代理，运行期 Invalid bound statement",
                    "@MapperScan(\"com.demo\")", "@MapperScan(\"com.demo.module.mapper\")", "坑 44"),
            llm("Q014", "事务方法内部自调用", Category.JAVA, Severity.WARN, JAVA,
                    "同类内部 this 调用绕过 Spring 代理，@Transactional 失效",
                    "public void a() { this.b(); } @Transactional public void b() {}",
                    "拆到独立 Bean 或注入自身代理后调用", "Spring 事务与 AOP 失效"),
            llm("Q015", "实体字段与表结构不一致", Category.JAVA, Severity.BLOCKER, JAVA,
                    "实体/Wrapper 引用的列必须真实存在，否则运行期 Unknown column",
                    "@TableField(\"old_name\") private String newName;",
                    "@TableField(\"new_name\") private String newName;", "坑 7 / mysql 4"),
            llm("Q016", "失败结果被缓存", Category.JAVA, Severity.WARN, JAVA,
                    "网络抖动产生的空结果被缓存，会把功能锁成「永久空值」，失败不应写缓存",
                    "cache.put(key, Collections.emptyList()); // 异常分支",
                    "异常分支直接抛出，只缓存成功结果", "坑 17"),
            llm("Q017", "日志脱敏未覆盖口令值", Category.JAVA, Severity.BLOCKER, JAVA,
                    "脱敏正则只替换字段名时口令值会原样落日志，需按值模式脱敏",
                    "log.info(\"login body={}\", JSON.toJSONString(body));",
                    "log.info(\"login user={}\", body.getUsername());", "坑 20"),
            llm("Q018", "管理端点仅登录校验", Category.JAVA, Severity.BLOCKER, JAVA,
                    "删除/导出等管理动作不能只要求登录，必须校验角色或权限码",
                    "@PostMapping(\"/delete/{id}\") public void delete(...)",
                    "@PreAuthorize(\"@ss.hasPermi('demo:remove')\")", "坑 64"),

            // ==================== Vue（12） ====================
            det("Q019", "v-html 直出未过滤", Category.VUE, Severity.BLOCKER, VUE,
                    Detector.REGEX, "v-html\\s*=\\s*\"(?!(?:richTextHtml|sanitize|DOMPurify|escapeHtml)\\()",
                    "v-html 直出用户内容会形成存储型 XSS，优先插值或先做白名单过滤",
                    "<div v-html=\"row.content\"></div>", "<div>{{ row.content }}</div>", "坑 62 / XSS"),
            det("Q020", "前端 Number() 转换雪花 ID", Category.VUE, Severity.BLOCKER, VUE,
                    Detector.REGEX, "Number\\s*\\(\\s*[^)]*(?:[iI]d|Id)\\s*\\)",
                    "19 位 ID 经 Number() 丢失精度，条件更新会 0 行，保持字符串透传",
                    "const id = Number(row.id)", "const id = String(row.id)", "前端三端联调 5"),
            det("Q021", "el-upload 缺少 action", Category.VUE, Severity.BLOCKER, VUE,
                    Detector.REGEX, "(?s)<el-upload(?![^>]*\\baction\\s*=)",
                    "即使使用 http-request，el-upload 也要求 action，缺失时组件不可用",
                    "<el-upload :http-request=\"upload\">",
                    "<el-upload action=\"/api/file/upload\" :http-request=\"upload\">", "前端与流程时序 30"),
            llm("Q022", "服务端分页丢失批量勾选", Category.VUE, Severity.WARN, VUE,
                    "el-table 服务端分页翻页后未保留选中项，批量操作只作用于当前页",
                    "翻页时清空 selection", "维护 selectedRows 并按 id 合并跨页选中", "前端与流程时序 4"),
            llm("Q023", "状态枚举映射缺失", Category.VUE, Severity.WARN, VUE,
                    "后端新增枚举值而前端 status map 未同步，统计/回显恒为 0 或空白",
                    "statusMap = { 0: '待处理' }",
                    "statusMap 覆盖全部枚举并给未知值兜底", "前端与流程时序 2"),
            llm("Q024", "权限码与菜单不一致", Category.VUE, Severity.BLOCKER, VUE,
                    "v-hasPermi 引用的权限码必须与菜单注册生成的权限码一致，否则按钮被静默剪掉",
                    "v-hasPermi=\"['demo_goods:add']\" 而菜单是 demo:goods:add",
                    "统一由同一份权限码常量生成页面与菜单", "坑 29 / 自检权限一致性"),
            llm("Q025", "ECharts 在 v-if 切换后立即初始化", Category.VUE, Severity.WARN, VUE,
                    "DOM 尚未挂载时初始化图表会报 invalid dom，需等待 nextTick",
                    "v-if 切换后同步 echarts.init(el)",
                    "await nextTick(); echarts.init(el)", "前端三端联调 坑 B"),
            llm("Q026", "动态路由菜单缺行导致 404", Category.VUE, Severity.WARN, VUE,
                    "生成页面必须同步注册动态路由与菜单，缺任一侧都会落到 /404",
                    "页面已生成但 gen-routes.js 未合入",
                    "生成后校验菜单、路由、views 文件三方一致", "坑 33 / 前端三端联调"),
            llm("Q027", "模板门控定义与消费不一致", Category.VUE, Severity.BLOCKER, VUE,
                    "组件/方法未生成但页面仍引用，运行时报 Unknown custom element 或 is not a function",
                    "v-if 关闭了组件定义，模板仍渲染 <upload-panel>",
                    "定义侧与消费侧复用同一个开关变量", "前端三端联调 模板门控"),
            llm("Q028", "i18n 文案缺失或重复", Category.VUE, Severity.WARN, VUE,
                    "语言包 key 缺失会直接显示 key，BCP47 标签重复会触发 Duplicate keys 警告",
                    "t('demo.goods.title') 未在语言包定义",
                    "所有语言包键集合一致且标签唯一", "前端三端联调 BCP47"),
            llm("Q029", "表格列与实体字段不一致", Category.VUE, Severity.WARN, VUE,
                    "列表列 prop 必须是实体真实字段，拼错时整列空白且无报错",
                    "<el-table-column prop=\"goodsNmae\" />",
                    "<el-table-column prop=\"goodsName\" />", "前端三端联调 三处同源失配"),
            llm("Q030", "首页指标卡取值层级错误", Category.VUE, Severity.WARN, VUE,
                    "包装函数返回值层级取错会让指标卡恒为 0，需核对 request 解包约定",
                    "const n = res.data.total; // 实际在 res.data.data.total",
                    "统一走 toPageData/unwrap 后再取字段", "前端三端联调 2"),

            // ==================== JS（8） ====================
            det("Q031", "EventSource 未携带鉴权", Category.JS, Severity.WARN, JS,
                    Detector.REGEX, "new\\s+EventSource\\s*\\(",
                    "EventSource 发不了 Authorization 头，SSE 鉴权需走查询参数或 Cookie",
                    "new EventSource('/gen/pipeline/1/stream')",
                    "new EventSource(base + '/stream?token=' + encodeURIComponent(token))", "坑 23"),
            det("Q032", "分页契约使用 list 而非 records", Category.JS, Severity.BLOCKER, JS,
                    Detector.REGEX, "\\.data\\.list\\b",
                    "全项目分页契约是 records，取 list 会永远为空或拿到 undefined",
                    "res.data.list", "res.data.records", "微服务拆分 11"),
            det("Q033", "Number() 转换接口返回 ID", Category.JS, Severity.BLOCKER, JS,
                    Detector.REGEX, "Number\\s*\\([^)]*(?:[iI]d|Id)[^)]*\\)",
                    "接口返回的 19 位 ID 必须保持字符串，Number() 会丢精度",
                    "Number(res.data.id)", "String(res.data.id)", "坑 5 / 坑 22"),
            llm("Q034", "fetch 未检查 HTTP 状态", Category.JS, Severity.WARN, JS,
                    "异常响应也返回 200 的业务约定下，只判断 HTTP 状态会漏报失败",
                    "const data = await res.json(); // 未检查 code",
                    "if (res.code !== 200) throw new Error(res.msg);", "坑 11"),
            llm("Q035", "上传目录使用相对路径", Category.JS, Severity.WARN, JS,
                    "相对路径随进程工作目录漂移，图片接口 200 但直链 404",
                    "uploadDir = './uploads'", "uploadDir = path.resolve(config.root, 'uploads')", "前端三端联调 3"),
            llm("Q036", "请求前缀 /api 未被代理剥离", Category.JS, Severity.BLOCKER, JS,
                    "api.js 自带网关前缀而脚手架代理未剥离时，所有列表接口 404",
                    "baseURL = '/api/gateway/demo'", "代理 rewrite 剥离 /api 后再转发", "坑 34"),
            llm("Q037", "定时器/监听未清理", Category.JS, Severity.WARN, JS,
                    "组件销毁未清理定时器会造成重复请求与内存泄漏",
                    "setInterval(...) 无 clearInterval", "onUnmounted(() => clearInterval(timer))", "前端三端联调 8"),
            det("Q038", "硬编码本机地址", Category.JS, Severity.WARN, JS,
                    Detector.REGEX, "https?://(?:127\\.0\\.0\\.1|localhost):\\d+",
                    "硬编码本机地址会让产物在其它环境不可用，走环境变量或相对路径",
                    "axios.get('http://localhost:8080/api/goods')", "axios.get('/api/goods')", "坑 31 / 部署"),

            // ==================== SQL（6） ====================
            det("Q039", "CREATE TABLE 缺少审计列", Category.SQL, Severity.BLOCKER, SQL,
                    Detector.SQL_MISSING_AUDIT, null,
                    "建表缺少 create_time/update_time/deleted 等审计列，生成代码运行期 Unknown column",
                    "CREATE TABLE demo (id bigint PRIMARY KEY, name varchar(64));",
                    "CREATE TABLE demo (..., create_time datetime, update_time datetime, deleted tinyint NOT NULL DEFAULT 0);",
                    "坑 7 / 建表规范"),
            det("Q040", "逻辑删除列进入唯一索引", Category.SQL, Severity.BLOCKER, SQL,
                    Detector.REGEX, "(?is)UNIQUE[^;]*\\bdeleted\\b|\\bdeleted\\b[^;]*UNIQUE",
                    "deleted 进入唯一索引后第二次删除同一业务键必撞 Duplicate entry",
                    "UNIQUE KEY uk_code (code, deleted)", "UNIQUE KEY uk_code (code)",
                    "mysql 逻辑删除列进唯一索引"),
            llm("Q041", "跨表 collation 不一致", Category.SQL, Severity.WARN, SQL,
                    "UNION/JOIN 两表 collation 不一致会报 Illegal mix of collations",
                    "utf8mb4_general_ci JOIN utf8mb4_unicode_ci",
                    "同库统一字符集与排序规则", "mysql 1"),
            llm("Q042", "INSERT 列名与表结构不一致", Category.SQL, Severity.BLOCKER, SQL,
                    "手写 INSERT 多写一列会被 catch 吞成静默无痕，必须与表结构逐列对齐",
                    "INSERT INTO demo (id, name, old_col) VALUES (...)",
                    "INSERT INTO demo (id, name) VALUES (...)", "mysql N"),
            det("Q043", "业务编号使用秒级时间戳", Category.SQL, Severity.WARN, SQL,
                    Detector.REGEX, "(?i)DATE_FORMAT\\s*\\([^)]*%Y%m%d%H%i%s",
                    "秒级时间戳拼业务编号，同一秒内连建多条必撞唯一键",
                    "DATE_FORMAT(NOW(),'%Y%m%d%H%i%s')",
                    "DATE_FORMAT(NOW(),'%Y%m%d%H%i%s%f') 或引入序列号", "mysql N+4"),
            det("Q044", "脚本包含 USE 覆盖目标库", Category.SQL, Severity.BLOCKER, SQL,
                    Detector.REGEX, "(?im)^\\s*USE\\s+[`\\w]+\\s*;",
                    "脚本里的 USE 会覆盖客户端 --database，可能把 DDL 落到错误库",
                    "USE demo_db;", "-- 不写 USE，由执行上下文指定库", "mysql N+2"),

            // ==================== 配置（8） ====================
            llm("Q045", "YAML 重复键", Category.CONFIG, Severity.BLOCKER, CONFIG,
                    "application-local.yml 写出重复键会导致整批测试启动失败",
                    "server:\n  port: 8080\nserver:\n  port: 9090",
                    "同一层级键唯一", "坑 21"),
            llm("Q046", "YAML 缩进导致配置错层", Category.CONFIG, Severity.BLOCKER, CONFIG,
                    "缩进差 2 空格会让配置静默落进上一层，功能不生效且无报错",
                    "spring:\n  application:\n  servlet:\n    multipart:",
                    "按层级正确缩进，并用配置元数据校验", "坑 58"),
            llm("Q047", "残留 SERVER__PORT 环境变量", Category.CONFIG, Severity.WARN, CONFIG,
                    "残留环境变量优先级高于命令行，服务会起在随机端口导致健康检查超时",
                    "$env:SERVER__PORT 未清理就启动子进程",
                    "启动前 remove SERVER__PORT 并显式 --server.port", "坑 5 / 13.1"),
            llm("Q048", "上传大小限制只配一层", Category.CONFIG, Severity.WARN, CONFIG,
                    "Spring 与 Servlet 两层都限制大小，配一层等于没配",
                    "只配 spring.servlet.multipart.max-file-size",
                    "同时配置 max-file-size 与 max-request-size", "坑 56"),
            llm("Q049", "静态资源白名单缺失", Category.CONFIG, Severity.WARN, CONFIG,
                    "静态资源映射已做但鉴权白名单没加，图片直链仍 401",
                    "/uploads/** 未加入 AuthContextFilter 白名单",
                    "白名单放行 /uploads/** 且限制扩展名", "坑 55"),
            det("Q050", "配置中出现明文口令", Category.CONFIG, Severity.BLOCKER, CONFIG,
                    Detector.HARDCODED_SECRET, null,
                    "配置文件明文口令会随仓库泄露，使用环境变量或密钥管理",
                    "password: admin123", "password: ${DB_PASSWORD}", "坑 20 / 安全基线"),
            llm("Q051", "可选依赖拖垮健康检查", Category.CONFIG, Severity.WARN, CONFIG,
                    "Redis 等可选依赖不可用时 /actuator/health 一直 DOWN，会拖垮网关聚合",
                    "health.redis.enabled 未关闭", "可选依赖关闭健康检查或降级", "微服务拆分 9"),
            llm("Q052", "网关同一路由多个 Path 写成「与」", Category.CONFIG, Severity.BLOCKER, CONFIG,
                    "同一 route 里写多条 Path= 是「与」关系，路由永远匹配不上",
                    "predicates: - Path=/a/** - Path=/b/**",
                    "拆成多条 route 或用一条 Path 逗号分隔", "微服务拆分 1")
    );

    private static final Map<String, QualityRule> BY_ID = RULES.stream()
            .collect(Collectors.toMap(QualityRule::id, Function.identity()));

    public static List<QualityRule> all() {
        return RULES;
    }

    public static List<QualityRule> deterministicRules() {
        return RULES.stream().filter(r -> r.engine() == QualityRule.Engine.DET).toList();
    }

    public static List<QualityRule> llmRules() {
        return RULES.stream().filter(r -> r.engine() == QualityRule.Engine.LLM).toList();
    }

    public static QualityRule byId(String id) {
        return BY_ID.get(id);
    }
}
