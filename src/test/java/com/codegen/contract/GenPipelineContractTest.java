package com.codegen.contract;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
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
        List<JsonNode> list = new ArrayList<>();
        res.path("data").forEach(list::add);
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
