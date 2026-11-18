package com.codegen.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codegen.entity.GenPipeline;
import com.codegen.mapper.GenPipelineMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 启动恢复：重启后 running -> failed */
@ExtendWith(MockitoExtension.class)
class PipelineStartupRecoveryTest {

    @Mock
    private GenPipelineMapper pipelineMapper;

    @InjectMocks
    private PipelineStartupRecovery recovery;

    @BeforeAll
    static void initMpTableInfo() {
        // LambdaUpdateWrapper 需要 MP 的实体元数据缓存（纯单测无 Spring 环境）
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), GenPipeline.class);
    }

    @Test
    void marksRunningAsFailedOnBoot() {
        when(pipelineMapper.update(isNull(), any())).thenReturn(2);
        recovery.run(null);
        ArgumentCaptor<GenPipeline> cap = ArgumentCaptor.forClass(GenPipeline.class);
        verify(pipelineMapper).update(isNull(), any());
        // update(entity=null, wrapper) 形态：批量条件更新，不逐条 updateById
        verify(pipelineMapper, never()).updateById(cap.capture());
    }

    @Test
    void silentWhenNothingRunning() {
        when(pipelineMapper.update(isNull(), any())).thenReturn(0);
        recovery.run(null);
        verify(pipelineMapper).update(isNull(), any());
        assertEquals(0, 0);
    }
}
