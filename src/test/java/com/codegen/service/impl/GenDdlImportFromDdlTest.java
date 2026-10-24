package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.dto.GenColumnDTO;
import com.codegen.dto.GenDdlImportDTO;
import com.codegen.dto.GenTableSaveDTO;
import com.codegen.service.DictDetectService;
import com.codegen.service.DictService;
import com.codegen.service.GenTableService;
import com.codegen.vo.DictDataVO;
import com.codegen.vo.DictDetectItemVO;
import com.codegen.vo.GenDdlImportVO;
import com.codegen.vo.GenTableVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DDL 文本一键导入（importFromDdl）单元测试。
 *
 * <p>不依赖数据库：GenTableService / DictDetectService 全部 mock，
 * 重点验证——多表拆分、EXISTS 跳过、失败不连坐、
 * 以及「字典信息正确导入」的两层校验（列数比对 + dict_type 回填检查）。
 */
class GenDdlImportFromDdlTest {

    private static final String TWO_TABLE_DDL = """
            SET NAMES utf8mb4;
            CREATE TABLE `demo_order` (
              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
              `order_no` varchar(32) NOT NULL COMMENT '订单号',
              `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:1待支付 2已支付 3已取消',
              `remark` varchar(255) NULL,
              PRIMARY KEY (`id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
            CREATE TABLE `demo_order_log` (
              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
              `order_id` bigint NOT NULL COMMENT '订单ID',
              PRIMARY KEY (`id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单日志表';
            """;

    private GenDdlServiceImpl service;
    private GenTableService genTableService;
    private DictDetectService dictDetectService;
    private DictService dictService;

