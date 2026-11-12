package com.codegen.controller;

import com.codegen.common.context.UserContext;
import com.codegen.config.MonitorProperties;
import com.codegen.monitor.MonitorStore;
import com.codegen.monitor.MonitorWindow;
import com.codegen.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6.10 监控接口层：在 HTTP 层次验证 AC-5 的两条硬断言——
 * 非法 metric → 400、缺权限 → 403、minutes 越界被钳到 30。
 *
 * <p>用 standalone MockMvc 直接吃控制器的 @ExceptionHandler，不拉 Spring 上下文，
 * 因此本测试秒级返回，可纳入 mvn test 常规门禁。
 */
@ExtendWith(MockitoExtension.class)
class MonitorControllerTest {

    @Mock MonitorStore store;
    @Mock MonitorWindow window;
    @Mock MonitorProperties props;
    @Mock PermissionService permissionService;
    @InjectMocks MonitorController controller;

    private final Long UID = 2L;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(UID);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        lenient().when(permissionService.isSuperAdmin(UID)).thenReturn(true);
        lenient().when(props.getSample()).thenReturn(new MonitorProperties().getSample());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void invalidMetricReturns400() throws Exception {
        mvc.perform(get("/monitor/series").param("metric", "evil"))
                .andExpect(status().is(400));
    }

    @Test
    void missingMetricReturns400() throws Exception {
        mvc.perform(get("/monitor/series"))
                .andExpect(status().is(400));
    }

    @Test
    void deniedWithoutViewPermissionReturns403() throws Exception {
        when(permissionService.isSuperAdmin(UID)).thenReturn(false);
        when(permissionService.permissionsOf(UID)).thenReturn(Collections.emptySet());
        mvc.perform(get("/monitor/overview")).andExpect(status().is(403));
    }

    @Test
    void overviewReturns200ForSuperAdmin() throws Exception {
        when(store.latest()).thenReturn(null);
        when(store.capacity()).thenReturn(360);
        when(store.size()).thenReturn(0);
        when(store.lastSampleAt()).thenReturn(0L);
        mvc.perform(get("/monitor/overview")).andExpect(status().isOk());
    }

    @Test
    void seriesClampsMinutesTo30() throws Exception {
        when(window.of(anyInt())).thenReturn(Collections.emptyList());
        when(window.intervalSeconds()).thenReturn(5.0);
        when(window.warmup(anyList(), anyInt())).thenReturn(true);
        mvc.perform(get("/monitor/series").param("metric", "jvm.heap").param("minutes", "999"))
                .andExpect(status().isOk());
        verify(window).of(30);
        verify(window, never()).of(999);
    }

    @Test
    void businessAndHttpReturn200ForSuperAdmin() throws Exception {
        when(window.of(anyInt())).thenReturn(Collections.emptyList());
        when(window.intervalSeconds()).thenReturn(5.0);
        when(window.warmup(anyList(), anyInt())).thenReturn(true);
        mvc.perform(get("/monitor/business")).andExpect(status().isOk());
        mvc.perform(get("/monitor/http")).andExpect(status().isOk());
    }
}
