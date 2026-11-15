package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.codegen.config.AiBuildJobProperties;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenAiBuildPlanDTO;
import com.codegen.entity.GenAiBuildJob;
import com.codegen.entity.GenAiBuildJobBatch;
import com.codegen.mapper.GenAiBuildJobBatchMapper;
import com.codegen.mapper.GenAiBuildJobMapper;
import com.codegen.service.AiModelService;
import com.codegen.vo.GenAiBuildJobVO;
import com.codegen.vo.GenAiBuildPlanVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V9.6 分批建模任务：任务落库、超时回收、收口汇总。
 *
 * <p>不启 Spring：调度与并发在 {@code AiBuildJobScheduler} 里，这里只验状态机与汇总口径，
 * 避免测试依赖数据库和真实线程池时序。
 */
@ExtendWith(MockitoExtension.class)
class AiBuildJobServiceImplTest {

    @Mock
    private AiModelService aiModelService;
    @Mock
    private AiTableRecommendServiceImpl recommendService;
    @Mock
    private GenAiBuildJobMapper jobMapper;
    @Mock
    private GenAiBuildJobBatchMapper batchMapper;

    private AiBuildJobServiceImpl service;

    @BeforeEach
    void setUp() {
        AiBuildJobProperties props = new AiBuildJobProperties();
        props.setWorkerThreads(1);
        props.setMaxRetry(2);
        props.setBatchTimeoutSeconds(600);

        GenPipelineProperties pipeline = new GenPipelineProperties();
        pipeline.getAi().setEnabled(true);

        service = new AiBuildJobServiceImpl();
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);
        ReflectionTestUtils.setField(service, "recommendService", recommendService);
        ReflectionTestUtils.setField(service, "jobMapper", jobMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "props", props);
        ReflectionTestUtils.setField(service, "pipelineProps", pipeline);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    private GenAiBuildPlanDTO dto(String scope) {
        GenAiBuildPlanDTO d = new GenAiBuildPlanDTO();
        d.setSystemName("电商系统");
        d.setSystemDesc("商品、订单、库存");
        d.setScope(scope);
        d.setScale("huge");
        d.setDeploy("micro");
        return d;
    }

    @Test
    @DisplayName("提交：落一个任务 + 一个拆模块批次，补表已有表写进 payload")
    void submitCreatesJobAndSplitBatch() {
        when(recommendService.loadExistingTableNames(anyLong())).thenReturn(Set.of("goods"));

        GenAiBuildPlanDTO d = dto("supplement");
        d.setProjectId(9L);
        GenAiBuildJob[] saved = new GenAiBuildJob[1];
        when(jobMapper.insert(any(GenAiBuildJob.class))).thenAnswer(inv -> {
            GenAiBuildJob j = inv.getArgument(0);
            j.setId(100L);
            saved[0] = j;
            return 1;
        });
        when(batchMapper.insert(any(GenAiBuildJobBatch.class))).thenReturn(1);
        when(jobMapper.selectById(100L)).thenAnswer(inv -> saved[0]);
        when(batchMapper.selectList(any())).thenReturn(List.of());

        GenAiBuildJobVO vo = service.submitPlan(d);

        assertEquals(100L, vo.getJobId());
        assertEquals(1, vo.getTotalBatch(), "初始只有拆模块这一批");
        assertTrue(saved[0].getPayload().contains("goods"), "已有表要固化进 payload，重试时输入一致");
        assertEquals("huge", saved[0].getScale());
        verify(batchMapper).insert(any(GenAiBuildJobBatch.class));
    }

    @Test
    @DisplayName("超时回收：未超重试上限→回 pending 且计数+1；超限→判 failed")
    void resetStuckResetsOrFails() {
        GenAiBuildJobBatch b1 = new GenAiBuildJobBatch();
        b1.setId(1L);
        b1.setJobId(10L);
        b1.setStatus("running");
        b1.setRetryCount(0);
        b1.setStartedAt(LocalDateTime.now().minusHours(1));

        GenAiBuildJobBatch b2 = new GenAiBuildJobBatch();
        b2.setId(2L);
        b2.setJobId(10L);
        b2.setStatus("running");
        b2.setRetryCount(2); // 已达 maxRetry=2
        b2.setStartedAt(LocalDateTime.now().minusHours(1));

        when(batchMapper.selectList(any())).thenReturn(List.of(b1, b2));
        // 收口判定在 resetStuck 内部按批次状态触发，这里只保证「不收口」，允许未被调用
        lenient().when(jobMapper.selectCount(any())).thenReturn(1L);

        service.resetStuck();

        List<String> sqlSets = new ArrayList<>();
        ArgumentCaptor<UpdateWrapper<GenAiBuildJobBatch>> cap =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(batchMapper, atLeastOnce()).update(any(), cap.capture());
        for (UpdateWrapper<GenAiBuildJobBatch> w : cap.getAllValues()) {
            sqlSets.add(String.valueOf(w.getSqlSet()));
        }
        assertTrue(sqlSets.stream().anyMatch(s -> s.contains("retry_count")), "第一次超时应重置重试计数");
        assertTrue(sqlSets.stream().anyMatch(s -> s.contains("failed")), "超过上限应判失败");
    }

    @Test
    @DisplayName("收口：模块批次合并成计划，有失败批次则 partial")
    void finalizeAggregatesBatches() {
        GenAiBuildJob job = new GenAiBuildJob();
        job.setId(10L);
        job.setStatus("running");
        job.setPayload("{\"dto\":{\"systemName\":\"电商系统\",\"scale\":\"huge\",\"deploy\":\"micro\"},"
                + "\"existing\":[],\"descForAi\":\"商品、订单\"}");

        GenAiBuildJobBatch b1 = new GenAiBuildJobBatch();
        b1.setJobId(10L);
        b1.setSeq(1);
        b1.setKind("module");
        b1.setModuleName("商品");
        b1.setStatus("success");
        b1.setTables("[{\"tableName\":\"goods\",\"columns\":[{\"name\":\"id\",\"type\":\"bigint\",\"pk\":true}]}]");

        GenAiBuildJobBatch b2 = new GenAiBuildJobBatch();
        b2.setJobId(10L);
        b2.setSeq(2);
        b2.setKind("module");
        b2.setModuleName("订单");
        b2.setStatus("failed");
        b2.setError("timeout");

        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(jobMapper.selectById(10L)).thenReturn(job);
        when(batchMapper.selectList(any())).thenReturn(List.of(b1, b2));

        GenAiBuildPlanVO plan = new GenAiBuildPlanVO();
        plan.setTableCount(1);
        plan.setModuleCount(1);
        when(recommendService.assemblePlan(any(), any(), any(), any(), any(), any())).thenReturn(plan);

        service.finalizeIfDone(10L);

        ArgumentCaptor<GenAiBuildJob> cap = ArgumentCaptor.forClass(GenAiBuildJob.class);
        verify(jobMapper).updateById(cap.capture());
        assertEquals("partial", cap.getValue().getStatus(), "有失败批次但出表了 → partial");
        assertNotNull(cap.getValue().getResult());
    }

    @Test
    @DisplayName("收口：还有 pending/running 时不动，避免提前宣告完成")
    void finalizeSkipsWhenBatchesOpen() {
        when(batchMapper.selectCount(any())).thenReturn(2L);

        service.finalizeIfDone(10L);

        verify(jobMapper, never()).updateById(any(GenAiBuildJob.class));
    }
}
