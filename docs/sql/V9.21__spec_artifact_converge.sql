-- V9.21 SDD 规格工件链 + Converge 对账（V1.30 第一期 / 三版本规划 M1.1）
-- 工件链：constitution → spec → plan → tables → tasks，保存即新增版本快照
-- 对账：spec 工件的期望清单（structured_json）逐项比对实际产物（gen_table / gen_table_column）
-- 幂等：重复执行无副作用

-- 1) SDD 规格工件
CREATE TABLE IF NOT EXISTS gen_spec_artifact (
    id              bigint       NOT NULL COMMENT '雪花ID',
    project_id      bigint       NOT NULL COMMENT '项目ID(sys_project.id)',
    artifact_type   varchar(20)  NOT NULL COMMENT '工件类型:CONSTITUTION/SPEC/PLAN/TABLES/TASKS',
    version         int          NOT NULL DEFAULT 1 COMMENT '版本号(同项目同类型递增)',
    title           varchar(128) NOT NULL DEFAULT '' COMMENT '标题',
    yaml_meta       text         COMMENT '结构化元数据JSON(对应Markdown front matter)',
    content_md      mediumtext   COMMENT 'Markdown正文(人与Agent共读)',
    structured_json mediumtext   COMMENT '期望清单JSON(converge比对数据源)',
    status          varchar(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/CONFIRMED',
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_spec_proj (project_id, artifact_type, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SDD规格工件(版本化)';

-- 2) Converge 对账报告
CREATE TABLE IF NOT EXISTS gen_converge_report (
    id            bigint      NOT NULL COMMENT '雪花ID',
    project_id    bigint      NOT NULL COMMENT '项目ID',
    artifact_id   bigint      NOT NULL COMMENT '对账依据的spec工件ID(gen_spec_artifact.id)',
    spec_version  int         NOT NULL DEFAULT 1 COMMENT 'spec工件版本号',
    ok_count      int         NOT NULL DEFAULT 0 COMMENT '已实现项数',
    missing_count int         NOT NULL DEFAULT 0 COMMENT '缺失项数',
    extra_count   int         NOT NULL DEFAULT 0 COMMENT '多余项数',
    status        varchar(20) NOT NULL DEFAULT 'UNCONVERGED' COMMENT '状态:CONVERGED/UNCONVERGED',
    create_time   datetime    DEFAULT CURRENT_TIMESTAMP,
    update_time   datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by     bigint      DEFAULT NULL,
    update_by     bigint      DEFAULT NULL,
    deleted       tinyint     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_conv_proj (project_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Converge规格对账报告';

-- 3) Converge 对账明细
CREATE TABLE IF NOT EXISTS gen_converge_item (
    id          bigint      NOT NULL COMMENT '雪花ID',
    report_id   bigint      NOT NULL COMMENT '报告ID(gen_converge_report.id)',
    item_type   varchar(10) NOT NULL COMMENT '项类型:TABLE/FIELD/ENUM',
    item_key    varchar(200) NOT NULL COMMENT '项键:表名 / 表名.列名 / 字典编码',
    kind        varchar(10) NOT NULL COMMENT '结论:OK/MISSING/EXTRA',
    source_ref  varchar(200) DEFAULT '' COMMENT '规格来源(如 spec.md §二)',
    suggestion  varchar(500) DEFAULT '' COMMENT '处理建议',
    create_time datetime    DEFAULT CURRENT_TIMESTAMP,
    update_time datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     tinyint     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_conv_item (report_id, kind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Converge对账明细';
