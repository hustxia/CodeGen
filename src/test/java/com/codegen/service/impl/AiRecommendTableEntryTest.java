package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenAiRecommendApplyDTO;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.vo.GenAiRecommendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V7.3 P0-3 表级 AI 推荐入口的契约（不依赖 Spring / 数据库 / LLM）：
 *
 * <ul>
 *   <li>token 是唯一入口凭据——格式非法一律拒，且不能拼出目录外路径；</li>
 *   <li>产物缺失报「已过期」而不是 500；</li>
 *   <li>同一系统产物只留最近 N 份，目录不会无限膨胀；</li>
 *   <li>应用只写勾选项（AC-3 的延伸：不勾 = 零 UPDATE）。</li>
 * </ul>
 */
class AiRecommendTableEntryTest {

    @TempDir
    Path tempDir;

    private AiRecommendServiceImpl service() {
        AiRecommendServiceImpl svc = new AiRecommendServiceImpl();
        GenPipelineProperties props = new GenPipelineProperties();
        props.setLogDir(tempDir.toString());
        ReflectionTestUtils.setField(svc, "properties", props);
        return svc;
    }

    @Test
    @DisplayName("token 格式非法（含路径穿越）直接拒，不落到文件系统")
    void rejectsIllegalToken() {
        AiRecommendServiceImpl svc = service();
        GenAiRecommendApplyDTO dto = new GenAiRecommendApplyDTO();
        for (String bad : new String[]{"../../etc/passwd", "AIT1-20260101000000-zzzzzzzz",
                "AIT1-2026-abcdef12", "", null}) {
            assertThrows(BusinessException.class, () -> svc.applyTables(bad, dto),
                    "应拒绝非法 token: " + bad);
        }
    }

    @Test
    @DisplayName("产物不存在 → 报过期，提示重新推荐")
    void reportsExpiredWhenArtifactMissing() {
        AiRecommendServiceImpl svc = service();
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.applyTables("AIT7-20260101000000-abcdef12", new GenAiRecommendApplyDTO()));
        assertTrue(e.getMessage().contains("过期"), "提示应包含「过期」: " + e.getMessage());
    }

    @Test
    @DisplayName("同一系统产物只保留最近 5 份")
    void prunesOldArtifacts() throws Exception {
        AiRecommendServiceImpl svc = service();
        for (int i = 0; i < 8; i++) {
            svc.writeTableArtifact(9L, new GenAiRecommendVO());
        }
        Path dir = tempDir.resolve("ai-recommend");
        try (var stream = Files.list(dir)) {
            assertEquals(5, stream.filter(p -> p.getFileName().toString().startsWith("AIT9-")).count(),
                    "应只保留 5 份产物");
        }
        // 另一个系统的产物不受影响
        String other = svc.writeTableArtifact(10L, new GenAiRecommendVO());
        assertTrue(Files.exists(dir.resolve(other + ".json")));
    }

    @Test
    @DisplayName("应用只写勾选项：不勾 = 零 UPDATE")
    void appliesOnlyPickedFields() {
        AiRecommendServiceImpl svc = service();
        GenPipelineProperties props = (GenPipelineProperties) ReflectionTestUtils.getField(svc, "properties");

        GenTableMapper tableMapper = mock(GenTableMapper.class);
        GenTable table = new GenTable();
        table.setId(1L);
        table.setTableName("t_order");
        table.setModuleName("old");
        when(tableMapper.selectList(any())).thenReturn(List.of(table));
        ReflectionTestUtils.setField(svc, "tableMapper", tableMapper);
        ReflectionTestUtils.setField(svc, "columnMapper", mock(GenTableColumnMapper.class));

        // 服务端产物：AI 建议 moduleName=goods
        GenAiRecommendVO suggest = new GenAiRecommendVO();
        GenAiRecommendVO.TableSuggest ts = new GenAiRecommendVO.TableSuggest();
        ts.setTableName("t_order");
        ts.setModuleName("goods");
        suggest.getTables().add(ts);
        String token = svc.writeTableArtifact(1L, suggest);

        // 只勾列级（产物里没有列建议）→ 表级一个字段都不写
        GenAiRecommendApplyDTO emptyPick = new GenAiRecommendApplyDTO();
        emptyPick.setToken(token);
        GenAiRecommendApplyDTO.TablePick pick = new GenAiRecommendApplyDTO.TablePick();
        pick.setTableName("t_order");
        emptyPick.getTables().add(pick);
        assertEquals(0, svc.applyTables(token, emptyPick));
        verify(tableMapper, never()).updateById(any());

        // 勾上 moduleName → 写一次
        pick.setFields(List.of("moduleName"));
        assertEquals(1, svc.applyTables(token, emptyPick));
        verify(tableMapper).updateById(table);
        assertEquals("goods", table.getModuleName(), "表级建议应被写入");
    }
}
