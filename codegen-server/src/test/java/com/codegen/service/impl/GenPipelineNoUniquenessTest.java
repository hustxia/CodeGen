package com.codegen.service.impl;

import com.codegen.entity.GenPipeline;
import com.codegen.mapper.GenPipelineMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流水线号唯一性（V9.6）：秒级时间戳 + 序号 + 撞 uk_no 换号重试。
 *
 * <p>回归的是线上真实报错：
 * {@code Duplicate entry 'PPL20261115172953' for key 'gen_pipeline.uk_no'}，
 * 微服务形态在一个请求内逐模块建库，同秒必撞。
 */
@ExtendWith(MockitoExtension.class)
class GenPipelineNoUniquenessTest {

    @Mock
    private GenPipelineMapper pipelineMapper;
    @InjectMocks
    private GenPipelineServiceImpl service;

    @Test
    @DisplayName("同秒连发 5000 次：编号全不重复且长度/格式固定")
    void noIsUniqueWithinSameSecond() throws Exception {
        int n = 5000;
        Set<String> nos = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                tasks.add(GenPipelineServiceImpl::nextPipelineNo);
            }
            for (Future<String> f : pool.invokeAll(tasks)) {
                String no = f.get();
                assertEquals(21, no.length(), "PPL + 14 位时间戳 + 4 位序号");
                assertTrue(no.matches("^PPL\\d{18}$"), "格式不符：" + no);
                nos.add(no);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(n, nos.size(), "并发下出现重复流水线号");
    }

    @Test
    @DisplayName("撞 uk_no：换号重试并清掉上次回填的 id，最终插入成功")
    void retryWithNewNoOnUkNoConflict() {
        GenPipeline p = new GenPipeline();
        p.setDbName("mall_m3");
        List<String> attemptedNos = new ArrayList<>();
        int[] calls = {0};
        when(pipelineMapper.insert(any(GenPipeline.class))).thenAnswer(inv -> {
            GenPipeline arg = inv.getArgument(0);
            calls[0]++;
            attemptedNos.add(arg.getPipelineNo());
            if (calls[0] == 1) {
                arg.setId(1234567890L); // 模拟 MyBatis-Plus 已回填主键
                throw new DuplicateKeyException(
                        "Duplicate entry '" + arg.getPipelineNo() + "' for key 'gen_pipeline.uk_no'");
            }
            assertNull(arg.getId(), "重试前必须清掉上一次回填的 id，否则会撞主键");
            return 1;
        });

        service.insertWithUniqueNo(p);

        verify(pipelineMapper, times(2)).insert(any(GenPipeline.class));
        assertEquals(2, attemptedNos.size());
        assertNotEquals(attemptedNos.get(0), attemptedNos.get(1), "重试必须换号");
        assertTrue(p.getPipelineNo().matches("^PPL\\d{18}$"));
    }

    @Test
    @DisplayName("冲突不是 uk_no（如同名库并发）：直接抛，不靠换号掩盖")
    void otherConflictIsPropagated() {
        GenPipeline p = new GenPipeline();
        p.setDbName("mall_m3");
        DuplicateKeyException other = new DuplicateKeyException(
                "Duplicate entry 'mall_m3' for key 'gen_pipeline.uk_db_active'");
        when(pipelineMapper.insert(any(GenPipeline.class))).thenThrow(other);

        DuplicateKeyException thrown =
                assertThrows(DuplicateKeyException.class, () -> service.insertWithUniqueNo(p));
        assertSame(other, thrown);
        verify(pipelineMapper, times(1)).insert(any(GenPipeline.class));
    }

    @Test
    @DisplayName("连续两次生成的编号不同（微服务逐模块建库的最小场景）")
    void consecutiveNosDiffer() {
        String a = GenPipelineServiceImpl.nextPipelineNo();
        String b = GenPipelineServiceImpl.nextPipelineNo();
        assertNotEquals(a, b);
    }
}
