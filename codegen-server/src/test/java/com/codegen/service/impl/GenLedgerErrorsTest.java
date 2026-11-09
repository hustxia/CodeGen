package com.codegen.service.impl;

import com.codegen.entity.GenOutput;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenOutputMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 台账写失败不静默（V4.2）：insert 抛异常时文件已落盘，写盘不能因此失败，
 * 但失败条数必须通过 {@code ledgerErrorCount()} 露出去——否则接口返回成功、
 * 库里没账，按批次回滚就缺文件。
 */
@DisplayName("台账写失败：计数可见且不阻断写盘")
class GenLedgerErrorsTest {

    private GenCodeServiceImpl service;
    private GenOutputMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // 计数器是静态 ThreadLocal，生产契约是 writeToDisk 开头 set(0)——
        // 直调 recordOutput 没有经过会话边界，这里重置模拟新会话，避免用例间串数
        Field counter = GenCodeServiceImpl.class.getDeclaredField("LEDGER_ERRORS");
        counter.setAccessible(true);
        ((ThreadLocal<Integer>) counter.get(null)).set(0);
        service = new GenCodeServiceImpl();
        mapper = Mockito.mock(GenOutputMapper.class);
        inject("genOutputMapper", mapper);
    }

    @Test
    @DisplayName("insert 抛异常 → ledgerErrorCount 计 1，且不向调用方抛出")
    void insertFailureIsCountedNotThrown() throws Exception {
        when(mapper.insert(any(GenOutput.class)))
                .thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(this::invokeRecordOutput);
        assertEquals(1, service.ledgerErrorCount());
    }

    @Test
    @DisplayName("insert 正常 → 计数保持 0")
    void insertSuccessKeepsZero() throws Exception {
        when(mapper.insert(any(GenOutput.class))).thenReturn(1);

        invokeRecordOutput();
        assertEquals(0, service.ledgerErrorCount());
    }

    private void invokeRecordOutput() throws Exception {
        Method m = GenCodeServiceImpl.class.getDeclaredMethod("recordOutput",
                String.class, GenTable.class, String.class, String.class,
                String.class, String.class, long.class, String.class,
                String.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, "GEN20261023000000", new GenTable(), "BACKEND",
                "com/demo/DemoController.java", "D:/tmp/com/demo/DemoController.java",
                "CREATE", 10L, "d41d8cd98f00b204e9800998ecf8427e", "",
                null, null);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = GenCodeServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}
