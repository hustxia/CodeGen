package com.codegen.service.impl;

import com.codegen.entity.SysProject;
import com.codegen.mapper.GenPipelineMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.service.GenSqlDeliverService.SqlArtifact;
import com.codegen.support.GenSeedGenerator;
import com.codegen.vo.GenSqlDeliverVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V7.3 P0-2：SQL 交付物统一出口的落盘与备份语义（不依赖 Spring/数据库）。
 */
class GenSqlDeliverServiceTest {

    @TempDir
    Path tempDir;

    private GenSqlDeliverServiceImpl service(SysProject project) {
        SysProjectMapper projectMapper = mock(SysProjectMapper.class);
        when(projectMapper.selectById(anyLong())).thenReturn(project);
        return new GenSqlDeliverServiceImpl(projectMapper, mock(GenTableMapper.class),
                mock(GenPipelineMapper.class), null, mock(GenSeedGenerator.class), null);
    }

    private SysProject project() {
        SysProject project = new SysProject();
        project.setId(1L);
        project.setBackendRoot(tempDir.toString());
        project.setProjectCode("wms");
        return project;
    }

    @Test
    @DisplayName("统一落点：三个交付物写入 <code>-server/db/，空内容只跳过不报错")
    void deliverToProjectDbDir() throws Exception {
        GenSqlDeliverVO vo = service(project()).deliver(1L, "GEN20260101000000", List.of(
                new SqlArtifact("init_tables.sql", "CREATE TABLE t1 (id bigint);"),
                new SqlArtifact("init_seed.sql", "INSERT INTO t1 (id) VALUES (1);"),
                new SqlArtifact("init_menu.sql", "")));

        Path dbDir = tempDir.resolve("wms-server/db");
        assertTrue(Files.exists(dbDir.resolve("init_tables.sql")));
        assertTrue(Files.exists(dbDir.resolve("init_seed.sql")));
        assertFalse(Files.exists(dbDir.resolve("init_menu.sql")), "空内容不应产出文件");
        assertEquals("SKIPPED", vo.getItems().get(2).getStatus());
        assertEquals(2, vo.getWritten());
        assertEquals(0, vo.getErrors());
        assertEquals(dbDir.toAbsolutePath().normalize().toString(), vo.getDbDir());
    }

    @Test
    @DisplayName("覆盖前备份到 .gen-backup/<batchNo>/，内容可回滚")
    void backupBeforeOverwrite() throws Exception {
        GenSqlDeliverServiceImpl service = service(project());
        service.deliver(1L, "GEN_OLD", List.of(new SqlArtifact("init_tables.sql", "OLD")));
        service.deliver(1L, "GEN_NEW", List.of(new SqlArtifact("init_tables.sql", "NEW")));

        Path target = tempDir.resolve("wms-server/db/init_tables.sql");
        assertEquals("NEW", Files.readString(target, StandardCharsets.UTF_8));
        Path backup = tempDir.resolve(".gen-backup/GEN_NEW/wms-server/db/init_tables.sql");
        assertEquals("OLD", Files.readString(backup, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("批次号非法字符归一化，避免路径穿越")
    void sanitizeBatchNo() {
        assertEquals("___a_b", GenSqlDeliverServiceImpl.safeBatchNo("../a/b"));
    }

    @Test
    @DisplayName("系统缺失时返回错误项而不抛出")
    void missingProject() {
        GenSqlDeliverVO vo = service(null).deliver(1L, "GEN1",
                List.of(new SqlArtifact("init_tables.sql", "X")));
        assertEquals(1, vo.getErrors());
        assertEquals("ERROR", vo.getItems().get(0).getStatus());
    }
}
