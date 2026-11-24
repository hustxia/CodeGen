package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import com.codegen.config.GenTemplatePackProperties;
import com.codegen.service.GenTemplatePackService;
import com.codegen.vo.GenAiTemplateVO;
import com.codegen.vo.GenPipelineAiVO.Column;
import com.codegen.vo.GenPipelineAiVO.TableModel;
import com.codegen.vo.GenTemplatePackImportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.19 模板市场：校验 / 解析 / 覆盖策略 / 导入导出闭环。
 *
 * <p>校验与编解码是纯函数，单独测；导入覆盖与删除走真实临时目录 + 真实服务，
 * 验证「默认拒绝、overwrite 才覆盖、删除后回落预置包」三条关键语义。
 */
class GenTemplatePackTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TemplatePackValidator.Limits LIMITS =
            new TemplatePackValidator.Limits(200, 48, 5L * 1024 * 1024);

    @TempDir
    Path tempDir;

    private GenTemplatePackProperties props;
    private IndustryTemplateRegistry registry;
    private GenTemplatePackService service;

    @BeforeEach
    void setUp() {
        props = new GenTemplatePackProperties();
        props.setExternalDir(tempDir.toString());
        props.setMaxPackSizeMb(5);
        props.setMaxTables(200);
        props.setMaxKeyLength(48);

        registry = new IndustryTemplateRegistry();
        registry.setPackProperties(props);
        registry.reload();
        service = new GenTemplatePackService(registry, props);
    }

    @Test
    void validPackPassesAndJsonRoundTrips() throws Exception {
        GenAiTemplateVO vo = validTemplate("demo_pack");
        assertTrue(TemplatePackValidator.validate(vo, LIMITS).ok());

        byte[] json = TemplatePackCodec.toJson(vo, MAPPER);
        TemplatePackCodec.Parsed parsed = TemplatePackCodec.parseJson(json, "demo_pack.json", MAPPER);
        assertTrue(parsed.ok(), "合法 JSON 不应有解析错误：" + parsed.errors());
        assertEquals(1, parsed.templates().size());
        assertEquals("demo_pack", parsed.templates().get(0).getKey());
    }

    @Test
    void missingFieldsReportEveryErrorAtOnce() {
        GenAiTemplateVO vo = new GenAiTemplateVO();
        GenAiTemplateVO.Module module = new GenAiTemplateVO.Module();
        TableModel table = new TableModel();
        Column column = new Column();
        column.setName("user-name");
        column.setType("");
        table.getColumns().add(column);
        module.getTables().add(table);
        vo.getModules().add(module);

        TemplatePackValidator.Result result = TemplatePackValidator.validate(vo, LIMITS);
        assertFalse(result.ok());
        assertTrue(contains(result.errors(), "缺少模板 key"), result.errors().toString());
        assertTrue(contains(result.errors(), "缺少模板 name"), result.errors().toString());
        assertTrue(contains(result.errors(), "缺少模板 category"), result.errors().toString());
        assertTrue(contains(result.errors(), "缺少 name"), result.errors().toString());
        assertTrue(contains(result.errors(), "缺少 tableName"), result.errors().toString());
        assertTrue(contains(result.errors(), "列名不合法"), result.errors().toString());
        assertTrue(contains(result.errors(), "缺少 type"), result.errors().toString());
    }

    @Test
    void illegalIdentifiersAndTooLongKeyRejected() {
        GenAiTemplateVO vo = validTemplate("Bad-Key");
        vo.getModules().get(0).getTables().get(0).setTableName("order-item");
        vo.getModules().get(0).getTables().get(0).getColumns().get(0).setName("OrderNo");

        TemplatePackValidator.Result result = TemplatePackValidator.validate(vo, LIMITS);
        assertFalse(result.ok());
        assertTrue(contains(result.errors(), "模板 key 不合法"), result.errors().toString());
        assertTrue(contains(result.errors(), "表名不合法"), result.errors().toString());
        assertTrue(contains(result.errors(), "列名不合法"), result.errors().toString());

        GenAiTemplateVO longKey = validTemplate("k" + "a".repeat(48));
        TemplatePackValidator.Result longResult = TemplatePackValidator.validate(longKey, LIMITS);
        assertTrue(contains(longResult.errors(), "过长"), longResult.errors().toString());
    }

    @Test
    void tableLimitExceededRejected() {
        GenAiTemplateVO vo = validTemplate("too_many_tables");
        GenAiTemplateVO.Module module = vo.getModules().get(0);
        module.getTables().clear();
        for (int i = 0; i < 201; i++) {
            TableModel table = new TableModel();
            table.setTableName("t_" + i);
            Column column = new Column();
            column.setName("id");
            column.setType("bigint");
            table.getColumns().add(column);
            module.getTables().add(table);
        }
        TemplatePackValidator.Result result = TemplatePackValidator.validate(vo, LIMITS);
        assertFalse(result.ok());
        assertTrue(contains(result.errors(), "表数量超限"), result.errors().toString());
    }

    @Test
    void safeBasenameRejectsTraversal() {
        assertEquals("", TemplatePackCodec.safeBasename("../evil.json"));
        assertEquals("", TemplatePackCodec.safeBasename("a/../../b.json"));
        assertEquals("", TemplatePackCodec.safeBasename("..\\evil.json"));
        assertEquals("ok.json", TemplatePackCodec.safeBasename("C:\\tmp\\ok.json"));
        assertEquals("ok.json", TemplatePackCodec.safeBasename("pack/ok.json"));
    }

    @Test
    void zipWithTraversalEntryIsRejectedButValidEntryKept() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../evil.json", TemplatePackCodec.toJson(validTemplate("evil_pack"), MAPPER));
        entries.put("ok.json", TemplatePackCodec.toJson(validTemplate("zip_tpl"), MAPPER));

        TemplatePackCodec.Parsed parsed =
                TemplatePackCodec.parseZip(zip(entries), LIMITS.maxPackBytes(), MAPPER);

        assertEquals(1, parsed.templates().size(), "合法条目应保留");
        assertEquals("zip_tpl", parsed.templates().get(0).getKey());
        assertTrue(contains(parsed.errors(), "路径非法"), parsed.errors().toString());
    }

    @Test
    void oversizePackRejectedBeforeParse() {
        props.setMaxPackSizeMb(1);
        byte[] body = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.json", "application/json", body);

        GenTemplatePackImportVO result = service.importPack(file, false);

        assertFalse(result.isSuccess());
        assertTrue(contains(result.getErrors(), "过大"), result.getErrors().toString());
        assertNull(registry.get("big"), "超大包不应写入任何模板");
    }

    @Test
    void importRejectsIllegalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../evil.json", "application/json", new byte[]{'{', '}'});
        GenTemplatePackImportVO result = service.importPack(file, false);
        assertFalse(result.isSuccess());
        assertTrue(contains(result.getErrors(), "文件名非法"), result.getErrors().toString());
    }

    @Test
    void importZipsAllOrNothing() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("good.json", TemplatePackCodec.toJson(validTemplate("pack_tpl_a"), MAPPER));
        GenAiTemplateVO bad = validTemplate("pack_tpl_b");
        bad.setName(" ");
        entries.put("bad.json", TemplatePackCodec.toJson(bad, MAPPER));

        MockMultipartFile file = new MockMultipartFile(
                "file", "mixed.zip", "application/zip", zip(entries));
        GenTemplatePackImportVO result = service.importPack(file, false);

        assertFalse(result.isSuccess());
        assertTrue(contains(result.getErrors(), "缺少模板 name"), result.getErrors().toString());
        assertNull(registry.get("pack_tpl_a"), "任一模板非法时整包不落盘");
        assertTrue(Files.notExists(tempDir.resolve("pack_tpl_a.json")));
    }

    /**
     * 闭环：导出预置包 -> 原样导入（默认拒绝）-> overwrite 覆盖 -> 再导出/打包 -> 删除回落预置。
     */
    @Test
    void exportImportOverwriteDeleteRoundTrip() throws Exception {
        GenTemplatePackService.PackFile exported = service.export("ecommerce");
        assertTrue(exported.filename().startsWith("template-ecommerce-"));
        assertTrue(exported.filename().endsWith(".json"));
        assertEquals("application/json", exported.contentType());

        TemplatePackCodec.Parsed parsed =
                TemplatePackCodec.parseJson(exported.content(), "ecommerce.json", MAPPER);
        assertEquals(1, parsed.templates().size());
        assertEquals("ecommerce", parsed.templates().get(0).getKey());
        System.out.println("[V9.19] 导出 ecommerce：" + exported.filename()
                + "，" + exported.content().length + " 字节");

        MockMultipartFile file = new MockMultipartFile(
                "file", "ecommerce.json", "application/json", exported.content());
        GenTemplatePackImportVO rejected = service.importPack(file, false);
        assertFalse(rejected.isSuccess());
        assertTrue(contains(rejected.getErrors(), "已存在"), rejected.getErrors().toString());
        assertTrue(contains(rejected.getErrors(), "预置包"), rejected.getErrors().toString());
        System.out.println("[V9.19] 默认拒绝覆盖：" + rejected.getErrors());

        GenTemplatePackImportVO overwritten = service.importPack(file, true);
        assertTrue(overwritten.isSuccess(), overwritten.getErrors().toString());
        assertEquals(1, overwritten.getImported());
        assertEquals(1, overwritten.getOverwritten());
        assertEquals("user", registry.get("ecommerce").getSource());
        assertNotNull(registry.userFilePath("ecommerce"));
        assertTrue(Files.exists(tempDir.resolve("ecommerce.json")));
        System.out.println("[V9.19] overwrite=true 导入成功：" + overwritten.getKeys()
                + "，来源=" + registry.get("ecommerce").getSource());

        GenTemplatePackService.PackFile zipped = service.exportZip("ecommerce,crm");
        assertTrue(zipped.filename().endsWith(".zip"));
        TemplatePackCodec.Parsed zipParsed =
                TemplatePackCodec.parseZip(zipped.content(), LIMITS.maxPackBytes(), MAPPER);
        assertEquals(2, zipParsed.templates().size());
        System.out.println("[V9.19] 多选导出 ZIP：" + zipped.filename()
                + "，" + zipped.content().length + " 字节，含 " + zipParsed.templates().size() + " 套模板");

        service.deleteUserPack("ecommerce");
        assertEquals("builtin", registry.get("ecommerce").getSource(), "删除用户包后应回落到预置包");
        assertNull(registry.userFilePath("ecommerce"));
        System.out.println("[V9.19] 删除用户包后 ecommerce 来源=" + registry.get("ecommerce").getSource());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteUserPack("crm"));
        assertTrue(ex.getMessage().contains("预置模板不可删除"), ex.getMessage());
    }

    private static GenAiTemplateVO validTemplate(String key) {
        GenAiTemplateVO vo = new GenAiTemplateVO();
        vo.setKey(key);
        vo.setName("测试模板");
        vo.setDesc("单元测试用");
        vo.setCategory("测试分类");
        GenAiTemplateVO.Module module = new GenAiTemplateVO.Module();
        module.setName("基础数据");
        TableModel table = new TableModel();
        table.setTableName("demo_order");
        table.setTableComment("演示订单");
        Column id = new Column();
        id.setName("id");
        id.setType("bigint");
        id.setPk(true);
        Column no = new Column();
        no.setName("order_no");
        no.setType("varchar(64)");
        table.getColumns().add(id);
        table.getColumns().add(no);
        module.getTables().add(table);
        vo.getModules().add(module);
        return vo;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static boolean contains(List<String> errors, String keyword) {
        return errors.stream().anyMatch(e -> e.contains(keyword));
    }
}
