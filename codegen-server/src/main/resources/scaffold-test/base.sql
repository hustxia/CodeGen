-- V6.7 产物测试出厂：H2 (MODE=MySQL) 版登录/权限/日志/流程基座表
-- 与 db/init_sys_user.sql、db/init_sys_flow.sql 同源的测试副本（基座表极少变，改动需两处同步，契约测试守卫表集合）
-- 转换规则：去 ENGINE/CHARSET/表注释/ON UPDATE CURRENT_TIMESTAMP；UNIQUE KEY→CONSTRAINT；KEY→INDEX
-- 幂等用 INSERT IGNORE（H2 mem 库同 JVM 多 context 会重复执行本文件）
CREATE TABLE IF NOT EXISTS sys_user (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  username varchar(64) NOT NULL COMMENT '登录名',
  password varchar(72) NOT NULL COMMENT '密码（BCrypt 60位，兼容旧 SHA-256 64位）',
  password_algo varchar(16) NOT NULL DEFAULT 'bcrypt' COMMENT '密码算法：bcrypt/sha256',
  fail_count int NOT NULL DEFAULT 0 COMMENT '连续失败次数',
  lock_until datetime NULL COMMENT '锁定截止时间',
  nickname varchar(64) NULL COMMENT '昵称',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by bigint NULL COMMENT '创建人',
  update_by bigint NULL COMMENT '更新人',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  CONSTRAINT uk_username UNIQUE (username)
);
CREATE TABLE IF NOT EXISTS sys_session (
  token varchar(64) NOT NULL COMMENT '登录令牌',
  username varchar(64) NOT NULL COMMENT '登录名',
  login_ip varchar(64) NULL COMMENT '登录IP',
  login_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  expire_time datetime NOT NULL COMMENT '过期时间（滑动续期）',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (token),
  INDEX idx_session_expire (expire_time)
);
CREATE TABLE IF NOT EXISTS sys_login_log (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  username varchar(64) NULL COMMENT '尝试登录的用户名',
  ip varchar(64) NULL COMMENT '来源IP',
  region varchar(128) NULL COMMENT 'IP归属地',
  success tinyint NOT NULL DEFAULT 0 COMMENT '是否成功：1是0否',
  fail_reason varchar(255) NULL COMMENT '失败原因',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (id),
  INDEX idx_login_log_time (create_time)
);
CREATE TABLE IF NOT EXISTS sys_oper_log (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  title varchar(64) NULL COMMENT '模块标题',
  oper_type varchar(32) NULL COMMENT '操作类型',
  method varchar(128) NULL COMMENT '方法名',
  request_uri varchar(128) NULL COMMENT '请求URI',
  request_method varchar(16) NULL COMMENT '请求方式',
  oper_param text NULL COMMENT '请求参数',
  json_result text NULL COMMENT '返回结果',
  status tinyint NULL COMMENT '1成功 0失败',
  error_msg varchar(512) NULL COMMENT '错误消息',
  oper_user_id bigint NULL COMMENT '操作人ID',
  oper_name varchar(64) NULL COMMENT '操作人',
  oper_ip varchar(64) NULL COMMENT '操作IP',
  province varchar(32) NULL COMMENT '省份',
  city varchar(32) NULL COMMENT '城市',
  cost_ms bigint NULL COMMENT '耗时毫秒',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_by bigint NULL, create_time datetime NULL, update_by bigint NULL, update_time datetime NULL,
  PRIMARY KEY (id),
  INDEX idx_oper_log_time (create_time)
);
CREATE TABLE IF NOT EXISTS sys_role (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_name varchar(64) NOT NULL COMMENT '角色名',
  role_key varchar(64) NOT NULL COMMENT '角色标识（admin 直通）',
  remark varchar(255) NULL COMMENT '备注',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS sys_menu (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID',
  name varchar(64) NOT NULL COMMENT '名称',
  path varchar(128) NULL COMMENT '路由路径',
  component varchar(128) NULL COMMENT '组件路径',
  perms varchar(128) NULL COMMENT '权限标识',
  type tinyint NOT NULL DEFAULT 1 COMMENT '类型：1目录2菜单3按钮',
  sort int NOT NULL DEFAULT 0 COMMENT '排序',
  status tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用0停用',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id bigint NOT NULL COMMENT '用户ID',
  role_id bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id)
);
CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id bigint NOT NULL COMMENT '角色ID',
  menu_id bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (role_id, menu_id)
);
-- 种子：admin / admin123（BCrypt，与 db/init_sys_user.sql 同值）
INSERT IGNORE INTO sys_user (id, username, password, password_algo, nickname)
VALUES (1, 'admin', '$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO', 'bcrypt', '管理员');
INSERT IGNORE INTO sys_role (id, role_name, role_key, remark)
VALUES (1, '超级管理员', 'admin', '内置角色：接口权限点直通');
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);
-- V6.1 流程表（builtin/activiti 双引擎共用的投影表）
CREATE TABLE IF NOT EXISTS sys_flow_def (
  id bigint NOT NULL,
  flow_code varchar(64) NOT NULL COMMENT '流程编码',
  flow_name varchar(64) NOT NULL COMMENT '流程名称',
  table_name varchar(64) NOT NULL COMMENT '业务表名',
  status_col varchar(64) NOT NULL DEFAULT 'status' COMMENT '状态列',
  nodes varchar(1024) NOT NULL COMMENT '节点 JSON',
  bpmn_xml mediumtext NULL COMMENT 'BPMN XML',
  bpmn_hash varchar(64) DEFAULT NULL COMMENT '已部署 bpmn_xml sha256',
  enabled tinyint NOT NULL DEFAULT 1,
  remark varchar(255) DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_code UNIQUE (flow_code, deleted)
);
CREATE TABLE IF NOT EXISTS sys_flow_task (
  id bigint NOT NULL,
  flow_code varchar(64) NOT NULL,
  biz_table varchar(64) NOT NULL,
  biz_id bigint NOT NULL,
  from_status varchar(20) DEFAULT NULL,
  to_status varchar(20) DEFAULT NULL,
  action varchar(20) NOT NULL,
  comment varchar(255) DEFAULT NULL,
  oper_user_id bigint DEFAULT NULL,
  oper_name varchar(64) DEFAULT NULL,
  oper_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_flow_biz (flow_code, biz_id)
);