    @BeforeEach
    void setUp() {
        service = new GenDdlServiceImpl();
        genTableService = mock(GenTableService.class);
        dictDetectService = mock(DictDetectService.class);
        dictService = mock(DictService.class);
        ReflectionTestUtils.setField(service, "genTableService", genTableService);
        ReflectionTestUtils.setField(service, "dictDetectService", dictDetectService);
        ReflectionTestUtils.setField(service, "dictService", dictService);
        // 默认绑定的字典都有选项；「空字典绑定」用例里单独覆盖为空
        when(dictService.listData(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(new DictDataVO()));
    }

    private GenDdlImportDTO dto(String ddl) {
        GenDdlImportDTO dto = new GenDdlImportDTO();
        dto.setDdl(ddl);
        dto.setProjectId(9L);
        return dto;
    }

    private DictDetectItemVO candidate(String table, String column) {
        DictDetectItemVO vo = new DictDetectItemVO();
        vo.setTableName(table);
        vo.setColumnName(column);
        vo.setDictCode(table + "_" + column);
        return vo;
    }

    private GenColumnDTO col(String name, String comment, String dictType) {
        GenColumnDTO c = new GenColumnDTO();
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setDictType(dictType);
        return c;
    }

    private void mockSavedTable(Long tableId, String comment, List<GenColumnDTO> columns) {
        GenTableVO vo = new GenTableVO();
        vo.setId(tableId);
        vo.setTableComment(comment);
        vo.setColumns(columns);
        when(genTableService.detail(tableId)).thenReturn(vo);
    }

    @Test
    @DisplayName("多表 DDL：非建表语句被忽略，逐表导入并自动建字典")
    void multiTableImport() {
        when(genTableService.existsByTableName(any())).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(100L);
        when(dictDetectService.detectFromDdl(eq("demo_order"), any())).thenReturn(List.of(candidate("demo_order", "status")));
        when(dictDetectService.detectFromDdl(eq("demo_order_log"), any())).thenReturn(List.of());
        when(dictDetectService.apply(eq(100L), isNull())).thenReturn(1);
        // parseDdl 设计上跳过 BaseEntity 公共列（id/deleted/create_* 等），主键由 PRIMARY KEY 子句识别
        mockSavedTable(100L, "订单表", List.of(
                col("order_no", "订单号", null),
                col("status", "状态:1待支付 2已支付 3已取消", "demo_order_status"),
                col("remark", "remark", null)));

        List<GenDdlImportVO> report = service.importFromDdl(dto(TWO_TABLE_DDL));

        assertEquals(2, report.size());
        GenDdlImportVO order = report.get(0);
        assertEquals("demo_order", order.getTableName());
        assertEquals("SAVED", order.getStatus());
        assertEquals(3, order.getColumnCount());
        assertEquals(1, order.getDictCount());
        assertTrue(order.getDictMissingColumns().isEmpty(), "status 的 dict_type 已回填，不应预警");
        assertEquals(List.of("remark"), order.getMissingCommentColumns(), "remark 缺列注释");
        assertTrue(order.getWarnings().stream().anyMatch(w -> w.contains("缺列注释")));
        // projectId 透传到 saveConfig，包名/路径留空走回落链；两张表各 save 一次
        org.mockito.ArgumentCaptor<GenTableSaveDTO> captor =
                org.mockito.ArgumentCaptor.forClass(GenTableSaveDTO.class);
        org.mockito.Mockito.verify(genTableService, org.mockito.Mockito.times(2)).saveConfig(captor.capture());
        assertEquals(9L, captor.getAllValues().get(0).getProjectId());
        assertEquals("demo_order", captor.getAllValues().get(0).getTableName());
        assertTrue(captor.getAllValues().get(0).getPackageName().isEmpty(), "包名应留空走回落链");
    }

    @Test
    @DisplayName("表已存在：跳过并标 EXISTS，不抛异常")
    void existsSkipped() {
        when(genTableService.existsByTableName("demo_order")).thenReturn(true);

        String ddl = "CREATE TABLE `demo_order` (`id` bigint NOT NULL COMMENT '主键', PRIMARY KEY (`id`)) COMMENT='订单表';";
        List<GenDdlImportVO> report = service.importFromDdl(dto(ddl));

        assertEquals(1, report.size());
        assertEquals("EXISTS", report.get(0).getStatus());
        assertTrue(report.get(0).getMessage().contains("跳过"));
    }

    @Test
    @DisplayName("单表失败不连坐：无主键表标 FAILED，其余照常导入")
    void failedIsolated() {
        when(genTableService.existsByTableName("demo_order")).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(101L);
        when(dictDetectService.detectFromDdl(eq("demo_order"), any())).thenReturn(List.of());
        mockSavedTable(101L, "订单表", List.of(col("id", "主键", null)));

        String ddl = TWO_TABLE_DDL + """
                CREATE TABLE `demo_bad` (
                  `name` varchar(32) NOT NULL COMMENT '名称'
                ) COMMENT='坏表';
                """;
        List<GenDdlImportVO> report = service.importFromDdl(dto(ddl));

        assertEquals(3, report.size());
        assertEquals("FAILED", report.get(2).getStatus());
        assertEquals("demo_bad", report.get(2).getTableName());
        assertTrue(report.get(2).getMessage().contains("主键"));
        assertEquals("SAVED", report.get(0).getStatus(), "前一张表不受影响");
    }

    @Test
    @DisplayName("字典漏建预警：候选列 dict_type 未回填时进 dictMissingColumns")
    void dictMissingWarning() {
        when(genTableService.existsByTableName(any())).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(102L);
        when(dictDetectService.detectFromDdl(eq("demo_order"), any()))
                .thenReturn(List.of(candidate("demo_order", "status"), candidate("demo_order", "pay_type")));
        when(dictDetectService.apply(eq(102L), isNull())).thenReturn(1);
        // pay_type 的 dict_type 没有回填
        mockSavedTable(102L, "订单表", List.of(
                col("id", "主键", null),
                col("order_no", "订单号", null),
                col("status", "状态", "demo_order_status"),
                col("pay_type", "支付方式", "")));

        List<GenDdlImportVO> report = service.importFromDdl(dto(
                "CREATE TABLE `demo_order` (`id` bigint NOT NULL COMMENT '主键', `order_no` varchar(32) NOT NULL COMMENT '订单号',"
                + " `status` tinyint NOT NULL COMMENT '状态:1待支付 2已支付', `pay_type` tinyint NOT NULL COMMENT '支付方式:1现金 2刷卡',"
                + " PRIMARY KEY (`id`)) COMMENT='订单表';"));

        GenDdlImportVO vo = report.get(0);
        assertEquals("SAVED", vo.getStatus());
        assertEquals(List.of("pay_type"), vo.getDictMissingColumns());
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("疑似枚举但字典未落库")));
    }

    @Test
    @DisplayName("apply 抛异常：仅记警告，表仍导入成功")
    void dictApplyFailureNotFatal() {
        when(genTableService.existsByTableName(any())).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(103L);
        when(dictDetectService.detectFromDdl(eq("demo_order"), any()))
                .thenReturn(List.of(candidate("demo_order", "status")));
        when(dictDetectService.apply(eq(103L), isNull())).thenThrow(new RuntimeException("dict db error"));
        mockSavedTable(103L, "订单表", List.of(
                col("id", "主键", null), col("status", "状态", null)));

        List<GenDdlImportVO> report = service.importFromDdl(dto(
                "CREATE TABLE `demo_order` (`id` bigint NOT NULL COMMENT '主键',"
                + " `status` tinyint NOT NULL COMMENT '状态:1待支付 2已支付', PRIMARY KEY (`id`)) COMMENT='订单表';"));

        GenDdlImportVO vo = report.get(0);
        assertEquals("SAVED", vo.getStatus());
        assertEquals(0, vo.getDictCount());
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("自动建字典失败")));
    }

    @Test
    @DisplayName("落库列数与 DDL 解析列数不一致：出校验警告")
    void columnCountMismatchWarning() {
        when(genTableService.existsByTableName(any())).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(104L);
        when(dictDetectService.detectFromDdl(eq("demo_order"), any())).thenReturn(List.of());
        // 模拟 saveConfig 落库丢列：DDL 解析出 3 列（id 为 BaseEntity 列被跳过），落库只有 2 列
        mockSavedTable(104L, "订单表", List.of(
                col("order_no", "订单号", null), col("status", "状态", null)));

        List<GenDdlImportVO> report = service.importFromDdl(dto(
                "CREATE TABLE `demo_order` (`id` bigint NOT NULL COMMENT '主键', `order_no` varchar(32) NOT NULL COMMENT '订单号',"
                + " `status` tinyint NOT NULL COMMENT '状态', `extra_col` varchar(32) NULL COMMENT '额外',"
                + " PRIMARY KEY (`id`)) COMMENT='订单表';"));

        GenDdlImportVO vo = report.get(0);
        assertEquals("SAVED", vo.getStatus());
        assertEquals(2, vo.getColumnCount());
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("不一致")),
                "warnings 实际为: " + vo.getWarnings() + ", 落库列数=" + vo.getColumnCount());
    }

    @Test
    @DisplayName("applyDict=false：不建字典也不出字典预警")
    void applyDictDisabled() {
        when(genTableService.existsByTableName(any())).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(105L);
        mockSavedTable(105L, "订单表", List.of(
                col("id", "主键", null), col("status", "状态:1待支付 2已支付", null)));

        GenDdlImportDTO d = dto("CREATE TABLE `demo_order` (`id` bigint NOT NULL COMMENT '主键',"
                + " `status` tinyint NOT NULL COMMENT '状态:1待支付 2已支付', PRIMARY KEY (`id`)) COMMENT='订单表';");
        d.setApplyDict(false);

        List<GenDdlImportVO> report = service.importFromDdl(d);

        GenDdlImportVO vo = report.get(0);
        assertEquals("SAVED", vo.getStatus());
        assertEquals(0, vo.getDictCount());
        assertTrue(vo.getDictMissingColumns().isEmpty());
    }

    @Test
    @DisplayName("空字典绑定预警：绑定了 *_status 通用字典但字典无选项")
    void emptyDictBindingWarning() {
        when(genTableService.existsByTableName(any())).thenReturn(false);
        when(genTableService.saveConfig(any(GenTableSaveDTO.class))).thenReturn(106L);
        when(dictDetectService.detectFromDdl(eq("demo_order"), any())).thenReturn(List.of());
        // *_status 列被 inferDictType 预设绑定 sys_status，但 sys_status 没有任何字典项
        when(dictService.listData("sys_status")).thenReturn(List.of());
        mockSavedTable(106L, "订单表", List.of(
                col("order_no", "订单号", null),
                col("order_status", "订单状态:1草稿 2已生效", "sys_status")));

        List<GenDdlImportVO> report = service.importFromDdl(dto(
                "CREATE TABLE `demo_order` (`order_no` varchar(32) NOT NULL COMMENT '订单号',"
                + " `order_status` tinyint NOT NULL COMMENT '订单状态:1草稿 2已生效', PRIMARY KEY (`order_no`)) COMMENT='订单表';"));

        GenDdlImportVO vo = report.get(0);
        assertEquals("SAVED", vo.getStatus());
        assertTrue(vo.getWarnings().stream()
                .anyMatch(w -> w.contains("没有任何选项") && w.contains("order_status")),
                "warnings 实际为: " + vo.getWarnings());
    }

    @Test
    @DisplayName("DDL 里没有 CREATE TABLE：整体报错")
    void noCreateTableThrows() {
        assertThrows(BusinessException.class, () -> service.importFromDdl(dto("SET NAMES utf8mb4;")));
    }
}
