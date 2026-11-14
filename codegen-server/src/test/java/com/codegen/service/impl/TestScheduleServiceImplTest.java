package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestRun;
import com.codegen.entity.TestSchedule;
import com.codegen.mapper.TestRunMapper;
import com.codegen.mapper.TestScheduleMapper;
import com.codegen.service.TestRunService;
import com.codegen.vo.TestVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-14 定时回归：调度核心（到期判定 / 行锁认领 / 触发 / 跳过已认领）通过 Mockito 单测，
 * 不依赖数据库——baseMapper / runService / runMapper 全部打桩。
 */
@ExtendWith(MockitoExtension.class)
class TestScheduleServiceImplTest {

    @Mock
    private TestScheduleMapper mapper;

    @Mock
    private TestRunService runService;

    @Mock
    private TestRunMapper runMapper;

    @InjectMocks
    private TestScheduleServiceImpl service;

    @BeforeEach
    void wireBaseMapper() {
        // ServiceImpl.baseMapper 由 MyBatis-Plus 注入；单测里手动补齐
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private TestSchedule schedule(Long id, Long envId, String suiteIds, String cron, Long lastRunId) {
        TestSchedule s = new TestSchedule();
        s.setId(id);
        s.setProjectId(1L);
        s.setName("t" + id);
        s.setEnvId(envId);
        s.setSuiteIds(suiteIds);
        s.setCron(cron);
        s.setEnabled(1);
        s.setLastRunId(lastRunId);
        return s;
    }

    @Test
    void scan_triggersDueScheduleAndRecordsRunId() {
        TestSchedule s = schedule(10L, 5L, "2,3", "* * * * * ?", null);
        when(mapper.selectList(any())).thenReturn(List.of(s));
        when(mapper.selectByIdForUpdate(10L)).thenReturn(s);
        when(mapper.updateById(any())).thenReturn(1);

        TestRun run = new TestRun();
        run.setId(99L);
        run.setStatus("success");
        when(runService.start(any())).thenReturn(new TestVO.RunDetail(run, List.of()));

        service.scanAndTrigger();

        ArgumentCaptor<TestCenterDTO.RunRequest> captor = ArgumentCaptor.forClass(TestCenterDTO.RunRequest.class);
        verify(runService).start(captor.capture());
        TestCenterDTO.RunRequest req = captor.getValue();
        assertEquals(5L, req.getEnvId());
        assertEquals(List.of(2L, 3L), req.getSuiteIds());
        assertEquals("schedule", req.getTriggerType());

        // markRun 把真实 runId 写回 lastRunId
        ArgumentCaptor<TestSchedule> upd = ArgumentCaptor.forClass(TestSchedule.class);
        verify(mapper, atLeastOnce()).updateById(upd.capture());
        assertEquals(99L, upd.getValue().getLastRunId());
    }

    @Test
    void scan_skipsAlreadyClaimed() {
        TestSchedule s = schedule(11L, 5L, "2", "* * * * * ?", -1L);
        when(mapper.selectList(any())).thenReturn(List.of(s));

        service.scanAndTrigger();

        verify(runService, never()).start(any());
    }

    @Test
    void scan_skipsWhenNoSuiteIds() {
        TestSchedule s = schedule(12L, 5L, "  ", "* * * * * ?", null);
        when(mapper.selectList(any())).thenReturn(List.of(s));

        service.scanAndTrigger();

        verify(runService, never()).start(any());
    }

    @Test
    void save_rejectsInvalidCron() {
        TestCenterDTO.ScheduleSave dto = new TestCenterDTO.ScheduleSave();
        dto.setName("x");
        dto.setEnvId(1L);
        dto.setCron("this is not a cron");
        dto.setSuiteIds("1");

        assertThrows(BusinessException.class, () -> service.save(dto));
    }

    @Test
    void save_rejectsMissingFields() {
        TestCenterDTO.ScheduleSave dto = new TestCenterDTO.ScheduleSave();
        dto.setCron("* * * * * ?");
        dto.setSuiteIds("1");
        // 缺 name 与 envId

        assertThrows(BusinessException.class, () -> service.save(dto));
    }

    @Test
    void remove_usesDeleteByIdForLogicDelete() {
        // deleted 字段带 @TableLogic：必须用 deleteById（触发 UPDATE SET deleted=1），
        // 不能靠 updateById(setDeleted(1)) —— 后者会被 MyBatis-Plus 忽略逻辑删除列，
        // 导致行「删不掉」。回归测试锁定此行为。
        when(mapper.deleteById(20L)).thenReturn(1);

        service.remove(20L);

        verify(mapper).deleteById(20L);
        verify(mapper, never()).updateById(any());
    }
}
