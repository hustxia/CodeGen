-- V9.22 行业范式模板市场 + 安装机制（V1.30 第二期 / M1.2）
-- 范式包本体复用 industry-templates/*.json（V9.7 注册表 + V9.19 导入导出）
-- 本脚本只建：范式包元数据（四包的命中度/外接/边界标注）+ 安装台账
-- 幂等：重复执行无副作用

-- 1) 范式包元数据：四包的"交付级"标注（registry 的 JSON 只有表结构，没有这些判断）
CREATE TABLE IF NOT EXISTS gen_industry_paradigm (
    id              bigint       NOT NULL COMMENT '雪花ID',
    pack_key        varchar(64)  NOT NULL COMMENT '模板包key(对应industry-templates/*.json)',
    industry        varchar(64)  NOT NULL COMMENT '行业:村务治理/城市管理/智慧园区/智慧种植',
    category        varchar(64)  NOT NULL DEFAULT '' COMMENT '分类(与registry一致)',
    version         varchar(20)  NOT NULL DEFAULT '1.0' COMMENT '范式版本',
    hit_rate        int          NOT NULL DEFAULT 0 COMMENT '生成器命中度(0-100)',
    direct_tables   int          NOT NULL DEFAULT 0 COMMENT '直建表数',
    external_systems int         NOT NULL DEFAULT 0 COMMENT '必须外接的系统数',
    dedup_note      varchar(512) NOT NULL DEFAULT '' COMMENT '去重说明(哪些清单项合并)',
    boundary_note   varchar(512) NOT NULL DEFAULT '' COMMENT '不承诺边界',
    core_chain      varchar(512) NOT NULL DEFAULT '' COMMENT '业务主线(发现→立案→…)',
    sort_no         int          NOT NULL DEFAULT 0 COMMENT '展示排序',
    enabled         tinyint      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paradigm_key (pack_key),
    KEY idx_paradigm_industry (industry, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '行业范式包元数据(四包命中度/外接/边界)';

-- 2) 安装台账：记录项目装了哪个范式包/版本/模块，只生成plan不建库
CREATE TABLE IF NOT EXISTS gen_industry_install (
    id              bigint       NOT NULL COMMENT '雪花ID',
    project_id      bigint       NOT NULL COMMENT '项目ID(sys_project.id)',
    pack_key        varchar(64)  NOT NULL COMMENT '范式包key',
    pack_version    varchar(20)  NOT NULL DEFAULT '' COMMENT '安装时的范式版本',
    scope           varchar(20)  NOT NULL DEFAULT 'new' COMMENT 'new新建系统/supplement补表',
    module_names    text         COMMENT '安装的模块名JSON数组',
    table_count     int          NOT NULL DEFAULT 0 COMMENT 'plan表数',
    plan_summary    text         COMMENT 'plan摘要JSON(模块数/表数/排除数/来源)',
    status          varchar(20)  NOT NULL DEFAULT 'INSTALLED' COMMENT 'INSTALLED/REMOVED',
    install_time    datetime     DEFAULT CURRENT_TIMESTAMP,
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_install_project (project_id, status),
    KEY idx_install_pack (pack_key, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '行业范式安装台账(只记录plan不建库)';
