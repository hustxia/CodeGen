package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.dto.GenPipelineDdlPreviewDTO;
import com.codegen.dto.GenPipelinePrepareDTO;
import com.codegen.entity.GenPipeline;
import com.codegen.mapper.GenPipelineMapper;
import com.codegen.service.GenDdlService;
import com.codegen.service.SysProjectService;
import com.codegen.vo.GenDdlExecuteVO;
import com.codegen.vo.GenPipelineAiVO;
import com.codegen.vo.GenPipelineDdlPreviewVO;
import com.codegen.vo.GenPipelinePrepareVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenPipelineDdlPreviewTest {

    @Mock
    private GenPipelineMapper pipelineMapper;
    @Mock
    private GenDdlService ddlService;
    @Mock
    private SysProjectService projectService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private GenPipelineProperties props;
    @Mock
    private GenOutputProperties outputProps;

    @InjectMocks
    private GenPipelineServiceImpl service;

    private GenPipelineDdlPreviewDTO sampleDto() {
        GenPipelineAiVO.Column column = new GenPipelineAiVO.Column();
        column.setName("asset_name");
        column.setType("varchar(128)");
        column.setComment("资产名称");
        GenPipelineAiVO.TableModel table = new GenPipelineAiVO.TableModel();
        table.setTableName("asset_info");
        table.setTableComment("资产档案");
        table.setColumns(List.of(column));
        GenPipelineDdlPreviewDTO dto = new GenPipelineDdlPreviewDTO();
        dto.setDbName("asset_demo");
        dto.setTables(List.of(table));
        return dto;
    }

    @Test
    void previewUsesBuildDdlWithoutTouchingDatabaseOrPipeline() {
        GenPipelineDdlPreviewDTO dto = sampleDto();

        GenPipelineDdlPreviewVO preview = service.previewDdl(dto);

        assertEquals("asset_demo", preview.getDbName());
        assertEquals(1, preview.getTableCount());
        assertEquals(2, preview.getStatementCount());
        assertEquals(service.buildDdl(dto.getDbName(), dto.getTables()), preview.getDdl());
        assertTrue(preview.getDdl().contains("CREATE DATABASE IF NOT EXISTS `asset_demo`"));
        assertTrue(preview.getDdl().contains("CREATE TABLE IF NOT EXISTS `asset_info`"));
        assertTrue(preview.getDdl().contains("`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'"));
        assertTrue(preview.getDdl().contains("`deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除'"));
        verifyNoInteractions(pipelineMapper, ddlService, projectService, jdbcTemplate);
    }

    @Test
    void previewRejectsInvalidDatabaseName() {
        GenPipelineDdlPreviewDTO dto = sampleDto();
        dto.setDbName("Asset-Demo");

        assertThrows(BusinessException.class, () -> service.previewDdl(dto));
        verifyNoInteractions(pipelineMapper, ddlService, projectService, jdbcTemplate);
    }

    @Test
    void previewRejectsEmptyTables() {
        GenPipelineDdlPreviewDTO dto = sampleDto();
        dto.setTables(List.of());

        assertThrows(BusinessException.class, () -> service.previewDdl(dto));
        verifyNoInteractions(pipelineMapper, ddlService, projectService, jdbcTemplate);
    }

    @Test
    void previewRejectsIllegalTableNameThroughSharedAssembler() {
        GenPipelineDdlPreviewDTO dto = sampleDto();
        dto.getTables().get(0).setTableName("Asset-Info;DROP TABLE x");

        assertThrows(BusinessException.class, () -> service.previewDdl(dto));
        verifyNoInteractions(pipelineMapper, ddlService, projectService, jdbcTemplate);
    }

    @Test
    void dtoRequiresDatabaseNameAndTables() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        GenPipelineDdlPreviewDTO dto = new GenPipelineDdlPreviewDTO();

        assertEquals(2, validator.validate(dto).size());
    }

    /**
     * AC-04 同源强证据：预览返回的 SQL 与 prepareDb 交给 DDL 执行器的 SQL 逐字一致。
     * 两侧都走 buildDdl，禁止前端或预览接口另拼一份执行 SQL。
     */
    @Test
    void prepareDbExecutesByteIdenticalDdl(@TempDir Path tempDir) {
        GenPipelineDdlPreviewDTO previewDto = sampleDto();
        GenPipelineDdlPreviewVO preview = service.previewDdl(previewDto);

        GenPipeline pipeline = new GenPipeline();
        pipeline.setId(1L);
        pipeline.setPipelineNo("PPL202611140001");
        pipeline.setSystemName("设备资产管理系统");
        pipeline.setDbName("asset_demo");
        pipeline.setMode("ai");
        pipeline.setStatus("draft");
        when(pipelineMapper.selectById(anyLong())).thenReturn(pipeline);
        when(props.getLogDir()).thenReturn(tempDir.toString());
        GenDdlExecuteVO executeResult = new GenDdlExecuteVO();
        executeResult.setSchema("asset_demo");
        when(ddlService.execute(any(GenDdlExecuteDTO.class))).thenReturn(executeResult);

        GenPipelinePrepareDTO prepareDto = new GenPipelinePrepareDTO();
        prepareDto.setTables(previewDto.getTables());
        GenPipelinePrepareVO prepared = service.prepareDb(1L, prepareDto);

        ArgumentCaptor<GenDdlExecuteDTO> captor = ArgumentCaptor.forClass(GenDdlExecuteDTO.class);
        verify(ddlService).execute(captor.capture());
        assertEquals(preview.getDdl(), captor.getValue().getDdl());
        assertEquals("asset_demo", captor.getValue().getSchema());
        assertEquals(Boolean.FALSE, captor.getValue().getAllowDestructive());
        assertEquals("asset_demo", prepared.getDbName());
    }
}
