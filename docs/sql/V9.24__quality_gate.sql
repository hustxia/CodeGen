-- V9.24 生成代码质量门禁（M2.2）
-- 52 条可执行规则 + 确定性/LLM 双引擎；报告头 + 行级 ❌→✅ 明细
-- 幂等：重复执行无副作用

-- 1) 质量门禁报告
CREATE TABLE IF NOT EXISTS gen_quality_report (
    id              bigint       NOT NULL COMMENT '雪花ID',
    project_id      bigint       DEFAULT NULL COMMENT '项目ID(sys_project.id)',
    table_ids       varchar(1000) DEFAULT '' COMMENT '本次扫描的表ID列表(逗号分隔)',
    engine          varchar(20)  NOT NULL DEFAULT 'DET' COMMENT '引擎组合:DET/DET+LLM',
    llm_state       varchar(20)  NOT NULL DEFAULT 'DISABLED' COMMENT 'LLM状态:APPLIED/DEGRADED/DISABLED',
    status          varchar(20)  NOT NULL DEFAULT 'PASS' COMMENT '结论:PASS/FAIL',
    blocked         tinyint      NOT NULL DEFAULT 0 COMMENT '是否命中BLOCKER',
    blocker_count   int          NOT NULL DEFAULT 0 COMMENT 'BLOCKER数',
    warn_count      int          NOT NULL DEFAULT 0 COMMENT 'WARN数',
    issue_count     int          NOT NULL DEFAULT 0 COMMENT '问题总数',
    file_count      int          NOT NULL DEFAULT 0 COMMENT '扫描文件数',
    rule_count      int          NOT NULL DEFAULT 0 COMMENT '启用规则数',
    degraded        tinyint      NOT NULL DEFAULT 0 COMMENT 'LLM是否降级',
    degraded_reason varchar(500) DEFAULT '' COMMENT '降级原因',
    message         varchar(500) DEFAULT '' COMMENT '摘要',
    elapsed_ms      bigint       NOT NULL DEFAULT 0 COMMENT '耗时ms',
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_quality_proj (project_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '生成代码质量门禁报告';

-- 2) 行级问题明细
CREATE TABLE IF NOT EXISTS gen_quality_issue (
    id           bigint       NOT NULL COMMENT '雪花ID',
    report_id    bigint       NOT NULL COMMENT '报告ID(gen_quality_report.id)',
    rule_id      varchar(20)  NOT NULL COMMENT '规则ID(Q001..Q052)',
    category     varchar(20)  DEFAULT '' COMMENT '分类:JAVA/VUE/JS/SQL/CONFIG',
    engine       varchar(10)  NOT NULL DEFAULT 'DET' COMMENT '引擎:DET/LLM',
    severity     varchar(20)  NOT NULL COMMENT '级别:BLOCKER/WARN',
    file_path    varchar(500) DEFAULT '' COMMENT '文件相对路径',
    line_no      bigint       NOT NULL DEFAULT 0 COMMENT '行号(0=与行无关)',
    message      varchar(1000) DEFAULT '' COMMENT '问题描述',
    bad_snippet  text         COMMENT '❌ 命中片段',
    fix_snippet  text         COMMENT '✅ 修正示例',
    source_ref   varchar(300) DEFAULT '' COMMENT '坑库来源',
    create_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    bigint       DEFAULT NULL,
    update_by    bigint       DEFAULT NULL,
    deleted      tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_quality_issue (report_id, severity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '质量门禁行级问题明细';
