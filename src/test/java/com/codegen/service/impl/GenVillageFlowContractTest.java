package com.codegen.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智慧村务端到端跑通暴露的两个生成器缺陷的防回归契约：
 * 1) 流程表 sys_flow_def / sys_flow_task 的 id 必须自增，否则运行时 insert 报
 *    "Field 'id' doesn't have a default value"（submit 直接 500）；
 * 2) 鉴权过滤器必须把 user id 注入 UserContext，否则流程角色校验 userId=null 恒 403、
 *    审计 create_by 恒空。
 *
 * <p>三处修复分别位于 base.sql（DB 初始化）、ScaffoldFlowWriter（流程表 DDL）、
 * ScaffoldAuthWriter（过滤器模板字符串）。这里锁定源码文本，防止后续分支回退。
 */
@DisplayName("村务流程表与鉴权上下文契约")
class GenVillageFlowContractTest {

    private String src(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("base.sql：流程表 id 自增")
    void baseSqlFlowTablesAutoIncrement() throws Exception {
        String sql = src("src/main/resources/scaffold-test/base.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_flow_def (\n  id bigint NOT NULL AUTO_INCREMENT"),
                "sys_flow_def.id 必须 AUTO_INCREMENT");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_flow_task (\n  id bigint NOT NULL AUTO_INCREMENT"),
                "sys_flow_task.id 必须 AUTO_INCREMENT");
    }

    @Test
    @DisplayName("ScaffoldFlowWriter：流程表 DDL id 自增")
    void scaffoldFlowWriterAutoIncrement() throws Exception {
        String java = src("src/main/java/com/codegen/service/impl/ScaffoldFlowWriter.java");
        int n = java.split("`id` bigint NOT NULL AUTO_INCREMENT", -1).length - 1;
        assertTrue(n >= 2, "ScaffoldFlowWriter 中至少两处流程表 id 自增，实际：" + n);
    }

    @Test
    @DisplayName("ScaffoldAuthWriter：过滤器注入 user id")
    void scaffoldAuthWriterInjectsUserId() throws Exception {
        String java = src("src/main/java/com/codegen/service/impl/ScaffoldAuthWriter.java");
        assertTrue(java.contains("UserContext.setUserId(ids.get(0))"),
                "过滤器模板必须在 setUsername 后注入 userId");
        assertTrue(java.contains("SELECT id FROM sys_user WHERE username"),
                "过滤器模板必须按用户名查 user id");
    }
}
