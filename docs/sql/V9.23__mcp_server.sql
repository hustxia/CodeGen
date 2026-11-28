-- V9.23 M1.3 MCP Server
-- 仅审计 tools/call；initialize、tools/list 不落调用日志。
-- 幂等：重复执行无副作用。

CREATE TABLE IF NOT EXISTS gen_mcp_call_log (
    id              bigint       NOT NULL COMMENT '雪花ID',
    tool_name       varchar(64)  NOT NULL COMMENT 'MCP工具名',
    arguments_json  mediumtext   COMMENT '调用参数JSON',
    status          varchar(10)  NOT NULL COMMENT '调用状态:SUCCESS/FAILED',
    result_summary  varchar(2000) DEFAULT NULL COMMENT '成功结果摘要JSON(截断)',
    error_message   varchar(2000) DEFAULT NULL COMMENT '失败错误摘要(截断)',
    duration_ms     bigint       NOT NULL DEFAULT 0 COMMENT '调用耗时毫秒',
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_mcp_log_time (create_time),
    KEY idx_mcp_log_tool (tool_name, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MCP工具调用审计日志';
