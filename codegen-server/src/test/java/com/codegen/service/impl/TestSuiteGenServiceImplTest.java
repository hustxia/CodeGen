package com.codegen.service.impl;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.entity.TestCase;
import com.codegen.entity.TestSuite;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.TestCaseMapper;
import com.codegen.mapper.TestSuiteMapper;
import com.codegen.vo.TestVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V9.2 F-15：按表生成冒烟用例的核心逻辑（create / update-on-unchanged-fingerprint / preserve-manual）。
 * 纯 Mockito，不依赖数据库——四个 mapper 全部打桩。
 */
@ExtendWith(MockitoExtension.class)
class TestSuiteGenServiceImplTest {

    @Mock
    private GenTableMapper genTableMapper;

    @Mock
    private GenTableColumnMapper columnMapper;

    @Mock
    private TestSuiteMapper suiteMapper;

    @Mock
    private TestCaseMapper caseMapper;

    @InjectMocks
    private TestSuiteGenServiceImpl service;

    /** 全开关表：期望生成 新增/分页/详情/修改/删除/必填校验 = 6 条用例 */
    private GenTable fullTable() {
        GenTable t = new GenTable();
        t.setId(1L);
        t.setProjectId(1L);
        t.setTableName("demo_t");
        t.setTableComment("演示表");
        t.setModuleName("demo");
        t.setGenAddApi(1);
        t.setGenPageApi(1);
        t.setGenDetailApi(1);
        t.setGenEditApi(1);
        t.setGenDeleteApi(1);
        return t;
    }

    /** 仅新增开关、无必填列：期望只生成 1 条「新增」用例 */
    private GenTable addOnlyTable() {
        GenTable t = new GenTable();
        t.setId(2L);
        t.setProjectId(1L);
        t.setTableName("demo_add");
        t.setTableComment("仅新增");
        t.setModuleName("demo");
        t.setGenAddApi(1);
        t.setGenPageApi(0);
        t.setGenDetailApi(0);
        t.setGenEditApi(0);
        t.setGenDeleteApi(0);
        return t;
    }

    private List<GenTableColumn> columns(boolean withRequired) {
        GenTableColumn pk = new GenTableColumn();
        pk.setTableId(1L);
        pk.setColumnName("id");
        pk.setJavaField("id");
        pk.setJavaType("Long");
        pk.setIsPk(1);
        pk.setIsForm(0);
        pk.setIsRequired(0);

        GenTableColumn name = new GenTableColumn();
        name.setTableId(1L);
        name.setColumnName("name");
        name.setJavaField("name");
        name.setJavaType("String");
        name.setIsPk(0);
        name.setIsForm(1);
        name.setIsRequired(withRequired ? 1 : 0);
        name.setValidateRule("maxlen=20");

        GenTableColumn age = new GenTableColumn();
        age.setTableId(1L);
        age.setColumnName("age");
        age.setJavaField("age");
        age.setJavaType("Integer");
        age.setIsPk(0);
        age.setIsForm(1);
        age.setIsRequired(0);

        return List.of(pk, name, age);
    }

    @Test
    void generate_createsSuiteAndCasesWhenNoneExist() {
        when(genTableMapper.selectById(1L)).thenReturn(fullTable());
        when(columnMapper.selectList(any())).thenReturn(columns(true));
        when(suiteMapper.selectOne(any())).thenReturn(null);
        when(caseMapper.selectOne(any())).thenReturn(null);

        TestVO.GenResult r = service.generate(1L);

        verify(suiteMapper, times(1)).insert(any(TestSuite.class));
        verify(caseMapper, times(6)).insert(any(TestCase.class));
        assertEquals(6, r.created());
        assertEquals(0, r.updated());
        assertEquals(0, r.skipped());
    }

    @Test
    void generate_updatesWhenFingerprintUnchanged() {
        // 单用例表（仅新增开关、无必填列）→ 只生成 1 条「新增」
        when(genTableMapper.selectById(2L)).thenReturn(addOnlyTable());
        when(columnMapper.selectList(any())).thenReturn(columns(false));
        when(suiteMapper.selectOne(any())).thenReturn(null);
        when(caseMapper.selectOne(any())).thenReturn(null);

        // 第一次：创建并捕获插入的用例（携带正确指纹）
        service.generate(2L);
        ArgumentCaptor<TestSuite> suiteCap = ArgumentCaptor.forClass(TestSuite.class);
        verify(suiteMapper).insert(suiteCap.capture());
        ArgumentCaptor<TestCase> caseCap = ArgumentCaptor.forClass(TestCase.class);
        verify(caseMapper).insert(caseCap.capture());
        TestSuite existingSuite = suiteCap.getValue();
        TestCase existingCase = caseCap.getValue();

        // 第二次：套件已存在，且用例已存在且指纹一致 → 应走更新分支而非再插入
        when(suiteMapper.selectOne(any())).thenReturn(existingSuite);
        when(caseMapper.selectOne(any())).thenReturn(existingCase);

        TestVO.GenResult r = service.generate(2L);

        // 总计仍只有阶段一那 1 次插入，阶段二不应再新增；应走更新分支
        verify(caseMapper, times(1)).insert(any(TestCase.class));
        verify(caseMapper, atLeast(1)).updateById(any(TestCase.class));
        assertEquals(0, r.created());
        assertEquals(1, r.updated());
        assertEquals(0, r.skipped());
    }

    @Test
    void generate_preservesManualCase() {
        when(genTableMapper.selectById(2L)).thenReturn(addOnlyTable());
        when(columnMapper.selectList(any())).thenReturn(columns(false));
        when(suiteMapper.selectOne(any())).thenReturn(null);

        // 已存在同名「手工」用例 → 必须保留，不覆盖、不新增
        TestCase manual = new TestCase();
        manual.setId(999L);
        manual.setSource("manual");
        manual.setFingerprint("whatever");
        when(caseMapper.selectOne(any())).thenReturn(manual);

        TestVO.GenResult r = service.generate(2L);

        verify(suiteMapper, times(1)).insert(any(TestSuite.class)); // 套件仍创建
        verify(caseMapper, never()).insert(any(TestCase.class));    // 手工用例不被覆盖
        verify(caseMapper, never()).updateById(any(TestCase.class));
        assertEquals(0, r.created());
        assertEquals(0, r.updated());
        assertEquals(1, r.skipped());
    }

    @Test
    void generate_preservesManuallyModifiedCase() {
        when(genTableMapper.selectById(2L)).thenReturn(addOnlyTable());
        when(columnMapper.selectList(any())).thenReturn(columns(false));
        when(suiteMapper.selectOne(any())).thenReturn(null);

        // 已存在 generated 用例但指纹被改（手工改过）→ 同样保留，不更新
        TestCase modified = new TestCase();
        modified.setId(998L);
        modified.setSource("generated");
        modified.setFingerprint("deadbeef-stale");
        when(caseMapper.selectOne(any())).thenReturn(modified);

        TestVO.GenResult r = service.generate(2L);

        verify(suiteMapper, times(1)).insert(any(TestSuite.class));
        verify(caseMapper, never()).insert(any(TestCase.class));
        verify(caseMapper, never()).updateById(any(TestCase.class));
        assertEquals(0, r.created());
        assertEquals(0, r.updated());
        assertEquals(1, r.skipped());
    }
}
