-- ============================================================
-- V2.0 快速构建系统能力（P1-P3 所需结构）
-- 幂等：重复执行安全（MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，
-- 这里依赖服务端 DDL 接口对 1060 Duplicate column 做「已存在即跳过」处理）
--
-- 注意：不要写 DELIMITER —— 那是 mysql 客户端指令，JDBC 执行会语法错误。
-- 直接走平台「建库建表一键导入」接口或 mysql 客户端都可以执行本文件。
-- ============================================================

-- ---------- P1 ----------
-- 说明：以下列在 V1.0 的建表语句里已包含，但存量老库是靠本脚本升级来的，
-- 因此统一用 information_schema 判存在 + PREPARE 动态 DDL，空库/老库都可反复执行
-- （2026-11-07 空库冒烟实测：裸 ADD COLUMN 在第 13 行报 1060 Duplicate column 'mask_type'）。

-- P1-1 字段脱敏类型：phone/idcard/email/name/bankcard，空为不脱敏
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table_column' AND COLUMN_NAME = 'mask_type');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table_column ADD COLUMN mask_type varchar(16) NULL COMMENT ''脱敏类型''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- P1-3 唯一性校验（可多列组合成联合唯一）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table_column' AND COLUMN_NAME = 'unique_flag');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table_column ADD COLUMN unique_flag tinyint NOT NULL DEFAULT 0 COMMENT ''是否参与唯一性校验:0否1是''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- P1-4 表单联动规则 JSON
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table_column' AND COLUMN_NAME = 'link_rule');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table_column ADD COLUMN link_rule varchar(512) NULL COMMENT ''表单联动规则 JSON''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- P2 ----------
-- P2-1 数据权限标记：dept 按部门 / self 按本人
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table_column' AND COLUMN_NAME = 'data_scope');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table_column ADD COLUMN data_scope varchar(16) NULL COMMENT ''数据权限标记''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- P3 ----------
-- P3-2 中间表外键标记
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table_column' AND COLUMN_NAME = 'relation_key');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table_column ADD COLUMN relation_key tinyint NOT NULL DEFAULT 0 COMMENT ''是否中间表外键列:0否1是''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- P3-1 主子表层级深度，0 为顶层
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'master_depth');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN master_depth tinyint NOT NULL DEFAULT 0 COMMENT ''主子表层级深度''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- P3-2 中间表识别结果
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'relation_type');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN relation_type varchar(16) NULL COMMENT ''表关系类型：middle 中间表''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- P2-1 数据权限：部门归属 ----------
-- 角色表的权限范围：all 全部 / dept 本部门及以下 / self 仅本人
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'data_scope');
SET @s := IF(@c = 0, 'ALTER TABLE sys_role ADD COLUMN data_scope varchar(16) NOT NULL DEFAULT ''all'' COMMENT ''数据权限范围:all/dept/self''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 用户归属部门：dept 级数据权限按它过滤
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'dept_id');
SET @s := IF(@c = 0, 'ALTER TABLE sys_user ADD COLUMN dept_id bigint NULL COMMENT ''所属部门ID''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- P2-2 操作日志 ----------
-- 生成开关：是否为生成的 Controller 打 @Log（0 可关掉调试期的日志噪音）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'gen_log');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN gen_log tinyint NOT NULL DEFAULT 1 COMMENT ''开关:生成操作日志@Log''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

CREATE TABLE IF NOT EXISTS sys_oper_log
(
    id             bigint      NOT NULL COMMENT '主键',
    title          varchar(64) NOT NULL COMMENT '模块标题',
    oper_type      varchar(16) NOT NULL COMMENT 'INSERT/UPDATE/DELETE/IMPORT/EXPORT/OTHER',
    method         varchar(200) NULL COMMENT '方法名',
    request_uri    varchar(255) NULL COMMENT '请求地址',
    request_method varchar(10) NULL COMMENT 'HTTP 方法',
    oper_param     text        NULL COMMENT '请求参数（敏感字段已脱敏）',
    json_result    text        NULL COMMENT '返回结果',
    status         tinyint     NOT NULL DEFAULT 1 COMMENT '1成功0失败',
    error_msg      text        NULL COMMENT '错误消息',
    oper_user_id   bigint      NULL COMMENT '操作人 ID',
    oper_name      varchar(64) NULL COMMENT '操作人用户名',
    oper_ip        varchar(64) NULL COMMENT '操作 IP',
    cost_ms        bigint      NULL COMMENT '耗时毫秒',
    oper_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    deleted        tinyint     NOT NULL DEFAULT 0,
    create_by      bigint      NULL COMMENT '创建人',
    create_time    datetime    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      bigint      NULL COMMENT '更新人',
    update_time    datetime    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_oper_time (oper_time),
    KEY idx_oper_user (oper_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志';

-- ---------- P3-3 简单审批流 ----------
CREATE TABLE IF NOT EXISTS sys_flow_def
(
    id          bigint        NOT NULL COMMENT '主键',
    flow_code   varchar(64)   NOT NULL COMMENT '流程编码',
    flow_name   varchar(64)   NOT NULL COMMENT '流程名称',
    table_name  varchar(64)   NOT NULL COMMENT '绑定业务表名',
    status_col  varchar(64)   NOT NULL DEFAULT 'status' COMMENT '单据状态列',
    nodes       varchar(1024) NOT NULL COMMENT '状态机 JSON [{from,to,action,roleCode}]',
    enabled     tinyint       NOT NULL DEFAULT 1,
    remark      varchar(255)  NULL,
    create_time datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   bigint        NULL,
    update_by   bigint        NULL,
    deleted     tinyint       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_code (flow_code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审批流程定义';

CREATE TABLE IF NOT EXISTS sys_flow_task
(
    id           bigint       NOT NULL PRIMARY KEY,
    flow_code    varchar(64)  NOT NULL,
    biz_table    varchar(64)  NOT NULL,
    biz_id       bigint       NOT NULL,
    from_status  varchar(32)  NULL,
    to_status    varchar(32)  NOT NULL,
    action       varchar(16)  NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT',
    comment      varchar(512) NULL,
    oper_user_id bigint       NULL,
    oper_name    varchar(64)  NULL,
    oper_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      tinyint      NOT NULL DEFAULT 0,
    KEY idx_biz (biz_table, biz_id),
    KEY idx_flow (flow_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审批任务流水';
