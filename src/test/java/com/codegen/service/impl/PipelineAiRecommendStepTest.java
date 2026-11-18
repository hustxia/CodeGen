package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.config.GenPipelineProperties;
import com.codegen.entity.GenPipeline;
import com.codegen.service.AiRecommendService;
import com.codegen.vo.GenAiRecommendPreviewVO;
import com.codegen.vo.GenAiRecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.0 pipeline ai_recommend 步骤的降级契约（spec AC-1 / AC-5）：
 *
 * <ul>
 *   <li>ai.enabled=false → 步骤标记 skipped，产物行为与 V6.8 一致；</li>
 *   <li>AI 超时/失败 → 步骤标记 failed-degraded，pipeline 用规则默认继续，不阻断；</li>
 *   <li>成功 → 建议产物落 ai_recommend.json（不落 gen_table），步骤返回可读摘要。</li>
 * </ul>
 */
class PipelineAiRecommendStepTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper json = new ObjectMapper();

    private GenPipelineProperties props(boolean aiEnabled) {
        GenPipelineProperties props = new GenPipelineProperties();
        props.setLogDir(tempDir.toString());
        props.getAi().setEnabled(aiEnabled);
        props.getAi().setBaseUrl("http://localhost:11434/v1");
        props.getAi().setApiKey("test");
        props.getAi().setModel("qwen2.5:7b");
        return props;
    }

    private PipelineExecutor executor(GenPipelineProperties props, AiRecommendService svc) {
        // 步骤只依赖 props 与 aiRecommendService，其余协作者本用例不触达（第 17 位 aiRecommendService，第 18 位 aiModelService）
        return new PipelineExecutor(null, null, null, null, null, null, null, props,
                null, null, null, null, null, null, null, null, svc, null, null, null);
    }

    /**
     * AiRecommendService 有五个方法、不是函数式接口，lambda 赋值编译不过；
     * 本用例只触达 recommend，其余方法抛 AssertionError 防误调。
     */
    private AiRecommendService recommendStub(java.util.function.Function<Long, GenAiRecommendVO> fn) {
        return new AiRecommendService() {
            @Override
            public GenAiRecommendVO recommend(Long projectId) {
                return fn.apply(projectId);
            }

            @Override
            public GenAiRecommendPreviewVO preview(Long pipelineId) {
                throw new AssertionError("步骤执行不应调用 preview");
            }

            @Override
            public int apply(Long pipelineId, com.codegen.dto.GenAiRecommendApplyDTO dto) {
                throw new AssertionError("步骤执行不应调用 apply");
            }

            @Override
            public GenAiRecommendPreviewVO recommendTables(com.codegen.dto.GenAiRecommendTableDTO dto) {
                throw new AssertionError("步骤执行不应调用表级 recommendTables");
            }

            @Override
            public int applyTables(String token, com.codegen.dto.GenAiRecommendApplyDTO dto) {
                throw new AssertionError("步骤执行不应调用表级 applyTables");
            }
        };
    }

    private GenPipeline pipeline() {
        GenPipeline p = new GenPipeline();
        p.setId(1L);
        p.setProjectId(9L);
        p.setPipelineNo("P20261104001");
        return p;
    }

    @Test
    @DisplayName("AC-1：AI 未启用 → 抛出 skipped 降级信号，不调 AI、不写产物")
    void aiDisabledMarkedSkipped() {
        AiRecommendService svc = recommendStub(projectId -> {
            throw new AssertionError("AI 未启用时不应发起推荐调用");
        });
        PipelineExecutor executor = executor(props(false), svc);

        PipelineExecutor.AiRecommendDegradedException e = assertThrows(
                PipelineExecutor.AiRecommendDegradedException.class,
                () -> executor.stepAiRecommend(pipeline()));
        assertEquals("skipped", e.getStatus());
        assertTrue(e.getMessage().contains("规则"));
    }

    @Test
    @DisplayName("AC-5：AI 调用失败 → 抛出 failed-degraded 降级信号（pipeline 继续）")
    void aiFailureMarkedFailedDegraded() {
        AiRecommendService svc = recommendStub(projectId -> {
            throw new BusinessException(500, "AI 请求失败（超时或网络异常）");
        });
        PipelineExecutor executor = executor(props(true), svc);

        PipelineExecutor.AiRecommendDegradedException e = assertThrows(
                PipelineExecutor.AiRecommendDegradedException.class,
                () -> executor.stepAiRecommend(pipeline()));
        assertEquals("failed-degraded", e.getStatus());
        assertTrue(e.getMessage().contains("降级"));
    }

    @Test
    @DisplayName("成功：建议产物落 ai_recommend.json，步骤摘要含建议量与丢弃量")
    void aiSuccessWritesArtifact() throws Exception {
        GenAiRecommendVO vo = new GenAiRecommendVO();
        GenAiRecommendVO.TableSuggest ts = new GenAiRecommendVO.TableSuggest();
        ts.setTableName("wms_stock");
        GenAiRecommendVO.ColumnSuggest c1 = new GenAiRecommendVO.ColumnSuggest();
        c1.setColumnName("status");
        c1.setIsQuery(1);
        GenAiRecommendVO.ColumnSuggest c2 = new GenAiRecommendVO.ColumnSuggest();
        c2.setColumnName("name");
        c2.setIsList(1);
        ts.getColumns().add(c1);
        ts.getColumns().add(c2);
        vo.getTables().add(ts);
        GenAiRecommendVO.MenuSuggest ms = new GenAiRecommendVO.MenuSuggest();
        ms.setModuleName("stock");
        vo.getMenus().add(ms);
        vo.getDrops().add("表 wms_stock 列 name：dictType「x」不在平台字典中");

        AiRecommendService svc = recommendStub(projectId -> vo);
        PipelineExecutor executor = executor(props(true), svc);

        String message = executor.stepAiRecommend(pipeline());

        assertTrue(message.contains("2"), "摘要含列级建议量：" + message);
        assertTrue(message.contains("丢弃"), "摘要含丢弃量：" + message);
        Path artifact = tempDir.resolve("P20261104001").resolve("ai_recommend.json");
        assertTrue(Files.exists(artifact), "产物文件要落盘：" + artifact);
        GenAiRecommendVO persisted = json.readValue(Files.readString(artifact), GenAiRecommendVO.class);
        assertEquals(1, persisted.getTables().size());
        assertEquals(2, persisted.getTables().get(0).getColumns().size());
        assertEquals(1, persisted.getMenus().size());
        assertEquals(1, persisted.getDrops().size());
    }
}
