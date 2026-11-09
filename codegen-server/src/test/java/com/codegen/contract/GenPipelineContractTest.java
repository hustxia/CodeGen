package com.codegen.contract;

import com.codegen.common.exception.BusinessException;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenDdlService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 写盘 / 漂移 / 回滚 四条不变式的接口契约测试。
 *
 * <p>背景：V4.2 上线时这四条不变式是**人工脚本**验证的（临时 mjs，跑完即删），
 * 也就是说「台账条数对不对」「备份能不能还原」这类结论没有任何东西守着。
 * 谁把写盘里的 {@code recordOutput} 挪个位置、把回滚的数据源换回 gen_history，
 * 都不会有测试变红——直到用户发现文件回不去。
 *
 * <p>本测试把四条不变式钉在 HTTP 接口层（MockMvc），用 {@code @TempDir} 做写盘沙箱：
 * <ol>
 *   <li><b>台账完整</b>：写盘后台账行数 == 磁盘实际文件数，且每行 md5 == 文件 md5；</li>
 *   <li><b>备份可信</b>：覆盖时 {@code prev_md5} == 备份文件 md5（回滚的凭据）；</li>
 *   <li><b>回滚可逆</b>：回滚后文件 md5 回到覆盖前；</li>
 *   <li><b>回滚可再回滚</b>：回滚动作本身记 RBK 批次，对该批次再回滚可回到回滚前。</li>
 * </ol>
 *
 * <p>安全约束：数据源必须是 {@code gen_platform_test}，否则直接 fail——
 * 这个测试会写库，连错库就是往开发台账里灌垃圾数据。
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("写盘链路四条不变式（HTTP 契约）")
class GenPipelineContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenTableColumnMapper genTableColumnMapper;

    @Autowired
    private GenDdlService ddlService;

    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path workDir;

    private GenTable table;

    @BeforeEach
    void setUp() throws Exception {
        guardAgainstDevDatabase();
        // 清掉上一轮遗留：测试库虽然独立，但表配置会一轮一轮堆积，
        // 堆到几百行后 health 的统计断言会变得没有意义
        jdbcTemplate.update("DELETE FROM gen_output WHERE table_name LIKE 'ct_goods_%'");
        jdbcTemplate.update("DELETE FROM gen_table_column WHERE table_id IN "
                + "(SELECT id FROM gen_table WHERE table_name LIKE 'ct_goods_%')");
        jdbcTemplate.update("DELETE FROM gen_table WHERE table_name LIKE 'ct_goods_%'");
        // V4.4 夹具同样按前缀清理（子表靠 master_table_id 关联，直接删行即可）
        jdbcTemplate.update("DELETE FROM gen_output WHERE table_name LIKE 'ct\\_order\\_%' ESCAPE '\\\\'");
        jdbcTemplate.update("DELETE FROM gen_table_column WHERE table_id IN "
                + "(SELECT id FROM gen_table WHERE table_name LIKE 'ct\\_order\\_%' ESCAPE '\\\\')");
        jdbcTemplate.update("DELETE FROM gen_table WHERE table_name LIKE 'ct\\_order\\_%' ESCAPE '\\\\'");
        jdbcTemplate.update("DELETE FROM sys_project WHERE project_code LIKE 'ct\\_proj\\_%' ESCAPE '\\\\'");
        table = newTable();
    }

    // ------------------------------------------------------------------
    // 不变式 1：台账完整
    // ------------------------------------------------------------------

    @Test
    @DisplayName("不变式1：写盘后台账行数 == 磁盘文件数，且 md5 与文件一致")
    void ledgerMatchesDisk() throws Exception {
        List<JsonNode> results = write(true, false);
        assertFalse(results.isEmpty(), "写盘没有返回任何文件结果");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rel_path, abs_path, action, md5, file_size FROM gen_output WHERE table_id = ?",
                table.getId());
        assertFalse(rows.isEmpty(), "写盘后台账为空——台账链路断了");

        long written = results.stream()
                .filter(r -> List.of("WRITTEN", "OVERWRITTEN", "MERGED")
                        .contains(r.path("status").asText()))
                .count();
        assertEquals(written, rows.size(),
                "台账行数与实际写入文件数不一致：磁盘 " + written + " / 台账 " + rows.size());

        for (Map<String, Object> row : rows) {
            Path file = Path.of(String.valueOf(row.get("abs_path")));
            assertTrue(Files.exists(file), "台账记录的文件不存在：" + file);
            assertEquals(md5(file), String.valueOf(row.get("md5")),
                    "台账 md5 与磁盘文件不一致：" + row.get("rel_path"));
            assertEquals(Files.size(file), ((Number) row.get("file_size")).longValue(),
                    "台账字节数与磁盘不一致：" + row.get("rel_path"));
            assertTrue(file.startsWith(workDir),
                    "写盘跑出了沙箱目录，说明路径回落取到了全局配置：" + file);
        }
    }

    // ------------------------------------------------------------------
    // 不变式 2：备份可信
    // ------------------------------------------------------------------

    @Test
    @DisplayName("不变式2：覆盖时 prev_md5 == 备份文件 md5，且备份内容就是覆盖前内容")
    void backupAndPrevMd5Trustworthy() throws Exception {
        write(true, false);
        Path target = firstWrittenFile();
        String beforeMd5 = md5(target);

        // 改一行配置，让第二次渲染的产物必然不同
        changeColumnComment("商品名称改过了");
        String batch = batchOf(write(true, false));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT prev_md5, backup_path, action FROM gen_output "
                        + "WHERE table_id = ? AND abs_path = ? ORDER BY id DESC LIMIT 1",
                table.getId(), target.toString());
        String backupPath = String.valueOf(row.get("backup_path"));
        assertTrue(!"null".equals(backupPath) && !backupPath.isBlank(),
                "覆盖写盘没有留下备份，回滚就无从谈起");

        Path backup = Path.of(backupPath);
        assertTrue(Files.exists(backup), "备份文件不存在：" + backup);
        assertEquals(md5(backup), String.valueOf(row.get("prev_md5")),
                "prev_md5 与备份文件内容不一致——回滚会还原成错误内容");
        assertEquals(beforeMd5, md5(backup),
                "备份内容不是覆盖前的文件内容");
        assertNotNull(batch);
    }

    // ------------------------------------------------------------------
    // 不变式 3 + 4：回滚可逆、可再回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("不变式3+4：回滚还原覆盖前内容，对 RBK 批次再回滚可回到回滚前")
    void rollbackRestoresAndCanRollbackAgain() throws Exception {
        write(true, false);
        Path target = firstWrittenFile();
        String originalMd5 = md5(target);

        changeColumnComment("第二版");
        String batch = batchOf(write(true, false));
        String afterOverwriteMd5 = md5(target);
        assertFalse(afterOverwriteMd5.equals(originalMd5), "第二次写盘没有改变文件内容，用例前提不成立");

        JsonNode preview = callJson(post("/gen/history/rollback/preview?batchNo=" + batch)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        long restorable = 0;
        for (JsonNode item : preview.path("data").path("items")) {
            if ("RESTORE".equals(item.path("op").asText())) {
                restorable++;
            }
        }
        assertTrue(restorable > 0, "预览里没有任何可还原行，回滚能力退化了");

        JsonNode rollback = callJson(post("/gen/history/rollback?batchNo=" + batch)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        String rbkBatch = rollback.path("data").path("rollbackBatch").asText();
        assertTrue(rbkBatch.startsWith("RBK"), "回滚没有生成 RBK 批次，回滚动作本身没有留痕：" + rbkBatch);
        assertEquals(originalMd5, md5(target), "回滚后文件没有回到覆盖前内容");

        JsonNode again = callJson(post("/gen/history/rollback?batchNo=" + rbkBatch)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertTrue(again.path("code").asInt() == 200, "对回滚批次再回滚失败：" + again.path("msg").asText());
        assertEquals(afterOverwriteMd5, md5(target),
                "对 RBK 批次再回滚后，文件应回到「回滚前」的状态");
    }

    // ------------------------------------------------------------------
    // 漂移检测与写盘拦截
    // ------------------------------------------------------------------

    @Test
    @DisplayName("漂移：手改后 drift=DRIFTED；只写未漂移策略会跳过该文件")
    void driftDetectedAndSkipped() throws Exception {
        String batch = batchOf(write(true, false));
        Path target = firstWrittenFile();

        Files.writeString(target, Files.readString(target, StandardCharsets.UTF_8)
                + "\n// 手工加的一行\n", StandardCharsets.UTF_8);

        JsonNode drift = callJson(post("/gen/drift")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + table.getId() + "\"]}"));
        String status = null;
        for (JsonNode item : drift.path("data").path("items")) {
            if (Path.of(item.path("absPath").asText()).equals(target)) {
                status = item.path("status").asText();
            }
        }
        assertEquals("DRIFTED", status, "手改文件未被识别为漂移");

        List<JsonNode> safe = write(true, true);
        JsonNode hit = safe.stream()
                .filter(r -> r.path("path").asText().equals(relOf(target)))
                .findFirst().orElseThrow();
        assertEquals("SKIPPED", hit.path("status").asText(),
                "只写未漂移策略没有跳过手改文件，手写代码会被覆盖");
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("手工加的一行"),
                "手改内容被覆盖了");

        assertNotNull(batch);
    }

    // ------------------------------------------------------------------
    // 台账巡检
    // ------------------------------------------------------------------

    @Test
    @DisplayName("巡检：health 能统计台账行数/路径数，孤儿记录可被识别")
    void healthReportsCounts() throws Exception {
        write(true, false);
        JsonNode health = callJson(get("/gen/ledger/health"));
        assertEquals(200, health.path("code").asInt(), health.path("msg").asText());

        JsonNode data = health.path("data");
        assertTrue(data.path("totalRecords").asInt() > 0, "巡检没有读到台账行");
        assertTrue(data.path("uniquePaths").asInt() > 0, "巡检没有统计到路径");

        // 删掉一个磁盘文件，制造孤儿记录
        Path victim = firstWrittenFile();
        Files.delete(victim);
        JsonNode after = callJson(get("/gen/ledger/health"));
        assertTrue(after.path("data").path("orphanCount").asInt() >= 1,
                "磁盘文件丢失后没有被识别为孤儿记录");
    }

    // ------------------------------------------------------------------
    // 选择性回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("选择性回滚：只还原勾选的文件，未勾选的保持覆盖后内容")
    void selectiveRollbackOnlyTouchesPicked() throws Exception {
        write(true, false);
        List<Path> files = writtenFiles();
        assertTrue(files.size() >= 2, "至少要有两个文件才能验证选择性回滚，实际 " + files.size());
        Path picked = files.get(0);
        Path untouched = files.get(1);
        String pickedBefore = md5(picked);
        String untouchedBefore = md5(untouched);

        changeColumnComment("选择性回滚用例");
        String batch = batchOf(write(true, false));
        assertFalse(md5(picked).equals(pickedBefore), "文件没有被覆盖，用例前提不成立");

        JsonNode preview = callJson(post("/gen/history/rollback/preview?batchNo=" + batch)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        String ledgerId = null;
        for (JsonNode item : preview.path("data").path("items")) {
            if (Path.of(item.path("absPath").asText()).equals(picked)) {
                ledgerId = item.path("ledgerId").asText();
            }
        }
        assertNotNull(ledgerId, "预览里找不到目标文件的台账 ID");

        JsonNode res = callJson(post("/gen/history/rollback?batchNo=" + batch)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ledgerIds\":[\"" + ledgerId + "\"]}"));
        assertEquals(200, res.path("code").asInt(), res.path("msg").asText());
        assertEquals(1, res.path("data").path("total").asInt(),
                "只勾了一个文件，回滚却处理了多条台账");

        assertEquals(pickedBefore, md5(picked), "勾选的文件没有还原");
        assertFalse(md5(untouched).equals(untouchedBefore),
                "未勾选的文件也被回滚了——选择性回滚退化成了整批回滚");
    }

    // ------------------------------------------------------------------
    // V4.4：一主多子 + 删除级联三档（渲染契约）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("级联模式：removeWithChildren 覆盖每张子表与孙层，controller 删除端点改走级联")
    void cascadeDeleteCleansEveryChildAndGrand() throws Exception {
        String tag = "cascade";
        GenTable master = newMasterWithChildren(tag);
        setMode(master, 1);
        String itemVar = decap("CtOrderItem" + uuidSafe(tag));
        String attrVar = decap("CtOrderItemAttr" + uuidSafe(tag));
        String logVar = decap("CtOrderLog" + uuidSafe(tag));
        String impl = previewContent(master, "ServiceImpl.java");
        // 每张子表都要按外键删
        assertTrue(impl.contains(itemVar + "Mapper.delete"), "级联实现缺第一张子表的删除");
        assertTrue(impl.contains(logVar + "Mapper.delete"), "级联实现缺第二张子表的删除——多子化退回了单子表");
        // 孙层必须先于子表清掉
        assertTrue(impl.contains(attrVar + "Mapper.delete"), "级联实现缺孙层删除，三级明细会变孤儿");
        // 孙层的实体与 Mapper 必须在 import 里：模板漏了就「找不到符号」，生成的主子表实现类直接编译不过
        String attrClass = "CtOrderItemAttr" + uuidSafe(tag);
        assertTrue(impl.contains("import com.demo.entity." + attrClass + ";"),
                "缺孙层实体 import，生成的主子表实现类编译不过");
        assertTrue(impl.contains("import com.demo.mapper." + attrClass + "Mapper;"),
                "缺孙层 Mapper import，生成的主子表实现类编译不过");
        // 孙层按主键集合一次删，不能逐行 delete（500 条明细会变成 500 条 SQL）
        assertTrue(impl.contains(itemVar + "Ids.isEmpty()"), "孙层删除缺空集合守卫，空 IN 会报 SQL 语法错");
        assertFalse(impl.contains("Row : " + itemVar + "Rows"),
                "孙层退回了逐行 delete，明细多时会生成 N 条 SQL");
        assertTrue(impl.contains("removeById(id)"), "级联实现最后没有删主行");
        String ctrl = previewContent(master, "Controller.java");
        assertTrue(ctrl.contains("removeWithChildren(id)"),
                "主子表的删除端点必须走 removeWithChildren，直接 removeById 会留孤儿明细");
    }

    @Test
    @DisplayName("阻止模式：存在子行时抛 BusinessException 拒删")
    void blockModeRejectsDelete() throws Exception {
        String tag = "block";
        GenTable master = newMasterWithChildren(tag);
        setMode(master, 0);
        String itemVar = decap("CtOrderItem" + uuidSafe(tag));
        String logVar = decap("CtOrderLog" + uuidSafe(tag));
        String impl = previewContent(master, "ServiceImpl.java");
        assertTrue(impl.contains("selectCount"), "阻止模式必须先数子行");
        assertTrue(impl.contains("BusinessException"), "阻止模式必须抛业务异常");
        assertFalse(impl.contains(itemVar + "Mapper.delete") || impl.contains(logVar + "Mapper.delete"),
                "阻止模式不允许出现子表删除——那是级联模式的行为");
    }

    @Test
    @DisplayName("不处理模式：维持 removeById 现状，不碰子表（存量行为逃生门）")
    void noneModeKeepsLegacyDelete() throws Exception {
        String tag = "legacy";
        GenTable master = newMasterWithChildren(tag);
        setMode(master, 2);
        String itemVar = decap("CtOrderItem" + uuidSafe(tag));
        String logVar = decap("CtOrderLog" + uuidSafe(tag));
        String impl = previewContent(master, "ServiceImpl.java");
        assertTrue(impl.contains("removeWithChildren"), "接口方法必须存在，否则 controller 编译不过");
        assertFalse(impl.contains(itemVar + "Mapper.delete") || impl.contains(logVar + "Mapper.delete"),
                "不处理模式不允许删子表——这档是给存量行为留的逃生门");
        assertFalse(impl.contains("BusinessException"), "不处理模式不应阻止删除");
    }

    @Test
    @DisplayName("模式回落：表级未配置时用系统默认，表级配置优先于系统默认")
    void cascadeModeFallsBackToProjectDefault() throws Exception {
        GenTable master = newMasterWithChildren("fallback");
        // 系统默认=不处理(2)，表级 NULL → 应维持现状（不出现子表删除）
        Long projectId = newProjectWithCascade(2);
        jdbcTemplate.update("UPDATE gen_table SET project_id = ? WHERE id = ?", projectId, master.getId());
        String itemVar = decap("CtOrderItem" + uuidSafe("fallback"));
        String impl = previewContent(master, "ServiceImpl.java");
        assertFalse(impl.contains(itemVar + "Mapper.delete"),
                "表级 NULL 应回落系统默认(2=不处理)，实际却按级联生成了");

        // 表级显式=阻止(0) → 覆盖系统默认
        setMode(master, 0);
        impl = previewContent(master, "ServiceImpl.java");
        assertTrue(impl.contains("BusinessException"), "表级配置应优先于系统默认，实际未按阻止模式生成");
    }

    @Test
    @DisplayName("多子详情：/with-children 每张子表一个 group，前端详情抽屉多 tab 取数")
    void multiChildDetailGroups() throws Exception {        String tag = "detail";
        GenTable master = newMasterWithChildren(tag);
        String itemVar = decap("CtOrderItem" + uuidSafe(tag));
        String logVar = decap("CtOrderLog" + uuidSafe(tag));
        String ctrl = previewContent(master, "Controller.java");
        assertTrue(ctrl.contains(itemVar + "Group"), "详情接口缺第一张子表的 group");
        assertTrue(ctrl.contains(logVar + "Group"), "详情接口缺第二张子表的 group——一主多子退回了单子");
        assertTrue(ctrl.contains("data.put(\"children\", children)"), "详情接口没有组装 children");

        String vue = previewContent(master, "index.vue");
        assertTrue(vue.contains("detailGroupRows('" + itemVar + "')"), "详情抽屉缺第一张明细 tab 的取数");
        assertTrue(vue.contains("detailGroupRows('" + logVar + "')"), "详情抽屉缺第二张明细 tab 的取数");
        assertTrue(vue.contains("activeDetailTab"), "详情抽屉没有 tabs 状态");
    }

    // ------------------------------------------------------------------
    // V4.5 打印单据契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("打印单据：print.vue A4 版式 + 各子表明细块与合计 + 无 layout 路由 + 开关真实生效")
    void printPageContainsA4Layout() throws Exception {
        String tag = "print";
        GenTable master = newMasterWithChildren(tag);
        String itemVar = decap("CtOrderItem" + uuidSafe(tag));
        String logVar = decap("CtOrderLog" + uuidSafe(tag));
        String attrVar = decap("CtOrderItemAttr" + uuidSafe(tag));
        String module = master.getModuleName();
        String className = master.getClassName();
        jdbcTemplate.update("UPDATE gen_table SET gen_print = 1 WHERE id = ?", master.getId());

        // print.vue 版式全要素
        String print = previewContent(master, "print.vue");
        assertTrue(print.contains("@page"), "打印页缺 @page 规则");
        assertTrue(print.contains("size: A4 portrait"), "打印页缺 A4 纸张声明");
        assertTrue(print.contains("table-header-group"), "明细表头缺跨页自动重复声明");
        assertTrue(print.contains("break-inside: avoid"), "明细行缺防拦腰截断声明");
        // 主子表：每张子表一个明细块 + 孙表平铺块
        assertTrue(print.contains("rowsOf('" + itemVar + "')"), "打印页缺订单明细块");
        assertTrue(print.contains("rowsOf('" + logVar + "')"), "打印页缺操作日志块——一主多子退回了单子");
        assertTrue(print.contains("rowsOf('" + itemVar + "." + attrVar + "')"), "打印页缺孙表平铺块");
        // 数值列合计（Integer → plain 累加）
        assertTrue(print.contains("colSum('" + itemVar + "', 'qty')"), "明细块缺数值列合计");

        // 入口接线：操作列打印按钮 + 无 layout 顶层路由
        String vue = previewContent(master, "index.vue");
        assertTrue(vue.contains("handlePrintRow(row)"), "列表操作列缺打印按钮");
        // 路由片段是 ZIP 共享文件（GenCodeServiceImpl.generateZip 挂载），预览接口不返回，必须走 ZIP 断言
        String snippet = zipContent(master.getId(), "router-snippet.txt");
        assertTrue(snippet.contains("/print/" + module + "/:id"), "路由片段缺无 layout 打印路由");
        assertTrue(snippet.contains("Print" + className), "打印路由组件名缺失");

        // 系统默认回落：表级 NULL + sys_project.default_gen_print=1 → 路由片段仍要挂打印路由
        long projectId = System.nanoTime();
        jdbcTemplate.update("INSERT INTO sys_project (id, project_code, project_name, default_gen_print) "
                + "VALUES (?, ?, ?, 1)", projectId, "ct_proj_print_" + projectId, "契约打印系统");
        jdbcTemplate.update("UPDATE gen_table SET gen_print = NULL, project_id = ? WHERE id = ?",
                projectId, master.getId());
        String snippetWithDefault = zipContent(master.getId(), "router-snippet.txt");
        assertTrue(snippetWithDefault.contains("Print" + className),
                "系统默认开(1)而表级未配置时，路由片段丢了打印路由——回落链断了");

        // 关闭真实生效：表级 0 → 产物无 print.vue、无打印按钮、无打印路由
        jdbcTemplate.update("UPDATE gen_table SET gen_print = 0, project_id = NULL WHERE id = ?", master.getId());
        JsonNode res = callJson(post("/gen/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + master.getId() + "\"]}"));
        assertEquals(200, res.path("code").asInt(), "预览失败：" + res.path("msg").asText());
        Map<String, String> files = new java.util.LinkedHashMap<>();
        for (JsonNode file : res.path("data")) {
            files.put(file.path("path").asText(), file.path("content").asText());
        }
        assertTrue(files.keySet().stream().noneMatch(p -> p.endsWith("print.vue")),
                "gen_print=0 时不应生成 print.vue：路径=" + files.keySet());
        String vueOff = files.entrySet().stream().filter(e -> e.getKey().endsWith("index.vue"))
                .map(Map.Entry::getValue).findFirst().orElse("");
        assertFalse(vueOff.contains("handlePrintRow"), "gen_print=0 时列表不应有打印按钮");
        String snippetOff = zipContent(master.getId(), "router-snippet.txt");
        assertFalse(snippetOff.contains("Print" + className), "gen_print=0 时路由片段不应含打印路由");
        assertFalse(snippetOff.contains("/print/"), "gen_print=0 时路由片段不应含 /print/ 路径");
    }

    // ------------------------------------------------------------------
    // V4.4 夹具
    // ------------------------------------------------------------------

    /**
     * 建一主两子（其一含孙表）的夹具：主表 ct_order_*，子表 ct_order_item_*（带孙表
     * ct_order_item_attr_*）与 ct_order_log_*。返回主表配置（gen_table 行）。
     */
    private GenTable newMasterWithChildren(String tag) {
        String suffix = tag + "_" + System.nanoTime();
        GenTable master = new GenTable();
        master.setTableName("ct_order_" + suffix);
        master.setTableComment("契约订单");
        master.setClassName("CtOrder" + uuidSafe(tag));
        master.setModuleName("ctOrder" + uuidSafe(tag));
        master.setPackageName("com.demo");
        master.setBackendPath("demo-server/src/main/java/com/demo");
        master.setBackendRoot(workDir.toString());
        master.setFrontendRoot(workDir.toString());
        master.setGatewayPrefix("demo");
        master.setAuthor("contract-test");
        genTableMapper.insert(master);
        insertColumn(master.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1);
        insertColumn(master.getId(), "order_no", "订单号", "varchar(32)", "orderNo", "String", 0, 2, 2);

        // 子表1：明细（含孙表：属性）
        GenTable item = childTable("ct_order_item_" + suffix, "CtOrderItem" + uuidSafe(tag),
                "CtOrderItem" + uuidSafe(tag), master.getId(), "order_id");
        insertColumn(item.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1);
        // isList 必须是 1（on() 严格判 1），否则打印明细块/编辑列表都不会带上该列
        insertColumn(item.getId(), "goods_name", "商品名", "varchar(100)", "goodsName", "String", 0, 1, 2);
        // V4.5 打印契约需要数值列验证合计行（Integer → plain 累加）
        insertColumn(item.getId(), "qty", "数量", "int", "qty", "Integer", 0, 1, 3);
        GenTable attr = childTable("ct_order_item_attr_" + suffix, "CtOrderItemAttr" + uuidSafe(tag),
                "CtOrderItemAttr" + uuidSafe(tag), item.getId(), "item_id");
        insertColumn(attr.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1);
        insertColumn(attr.getId(), "attr_name", "属性名", "varchar(50)", "attrName", "String", 0, 1, 2);

        // 子表2：日志（单层）
        GenTable log = childTable("ct_order_log_" + suffix, "CtOrderLog" + uuidSafe(tag),
                "CtOrderLog" + uuidSafe(tag), master.getId(), "order_id");
        insertColumn(log.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1);
        insertColumn(log.getId(), "log_text", "日志", "varchar(200)", "logText", "String", 0, 1, 2);
        return master;
    }

    private GenTable childTable(String tableName, String className, String moduleName,
                                Long masterTableId, String fk) {
        GenTable t = new GenTable();
        t.setTableName(tableName);
        t.setTableComment(tableName.contains("item_attr") ? "明细属性" : tableName.contains("item") ? "订单明细" : "操作日志");
        t.setClassName(className);
        t.setModuleName(moduleName);
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setBackendRoot(workDir.toString());
        t.setFrontendRoot(workDir.toString());
        t.setGatewayPrefix("demo");
        t.setAuthor("contract-test");
        t.setMasterTableId(masterTableId);
        t.setMasterForeignKey(fk);
        t.setMasterDepth(1);
        genTableMapper.insert(t);
        return t;
    }

    private void insertColumn(Long tableId, String name, String comment, String dbType,
                              String javaField, String javaType, int isPk, int isList, int sort) {
        genTableColumnMapper.insert(column(tableId, name, comment, dbType, javaField, javaType, isPk, isList, sort));
    }

    private Long newProjectWithCascade(int defaultMode) {
        long id = System.nanoTime();
        String code = "ct_proj_" + id;
        jdbcTemplate.update("INSERT INTO sys_project (id, project_code, project_name, default_delete_cascade_mode) "
                + "VALUES (?, ?, ?, ?)", id, code, "契约系统", defaultMode);
        return id;
    }

    private void setMode(GenTable master, int mode) {
        jdbcTemplate.update("UPDATE gen_table SET delete_cascade_mode = ? WHERE id = ?", mode, master.getId());
    }

    /** 预览产物里取「路径以 suffix 结尾」的文件内容（如 ServiceImpl.java / Controller.java / index.vue） */
    private String previewContent(GenTable master, String suffix) throws Exception {
        JsonNode res = callJson(post("/gen/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + master.getId() + "\"]}"));
        assertEquals(200, res.path("code").asInt(), "预览失败：" + res.path("msg").asText());
        for (JsonNode file : res.path("data")) {
            String path = file.path("path").asText();
            if (path.endsWith(suffix)) {
                return file.path("content").asText();
            }
        }
        throw new AssertionError("预览产物里找不到 *" + suffix + "（表 " + master.getTableName() + "）");
    }

    /**
     * ZIP 产物里取文件内容。路由片段 / README 这类共享文件只在 ZIP 组装时挂载
     * （GenCodeServiceImpl.generateZip），预览接口不返回——断言它们必须走 ZIP。
     */
    private String zipContent(Long tableId, String fileName) throws Exception {
        MvcResult result = mockMvc.perform(post("/gen/download")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + tableId + "\"]}")).andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 4 && bytes[0] != '{',
                "下载接口未返回 ZIP（表 " + tableId + "）：" + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes));
        try {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(fileName)) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } finally {
            zis.close();
        }
        throw new AssertionError("ZIP 产物里找不到 " + fileName + "（表 " + tableId + "）");
    }

    /** 首字母小写（与 GenTemplateModel.decap 同规则），用于从夹具类名推导模板变量名 */
    private static String decap(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** 模块/类名后缀：nanoTime 已含数字，这里只把 tag 首字母大写保证标识符合法 */
    private static String uuidSafe(String tag) {
        return Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
    }

    // ------------------------------------------------------------------
    // 差异事实
    // ------------------------------------------------------------------

    @Test
    @DisplayName("diff：每行返回 status 与 side 事实，前后端产物都要能识别")
    void diffReturnsFacts() throws Exception {
        write(true, false);
        changeColumnComment("差异用例");
        JsonNode res = callJson(post("/gen/diff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + table.getId() + "\"],\"onlyChanged\":false}"));
        assertEquals(200, res.path("code").asInt(), res.path("msg").asText());

        boolean hasBackend = false;
        boolean hasFrontend = false;
        for (JsonNode row : res.path("data")) {
            String side = row.path("side").asText();
            assertTrue(List.of("BACKEND", "FRONTEND").contains(side),
                    "diff 行缺少 side 事实：" + row.path("path").asText() + " -> " + side);
            assertFalse(row.path("status").asText().isBlank(), "diff 行缺少 status");
            hasBackend |= "BACKEND".equals(side);
            hasFrontend |= "FRONTEND".equals(side);
        }
        assertTrue(hasBackend && hasFrontend,
                "diff 没有同时识别出前后端产物：backend=" + hasBackend + ", frontend=" + hasFrontend);
    }

    // ------------------------------------------------------------------
    // 基础设施
    // ------------------------------------------------------------------

    private void guardAgainstDevDatabase() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String url = c.getMetaData().getURL();
            assertTrue(url.contains("gen_platform_test"),
                    "契约测试会写库，数据源必须是 gen_platform_test，当前是：" + url);
        }
    }

    /** 建一张只在本测试存在的表配置，写盘根目录固定到 @TempDir 沙箱 */
    private GenTable newTable() {
        GenTable t = new GenTable();
        t.setTableName("ct_goods_" + System.nanoTime());
        t.setTableComment("契约测试商品");
        t.setClassName("CtGoods");
        t.setModuleName("ctGoods");
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setBackendRoot(workDir.toString());
        t.setFrontendRoot(workDir.toString());
        t.setGatewayPrefix("demo");
        t.setAuthor("contract-test");
        genTableMapper.insert(t);

        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(column(t.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1));
        cols.add(column(t.getId(), "goods_name", "商品名称", "varchar(100)", "goodsName", "String", 0, 2, 2));
        cols.add(column(t.getId(), "price", "价格", "decimal(10,2)", "price", "BigDecimal", 0, 3, 3));
        cols.forEach(genTableColumnMapper::insert);
        return t;
    }

    private GenTableColumn column(Long tableId, String name, String comment, String dbType,
                                  String javaField, String javaType, int isPk, int isList, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setTableId(tableId);
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(isPk);
        c.setIsRequired(isPk == 1 ? 0 : 1);
        c.setIsQuery(isPk == 1 ? 0 : 1);
        c.setQueryType(isPk == 1 ? "EQ" : "LIKE");
        c.setIsList(isList);
        c.setIsForm(isPk == 1 ? 0 : 1);
        c.setHtmlType("input");
        c.setSort(sort);
        return c;
    }

    private void changeColumnComment(String comment) {
        jdbcTemplate.update("UPDATE gen_table_column SET column_comment = ? WHERE table_id = ? AND column_name = 'goods_name'",
                comment, table.getId());
    }

    private List<JsonNode> write(boolean overwrite, boolean skipDrifted) throws Exception {
        String body = "{\"ids\":[\"" + table.getId() + "\"],\"overwrite\":" + overwrite
                + ",\"skipDrifted\":" + skipDrifted + "}";
        JsonNode res = callJson(post("/gen/write")
                .contentType(MediaType.APPLICATION_JSON).content(body));
        assertEquals(200, res.path("code").asInt(), "写盘失败：" + res.path("msg").asText());
        // V4.2 台账合一：data 从文件数组包成 {batchNo, ledgerErrors, files}
        List<JsonNode> list = new ArrayList<>();
        res.path("data").path("files").forEach(list::add);
        return list;
    }

    private String batchOf(List<JsonNode> results) {
        for (JsonNode r : results) {
            String b = r.path("batchNo").asText();
            if (!b.isBlank() && !"null".equals(b)) {
                return b;
            }
        }
        // 老版本 VO 不带 batchNo 时，回落到台账里的批次
        return jdbcTemplate.queryForObject(
                "SELECT batch_no FROM gen_output WHERE table_id = ? ORDER BY id DESC LIMIT 1",
                String.class, table.getId());
    }

    private Path firstWrittenFile() {
        return writtenFiles().get(0);
    }

    private List<Path> writtenFiles() {
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT abs_path FROM gen_output WHERE table_id = ? AND action IN ('WRITTEN','OVERWRITTEN','MERGED') "
                        + "ORDER BY id ASC", String.class, table.getId());
        assertFalse(paths.isEmpty(), "台账里没有已写文件");
        List<Path> out = new ArrayList<>();
        paths.stream().distinct().forEach(p -> out.add(Path.of(p)));
        return out;
    }

    // ------------------------------------------------------------------
    // 坑 38：目标库不存在且 DDL 不建库时，execute 必须拒绝（防 USE 回落打错库）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("坑38：库不存在且无 CREATE DATABASE → execute 抛异常，不污染任何库")
    void executeRefusesWhenSchemaMissingAndNoCreateDatabase() {
        String missingDb = "gen_platform_test_no_such_db_38";
        GenDdlExecuteDTO dto = new GenDdlExecuteDTO();
        dto.setSchema(missingDb);
        dto.setDdl("""
                CREATE TABLE `ct_38_probe` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB;
                """);
        BusinessException ex = assertThrows(BusinessException.class, () -> ddlService.execute(dto));
        assertTrue(ex.getMessage().contains("不存在"), "报错要指明目标库不存在：" + ex.getMessage());

        // 不变式：拒绝执行后，任何库里都不允许出现这张探针表
        Integer hits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ct_38_probe'",
                Integer.class);
        assertEquals(0, hits, "execute 必须在执行任何语句前拒绝，探针表不允许出现在任何库");
    }

    @Test
    @DisplayName("坑38对照：DDL 自带 CREATE DATABASE 时允许执行并真正建库")
    void executeAllowsWhenDdlCreatesDatabase() {
        String freshDb = "gen_platform_test_ct38";
        GenDdlExecuteDTO dto = new GenDdlExecuteDTO();
        dto.setSchema(freshDb);
        dto.setDdl("CREATE DATABASE IF NOT EXISTS `" + freshDb + "` DEFAULT CHARACTER SET utf8mb4;\n"
                + "CREATE TABLE `ct_38_probe2` ("
                + "  `id` bigint NOT NULL AUTO_INCREMENT, PRIMARY KEY (`id`)"
                + ") ENGINE=InnoDB;");
        try {
            var vo = ddlService.execute(dto);
            assertEquals(0, vo.getFailedCount(), "自带建库语句的 DDL 应正常执行");
        } finally {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS `" + freshDb + "`");
        }
    }

    private String relOf(Path abs) {
        return jdbcTemplate.queryForObject(
                "SELECT rel_path FROM gen_output WHERE abs_path = ? ORDER BY id DESC LIMIT 1",
                String.class, abs.toString());
    }

    private JsonNode callJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.isBlank(), "接口返回空响应：HTTP " + result.getResponse().getStatus());
        return json.readTree(body);
    }

    private static String md5(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
