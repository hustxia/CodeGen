-- V9.25 M2.1 护栏澄清向导 + 28 条行业规则（V1.30 质量闭环首期）
-- 幂等：全部 CREATE TABLE IF NOT EXISTS；只允许 CREATE / ADD COLUMN
-- 规则与问题是数据（可改可停），判定引擎是代码，业务知识不硬编码进 Java

-- 1) 行业护栏规则：4 行业 × 7 条 = 28
CREATE TABLE IF NOT EXISTS gen_guardrail_rule (
    id              bigint       NOT NULL COMMENT '雪花ID',
    rule_code       varchar(32)  NOT NULL COMMENT '规则编码:GOV-B01',
    industry        varchar(32)  NOT NULL COMMENT 'gov_affairs/cm_city/agr_crop/park_park',
    category        varchar(20)  NOT NULL COMMENT 'BLACKLIST/WHITELIST/CONFLICT/BOUNDARY/CLARIFY',
    severity        varchar(16)  NOT NULL DEFAULT 'WARN' COMMENT 'BLOCK/WARN/INFO',
    trigger_type    varchar(20)  NOT NULL DEFAULT 'KEYWORD' COMMENT 'KEYWORD/MISSING/COMBO/ANSWER/CLARIFY',
    trigger_expr    varchar(512) NOT NULL DEFAULT '' COMMENT '触发表达式:关键词用|分隔;其余为JSON',
    title           varchar(200) NOT NULL COMMENT '规则标题',
    reason          varchar(1000) NOT NULL DEFAULT '' COMMENT '为什么拦（命中解释）',
    suggestion      varchar(1000) NOT NULL DEFAULT '' COMMENT '怎么做才对（修正建议）',
    sort_no         int          NOT NULL DEFAULT 0 COMMENT '展示排序',
    enabled         tinyint      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_guardrail_code (rule_code),
    KEY idx_guardrail_industry (industry, category, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '行业护栏规则(28条,可启停)';

-- 2) 澄清问题库：4 个通用问题 + 4 个行业专属问题；向导取 4+1=5 问
CREATE TABLE IF NOT EXISTS gen_clarify_question (
    id              bigint       NOT NULL COMMENT '雪花ID',
    question_code   varchar(64)  NOT NULL COMMENT '问题码(答复幂等键)',
    industry        varchar(32)  NOT NULL DEFAULT '' COMMENT '空串=通用;否则行业key',
    sort_no         int          NOT NULL DEFAULT 0 COMMENT '提问顺序(通用1-4,行业5)',
    question        varchar(500) NOT NULL COMMENT '问题文本',
    options_json    varchar(1000) NOT NULL DEFAULT '' COMMENT '选项JSON数组;空=自由文本',
    hint            varchar(500) NOT NULL DEFAULT '' COMMENT '为什么问这个问题',
    enabled         tinyint      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       bigint       DEFAULT NULL,
    update_by       bigint       DEFAULT NULL,
    deleted         tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_clarify_question_code (question_code),
    KEY idx_clarify_question (industry, sort_no, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '澄清向导问题库(一次一问,最多5问)';

-- 3) 澄清会话：问答/命中/忽略均留痕，确认时写 SPEC 工件
CREATE TABLE IF NOT EXISTS gen_clarify_session (
    id                  bigint       NOT NULL COMMENT '雪花ID',
    project_id          bigint       NOT NULL COMMENT '项目ID(sys_project.id)',
    industry            varchar(32)  NOT NULL COMMENT '行业key',
    system_name         varchar(200) NOT NULL COMMENT '系统名称',
    system_desc         text         COMMENT '系统描述',
    module_names        text         COMMENT '候选模块JSON数组',
    answers_json        text         COMMENT '问答JSON:{questionCode:answer}',
    hits_json           text         COMMENT '命中记录JSON:[{ruleCode,severity,...}]',
    overridden_codes    varchar(1000) NOT NULL DEFAULT '' COMMENT '高级模式显式忽略的规则码',
    advanced_mode       tinyint      NOT NULL DEFAULT 0 COMMENT '0标准 1高级(可忽略BLOCK)',
    question_index      int          NOT NULL DEFAULT 0 COMMENT '当前问到第几题(0-based)',
    status              varchar(20)  NOT NULL DEFAULT 'CLARIFYING' COMMENT 'CLARIFYING/READY/CONFIRMED/ABANDONED',
    spec_id             bigint       DEFAULT NULL COMMENT '确认后写入的SPEC工件ID',
    create_time         datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time         datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by           bigint       DEFAULT NULL,
    update_by           bigint       DEFAULT NULL,
    deleted             tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_clarify_session_project (project_id, status),
    KEY idx_clarify_session_industry (industry, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '澄清向导会话(问答与拦截留痕)';
