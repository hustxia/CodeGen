package com.codegen.contract;

import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenAiRecommendApplyDTO;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.AiRecommendService;
import com.codegen.util.GenUtils;
import com.codegen.vo.GenAiRecommendPreviewVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.0 AI 建议预览/应用两条不变式（spec AC-3 / AC-4）：
 *
 * <ol>
 *   <li><b>AC-3 未应用不写库</b>：预览只读，未点应用 gen_table / gen_table_column 零 UPDATE；</li>
 *   <li><b>AC-4 勾选才写库</b>：被选中字段值与建议一致写库，未选中字段保持现值；
 *       主子建议按 masterTableName 解析成 master_table_id；菜单勾选把成员表归到模块。</li>
 * </ol>
 *
 * <p>安全约束与 {@link GenPipelineContractTest} 一致：数据源必须是 gen_platform_test。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("V7.0 AI 建议预览/应用契约")
class AiRecommendApplyContractTest {

    private static final String PREFIX = "ct\\_air\\_%";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenTableColumnMapper genTableColumnMapper;

    @Autowired
    private AiRecommendService aiRecommendService;

    @Autowired
    private GenPipelineProperties props;

    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path logDir;

    private String oldLogDir;
    private long projectId;
    private long pipelineId;
    private String pipelineNo;
    private GenTable master;
    private GenTable child;
    private Long statusColumnId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            assertTrue(url.contains("gen_platform_test"),
                    "契约测试只允许跑在 gen_platform_test 库上，当前：" + url);
        }
        oldLogDir = props.getLogDir();
        props.setLogDir(logDir.toString());

        jdbcTemplate.update("DELETE FROM gen_table_column WHERE table_id IN "
                + "(SELECT id FROM gen_table WHERE table_name LIKE '" + PREFIX + "' ESCAPE '\\\\')");
        jdbcTemplate.update("DELETE FROM gen_table WHERE table_name LIKE '" + PREFIX + "' ESCAPE '\\\\'");
        jdbcTemplate.update("DELETE FROM gen_pipeline WHERE pipeline_no LIKE 'PPL_AIR_%'");
        jdbcTemplate.update("DELETE FROM sys_project WHERE project_code LIKE 'ct_air_%'");

        projectId = System.nanoTime();
        jdbcTemplate.update("INSERT INTO sys_project (id, project_code, project_name) VALUES (?, ?, ?)",
                projectId, "ct_air_" + projectId, "契约AI推荐系统");

        String suffix = String.valueOf(System.nanoTime());
        master = newTable("ct_air_stock_" + suffix, "契约库存", "CtAirStock" + suffix);
        child = newTable("ct_air_item_" + suffix, "契约明细", "CtAirItem" + suffix);
        // status：现值 isQuery=1（规则默认 0 → 已手改）；queryType 现值 LIKE（建议 EQ，不选则不动）
        insertColumn(master.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1);
        statusColumnId = insertColumn(master.getId(), "status", "状态", "tinyint", "status", "Integer", 0, 1, 2);
        jdbcTemplate.update("UPDATE gen_table_column SET is_query = 1, query_type = 'LIKE' WHERE id = ?", statusColumnId);
        // name：全部规则默认（已手改=false）
        insertColumn(master.getId(), "name", "名称", "varchar(64)", "name", "String", 0, 1, 3);
        insertColumn(child.getId(), "id", "主键", "bigint", "id", "Long", 1, 1, 1);

        pipelineNo = "PPL_AIR_" + suffix;
        pipelineId = System.nanoTime();
        jdbcTemplate.update("INSERT INTO gen_pipeline (id, pipeline_no, system_name, db_name, mode, seed_mode, status, project_id) "
                        + "VALUES (?, ?, ?, ?, 'ai', 'off', 'success', ?)",
                pipelineId, pipelineNo, "契约AI推荐", "ct_air_db", projectId);

        Files.createDirectories(logDir.resolve(pipelineNo));
        Files.writeString(logDir.resolve(pipelineNo).resolve("ai_recommend.json"), """
                {"tables":[
                  {"tableName":"%s","moduleName":"inventory","columns":[
                    {"columnName":"status","isQuery":0,"queryType":"EQ","htmlType":"select","dictType":"sys_status","sort":9},
                    {"columnName":"name","isQuery":1,"queryType":"LIKE"}
                  ]},
                  {"tableName":"%s","masterTableName":"%s"}
                ],
                 "menus":[{"moduleName":"inventory","dirLabel":"库存管理","icon":"el-icon-box","sort":1,"tables":["%s","%s"]}],
                 "drops":["表 ghost：表不存在，整表建议丢弃"]}
                """.formatted(master.getTableName(), child.getTableName(), master.getTableName(),
                master.getTableName(), child.getTableName()));
    }

    @AfterEach
    void tearDown() {
        props.setLogDir(oldLogDir);
    }

    // ------------------------------------------------------------------
    // AC-3：预览只读
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-3：预览返回 diff（含已手改标记），且不产生任何 UPDATE")
    void previewIsReadOnly() {
        String before = snapshot();

        GenAiRecommendPreviewVO vo = aiRecommendService.preview(pipelineId);

        assertTrue(vo.isExists(), "产物存在");
        assertEquals(1, vo.getDrops().size());
        assertEquals(1, vo.getMenus().size());
        GenAiRecommendPreviewVO.TablePreview tp = vo.getTables().stream()
                .filter(t -> master.getTableName().equals(t.getTableName())).findFirst().orElseThrow();
        // 表级 moduleName diff：现值=规则推断默认 → manual=false
        GenAiRecommendPreviewVO.FieldDiff module = tp.getTableFields().stream()
                .filter(f -> "moduleName".equals(f.getField())).findFirst().orElseThrow();
        assertEquals("inventory", module.getSuggested());
        assertFalse(module.isManual(), "moduleName 现值等于规则默认，不是手改");
        // status.isQuery：建议 0 vs 现值 1（手改）→ manual=true
        GenAiRecommendPreviewVO.ColumnPreview status = tp.getColumns().stream()
                .filter(c -> "status".equals(c.getColumnName())).findFirst().orElseThrow();
        GenAiRecommendPreviewVO.FieldDiff isQuery = status.getFields().stream()
                .filter(f -> "isQuery".equals(f.getField())).findFirst().orElseThrow();
        assertEquals(0, isQuery.getSuggested());
        assertEquals(1, isQuery.getCurrent());
        assertTrue(isQuery.isManual(), "现值偏离规则默认，要标已手改");
        // name.isQuery：建议 1 vs 现值 0（=默认）→ manual=false
        GenAiRecommendPreviewVO.ColumnPreview name = tp.getColumns().stream()
                .filter(c -> "name".equals(c.getColumnName())).findFirst().orElseThrow();
        assertFalse(name.getFields().get(0).isManual());
        // 与现值一致的建议不出现（status.queryType 现值 LIKE vs 建议 EQ？——出现；但 status.isList 无建议）
        assertTrue(tp.getColumns().stream().noneMatch(c -> "id".equals(c.getColumnName())), "无建议的列不出现");

        assertEquals(before, snapshot(), "预览不得写库（AC-3）");
    }

    // ------------------------------------------------------------------
    // AC-4：勾选应用才写库
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-4：选中字段与建议一致写库，未选中字段保持现值；主子/菜单按名解析")
    void applyWritesOnlySelected() {
        GenAiRecommendApplyDTO dto = new GenAiRecommendApplyDTO();
        GenAiRecommendApplyDTO.TablePick pick = new GenAiRecommendApplyDTO.TablePick();
        pick.setTableName(master.getTableName());
        pick.setFields(List.of("moduleName"));
        GenAiRecommendApplyDTO.ColumnPick status = new GenAiRecommendApplyDTO.ColumnPick();
        status.setColumnName("status");
        // 选 isQuery/htmlType/dictType，不选 queryType/sort
        status.setFields(List.of("isQuery", "htmlType", "dictType"));
        pick.setColumns(List.of(status));
        GenAiRecommendApplyDTO.TablePick childPick = new GenAiRecommendApplyDTO.TablePick();
        childPick.setTableName(child.getTableName());
        childPick.setFields(List.of("masterTable"));
        dto.setTables(List.of(pick, childPick));
        dto.setMenus(List.of("inventory"));

        int applied = aiRecommendService.apply(pipelineId, dto);
        assertTrue(applied >= 5, "至少 5 个字段写库，实际 " + applied);

        GenTable after = genTableMapper.selectById(master.getId());
        assertEquals("inventory", after.getModuleName(), "表级 moduleName 按建议写库");

        GenTableColumn statusAfter = genTableColumnMapper.selectById(statusColumnId);
        assertEquals(0, statusAfter.getIsQuery(), "选中的 isQuery 按建议写库");
        assertEquals("select", statusAfter.getHtmlType(), "选中的 htmlType 按建议写库");
        assertEquals("sys_status", statusAfter.getDictType(), "选中的 dictType 按建议写库");
        assertEquals("LIKE", statusAfter.getQueryType(), "未选中的 queryType 保持现值 LIKE");
        assertEquals(2, statusAfter.getSort(), "未选中的 sort 保持现值");

        GenTableColumn nameAfter = jdbcTemplate.queryForObject(
                "SELECT * FROM gen_table_column WHERE table_id = ? AND column_name = 'name'",
                (rs, n) -> {
                    GenTableColumn c = new GenTableColumn();
                    c.setIsQuery(rs.getInt("is_query"));
                    return c;
                }, master.getId());
        assertNotNull(nameAfter);
        assertEquals(0, nameAfter.getIsQuery(), "name 列整列未选，保持现值");

        GenTable childAfter = genTableMapper.selectById(child.getId());
        assertEquals(master.getId(), childAfter.getMasterTableId(), "masterTableName 解析成 master_table_id");
        assertEquals("inventory", childAfter.getModuleName(), "菜单勾选把成员表归入模块");
    }

    @Test
    @DisplayName("产物不存在：preview.exists=false，apply 抛业务异常")
    void noArtifact() {
        long noneId = System.nanoTime();
        jdbcTemplate.update("INSERT INTO gen_pipeline (id, pipeline_no, system_name, db_name, mode, seed_mode, status, project_id) "
                + "VALUES (?, 'PPL_AIR_NONE', '无产物', 'ct_air_db', 'ai', 'off', 'success', ?)", noneId, projectId);

        assertFalse(aiRecommendService.preview(noneId).isExists());
        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> aiRecommendService.apply(noneId, new GenAiRecommendApplyDTO()));
        assertTrue(e.getMessage().contains("产物"));
    }

    // ------------------------------------------------------------------ fixtures

    private GenTable newTable(String tableName, String comment, String className) {
        GenTable t = new GenTable();
        t.setTableName(tableName);
        t.setTableComment(comment);
        t.setClassName(className);
        // moduleName 现值=规则默认（GenUtils.inferModuleName），保证预览 manual=false
        t.setModuleName(GenUtils.inferModuleName(tableName));
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setBackendRoot(logDir.toString());
        t.setFrontendRoot(logDir.toString());
        t.setGatewayPrefix("demo");
        t.setAuthor("contract-test");
        t.setProjectId(projectId);
        genTableMapper.insert(t);
        return t;
    }

    private Long insertColumn(Long tableId, String name, String comment, String dbType,
                              String javaField, String javaType, int isPk, int isList, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setTableId(tableId);
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(isPk);
        c.setIsRequired(0);
        c.setIsQuery(0);
        c.setQueryType("EQ");
        c.setIsList(isList);
        c.setIsForm(1);
        c.setHtmlType(GenUtils.inferHtmlType(dbType, javaType, name));
        c.setSort(sort);
        genTableColumnMapper.insert(c);
        return c.getId();
    }

    /** 台账快照：两张表 + 全部列的关键字段，检测任何 UPDATE */
    private String snapshot() {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT CONCAT(t.id, '|', t.module_name, '|', IFNULL(t.master_table_id,''), '|', "
                        + "c.column_name, '|', c.is_query, '|', c.query_type, '|', c.is_list, '|', c.is_form, '|', "
                        + "IFNULL(c.html_type,''), '|', IFNULL(c.dict_type,''), '|', c.sort) "
                        + "FROM gen_table t JOIN gen_table_column c ON c.table_id = t.id "
                        + "WHERE t.id IN (?, ?) ORDER BY t.id, c.sort",
                String.class, master.getId(), child.getId());
        return String.join("\n", rows);
    }
}
