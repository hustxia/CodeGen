-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V1.0__init.sql
-- 转换记录:
  [丢弃] DROP 语句（内存库每次全新建）
  [丢弃] DROP 语句（内存库每次全新建）
  [丢弃] DROP 语句（内存库每次全新建）
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] gen_template_history.id RESTART WITH 4
  [丢弃] DROP 语句（内存库每次全新建）
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] gen_history.id RESTART WITH 139
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_menu.id RESTART WITH 2112600000000000676
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_role.id RESTART WITH 4
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_role_menu.id RESTART WITH 141
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_user.id RESTART WITH 4
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_user_role.id RESTART WITH 4
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_dict_type.id RESTART WITH 8
  [丢弃] DROP 语句（内存库每次全新建）
  [序列] sys_dict_data.id RESTART WITH 21

CREATE TABLE IF NOT EXISTS `gen_table` (
  `id` bigint NOT NULL,
  `table_name` varchar(100) NOT NULL,
  `table_comment` varchar(200) NOT NULL DEFAULT '',
  `class_name` varchar(100) NOT NULL,
  `module_name` varchar(50) NOT NULL DEFAULT '',
  `package_name` varchar(100) NOT NULL DEFAULT 'com.demo',
  `backend_path` varchar(200) NOT NULL DEFAULT '',
  `backend_root` varchar(200) NOT NULL DEFAULT '',
  `frontend_root` varchar(200) NOT NULL DEFAULT '',
  `gateway_prefix` varchar(30) NOT NULL DEFAULT 'demo',
  `author` varchar(64) NOT NULL DEFAULT '',
  `target_module` varchar(50) DEFAULT NULL,
  `gen_entity` tinyint NOT NULL DEFAULT '1',
  `gen_mapper` tinyint NOT NULL DEFAULT '1',
  `gen_service` tinyint NOT NULL DEFAULT '1',
  `gen_log` tinyint NOT NULL DEFAULT '1',
  `gen_controller` tinyint NOT NULL DEFAULT '1',
  `gen_page_api` tinyint NOT NULL DEFAULT '1',
  `gen_detail_api` tinyint NOT NULL DEFAULT '1',
  `gen_add_api` tinyint NOT NULL DEFAULT '1',
  `gen_edit_api` tinyint NOT NULL DEFAULT '1',
  `gen_delete_api` tinyint NOT NULL DEFAULT '1',
  `gen_frontend` tinyint NOT NULL DEFAULT '1',
  `gen_api_js` tinyint NOT NULL DEFAULT '1',
  `gen_vue_page` tinyint NOT NULL DEFAULT '1',
  `gen_query_form` tinyint NOT NULL DEFAULT '1',
  `gen_add_btn` tinyint NOT NULL DEFAULT '1',
  `gen_edit_btn` tinyint NOT NULL DEFAULT '1',
  `gen_delete_btn` tinyint NOT NULL DEFAULT '1',
  `tree_parent_column` varchar(64) DEFAULT NULL,
  `tree_label_column` varchar(64) DEFAULT NULL,
  `master_table_id` bigint DEFAULT NULL,
  `master_foreign_key` varchar(64) DEFAULT NULL,
  `master_depth` tinyint NOT NULL DEFAULT '0',
  `relation_type` varchar(16) DEFAULT NULL,
  `remark` varchar(500) NOT NULL DEFAULT '',
  `menu_id` bigint DEFAULT NULL,
  `last_gen_time` datetime DEFAULT NULL,
  `last_gen_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_table_name` UNIQUE (`table_name`)
);

COMMENT ON TABLE `gen_table` IS '代码生成器-表配置';

COMMENT ON COLUMN `gen_table`.`id` IS '主键';

COMMENT ON COLUMN `gen_table`.`table_name` IS '表名';

COMMENT ON COLUMN `gen_table`.`table_comment` IS '表注释（功能名）';

COMMENT ON COLUMN `gen_table`.`class_name` IS '实体类名（大驼峰）';

COMMENT ON COLUMN `gen_table`.`module_name` IS '前端模块名（api/<模块>.js、views/<模块>/）';

COMMENT ON COLUMN `gen_table`.`package_name` IS '生成 Java 包名';

COMMENT ON COLUMN `gen_table`.`backend_path` IS '后端输出基准相对路径';

COMMENT ON COLUMN `gen_table`.`backend_root` IS '后端仓库根绝对路径';

COMMENT ON COLUMN `gen_table`.`frontend_root` IS '前端仓库根绝对路径';

COMMENT ON COLUMN `gen_table`.`gateway_prefix` IS '前端网关服务前缀';

COMMENT ON COLUMN `gen_table`.`author` IS '作者';

COMMENT ON COLUMN `gen_table`.`target_module` IS '目标模块key(product/order/...)，空=沿用当前配置';

COMMENT ON COLUMN `gen_table`.`gen_entity` IS '开关:生成entity';

COMMENT ON COLUMN `gen_table`.`gen_mapper` IS '开关:生成mapper';

COMMENT ON COLUMN `gen_table`.`gen_service` IS '开关:生成service+impl';

COMMENT ON COLUMN `gen_table`.`gen_log` IS '开关:生成操作日志@Log';

COMMENT ON COLUMN `gen_table`.`gen_controller` IS '开关:生成controller';

COMMENT ON COLUMN `gen_table`.`gen_page_api` IS '开关:分页接口';

COMMENT ON COLUMN `gen_table`.`gen_detail_api` IS '开关:详情接口';

COMMENT ON COLUMN `gen_table`.`gen_add_api` IS '开关:新增接口';

COMMENT ON COLUMN `gen_table`.`gen_edit_api` IS '开关:修改接口';

COMMENT ON COLUMN `gen_table`.`gen_delete_api` IS '开关:删除接口';

COMMENT ON COLUMN `gen_table`.`gen_frontend` IS '开关:生成前端';

COMMENT ON COLUMN `gen_table`.`gen_api_js` IS '开关:生成api.js';

COMMENT ON COLUMN `gen_table`.`gen_vue_page` IS '开关:生成vue页面';

COMMENT ON COLUMN `gen_table`.`gen_query_form` IS '开关:查询区';

COMMENT ON COLUMN `gen_table`.`gen_add_btn` IS '开关:新增按钮';

COMMENT ON COLUMN `gen_table`.`gen_edit_btn` IS '开关:编辑按钮';

COMMENT ON COLUMN `gen_table`.`gen_delete_btn` IS '开关:删除按钮';

COMMENT ON COLUMN `gen_table`.`tree_parent_column` IS '树形父级列(如parent_id)，非空即按树形生成';

COMMENT ON COLUMN `gen_table`.`tree_label_column` IS '树节点显示列';

COMMENT ON COLUMN `gen_table`.`master_table_id` IS '作为子表时所属主表的gen_table.id';

COMMENT ON COLUMN `gen_table`.`master_foreign_key` IS '子表中指向主表主键的列';

COMMENT ON COLUMN `gen_table`.`master_depth` IS 'P3-1 主子表层级深度，0为顶层';

COMMENT ON COLUMN `gen_table`.`relation_type` IS 'P3-2 表关系类型:middle 多对多中间表';

COMMENT ON COLUMN `gen_table`.`remark` IS '备注';

COMMENT ON COLUMN `gen_table`.`menu_id` IS '已注册菜单ID(sys_menu.id)';

COMMENT ON COLUMN `gen_table`.`last_gen_time` IS '最近一次写盘时间';

COMMENT ON COLUMN `gen_table`.`last_gen_by` IS '最近一次写盘操作人';

COMMENT ON COLUMN `gen_table`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `gen_table`.`create_by` IS '创建人';

COMMENT ON COLUMN `gen_table`.`create_time` IS '创建时间';

COMMENT ON COLUMN `gen_table`.`update_by` IS '更新人';

COMMENT ON COLUMN `gen_table`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `gen_table_column` (
  `id` bigint NOT NULL,
  `table_id` bigint NOT NULL,
  `column_name` varchar(100) NOT NULL,
  `column_comment` varchar(200) NOT NULL DEFAULT '',
  `column_type` varchar(50) NOT NULL,
  `java_field` varchar(100) NOT NULL,
  `java_type` varchar(50) NOT NULL,
  `is_pk` tinyint NOT NULL DEFAULT '0',
  `is_required` tinyint NOT NULL DEFAULT '0',
  `is_query` tinyint NOT NULL DEFAULT '0',
  `query_type` varchar(10) NOT NULL DEFAULT 'EQ',
  `is_list` tinyint NOT NULL DEFAULT '1',
  `is_form` tinyint NOT NULL DEFAULT '1',
  `html_type` varchar(32) NOT NULL DEFAULT 'input',
  `dict_type` varchar(64) DEFAULT NULL,
  `ref_table` varchar(64) DEFAULT NULL,
  `ref_label` varchar(64) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `validate_rule` varchar(255) DEFAULT NULL,
  `excel_export` tinyint NOT NULL DEFAULT '1',
  `excel_import` tinyint NOT NULL DEFAULT '0',
  `mask_type` varchar(16) DEFAULT NULL,
  `unique_flag` tinyint NOT NULL DEFAULT '0',
  `link_rule` varchar(512) DEFAULT NULL,
  `data_scope` varchar(16) DEFAULT NULL,
  `relation_key` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_table_id` ON `gen_table_column` (`table_id`);

COMMENT ON TABLE `gen_table_column` IS '代码生成器-字段配置';

COMMENT ON COLUMN `gen_table_column`.`id` IS '主键';

COMMENT ON COLUMN `gen_table_column`.`table_id` IS '表配置ID（gen_table.id）';

COMMENT ON COLUMN `gen_table_column`.`column_name` IS '数据库列名';

COMMENT ON COLUMN `gen_table_column`.`column_comment` IS '列注释';

COMMENT ON COLUMN `gen_table_column`.`column_type` IS '数据库类型';

COMMENT ON COLUMN `gen_table_column`.`java_field` IS 'Java属性名（小驼峰）';

COMMENT ON COLUMN `gen_table_column`.`java_type` IS 'Java类型';

COMMENT ON COLUMN `gen_table_column`.`is_pk` IS '是否主键:0否1是';

COMMENT ON COLUMN `gen_table_column`.`is_required` IS '是否必填:0否1是';

COMMENT ON COLUMN `gen_table_column`.`is_query` IS '是否查询条件:0否1是';

COMMENT ON COLUMN `gen_table_column`.`query_type` IS '查询方式:EQ/LIKE/GE/LE';

COMMENT ON COLUMN `gen_table_column`.`is_list` IS '列表是否显示:0否1是';

COMMENT ON COLUMN `gen_table_column`.`is_form` IS '表单是否录入:0否1是';

COMMENT ON COLUMN `gen_table_column`.`html_type` IS '琛ㄥ崟鎺т欢:input/textarea/number/select/radio/switch/date/datetime';

COMMENT ON COLUMN `gen_table_column`.`dict_type` IS '瀛楀吀缂栫爜(瀵瑰簲 sys_dict_type.dict_code)';

COMMENT ON COLUMN `gen_table_column`.`ref_table` IS '关联下拉来源表';

COMMENT ON COLUMN `gen_table_column`.`ref_label` IS '关联下拉显示列';

COMMENT ON COLUMN `gen_table_column`.`sort` IS '显示顺序';

COMMENT ON COLUMN `gen_table_column`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `gen_table_column`.`create_by` IS '创建人';

COMMENT ON COLUMN `gen_table_column`.`create_time` IS '创建时间';

COMMENT ON COLUMN `gen_table_column`.`update_by` IS '更新人';

COMMENT ON COLUMN `gen_table_column`.`update_time` IS '更新时间';

COMMENT ON COLUMN `gen_table_column`.`validate_rule` IS '鏍￠獙瑙勫垯:required,maxlen=50,min=1,max=9999,email,phone,url,regex=...';

COMMENT ON COLUMN `gen_table_column`.`excel_export` IS '是否导出Excel';

COMMENT ON COLUMN `gen_table_column`.`excel_import` IS '是否允许导入';

COMMENT ON COLUMN `gen_table_column`.`mask_type` IS 'P1-1 脱敏类型:phone/idcard/email/name/bankcard，空为不脱敏';

COMMENT ON COLUMN `gen_table_column`.`unique_flag` IS 'P1-3 是否参与唯一性校验:0否1是（多列同置1则联合唯一）';

COMMENT ON COLUMN `gen_table_column`.`link_rule` IS 'P1-4 表单联动规则 JSON';

COMMENT ON COLUMN `gen_table_column`.`data_scope` IS 'P2-1 数据权限标记:dept 按部门/self 按本人';

COMMENT ON COLUMN `gen_table_column`.`relation_key` IS 'P3-2 是否中间表外键列:0否1是';

CREATE TABLE IF NOT EXISTS `gen_template` (
  `id` bigint NOT NULL,
  `tpl_code` varchar(50) NOT NULL,
  `tpl_name` varchar(100) NOT NULL DEFAULT '',
  `file_group` varchar(20) NOT NULL DEFAULT '',
  `content` mediumtext,
  `path_pattern` varchar(500) NOT NULL DEFAULT '',
  `table_id` bigint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `version` int NOT NULL DEFAULT '1',
  `remark` varchar(500) NOT NULL DEFAULT '',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_code_table` UNIQUE (`tpl_code`,`table_id`,`deleted`)
);

COMMENT ON TABLE `gen_template` IS '代码生成器-模板配置';

COMMENT ON COLUMN `gen_template`.`id` IS '主键';

COMMENT ON COLUMN `gen_template`.`tpl_code` IS '模板编码:entity/mapper/service/serviceImpl/pageDto/saveDto/vo/controller/apiJs/vuePage/routerSnippet/readme';

COMMENT ON COLUMN `gen_template`.`tpl_name` IS '模板名称';

COMMENT ON COLUMN `gen_template`.`file_group` IS '产物分组:backend/frontend/shared';

COMMENT ON COLUMN `gen_template`.`content` IS 'Freemarker 模板内容';

COMMENT ON COLUMN `gen_template`.`path_pattern` IS '输出相对路径模式（支持 ${var} 占位）';

COMMENT ON COLUMN `gen_template`.`table_id` IS '归属表:0全局模板 >0表级覆盖';

COMMENT ON COLUMN `gen_template`.`status` IS '状态:0停用 1启用';

COMMENT ON COLUMN `gen_template`.`version` IS '版本号，每次保存+1';

COMMENT ON COLUMN `gen_template`.`remark` IS '备注';

COMMENT ON COLUMN `gen_template`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `gen_template`.`create_by` IS '创建人';

COMMENT ON COLUMN `gen_template`.`create_time` IS '创建时间';

COMMENT ON COLUMN `gen_template`.`update_by` IS '更新人';

COMMENT ON COLUMN `gen_template`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `gen_template_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `tpl_code` varchar(50) NOT NULL,
  `table_id` bigint NOT NULL DEFAULT '0',
  `version` int NOT NULL,
  `content` mediumtext NOT NULL,
  `path_pattern` varchar(255) NOT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_tpl` ON `gen_template_history` (`template_id`,`version`);

CREATE INDEX IF NOT EXISTS `idx_code` ON `gen_template_history` (`tpl_code`,`table_id`);

COMMENT ON TABLE `gen_template_history` IS '模板版本快照';

COMMENT ON COLUMN `gen_template_history`.`template_id` IS 'gen_template.id';

COMMENT ON COLUMN `gen_template_history`.`table_id` IS '0=全局模板';

ALTER TABLE `gen_template_history` ALTER COLUMN `id` RESTART WITH 4;

CREATE TABLE IF NOT EXISTS `gen_output` (
  `id` bigint NOT NULL,
  `batch_no` varchar(32) NOT NULL,
  `table_id` bigint NOT NULL,
  `table_name` varchar(100) NOT NULL,
  `file_group` varchar(20) NOT NULL DEFAULT '',
  `rel_path` varchar(500) NOT NULL,
  `abs_path` varchar(500) NOT NULL DEFAULT '',
  `action` varchar(20) NOT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `md5` varchar(32) NOT NULL DEFAULT '',
  `message` varchar(500) NOT NULL DEFAULT '',
  `operator` varchar(64) NOT NULL DEFAULT '',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_batch_no` ON `gen_output` (`batch_no`);

CREATE INDEX IF NOT EXISTS `idx_table_id` ON `gen_output` (`table_id`);

COMMENT ON TABLE `gen_output` IS '代码生成器-写盘文件级台账';

COMMENT ON COLUMN `gen_output`.`id` IS '主键';

COMMENT ON COLUMN `gen_output`.`batch_no` IS '写盘批次号（GEN+yyyyMMddHHmmss，三入口统一生成）';

COMMENT ON COLUMN `gen_output`.`table_id` IS '表配置ID（gen_table.id）';

COMMENT ON COLUMN `gen_output`.`table_name` IS '表名';

COMMENT ON COLUMN `gen_output`.`file_group` IS '产物分组:backend/frontend';

COMMENT ON COLUMN `gen_output`.`rel_path` IS '相对路径';

COMMENT ON COLUMN `gen_output`.`abs_path` IS '写盘绝对路径（SKIPPED/ERROR 可为空）';

COMMENT ON COLUMN `gen_output`.`action` IS '动作:WRITTEN新建/OVERWRITTEN覆盖/MERGED合并块/SKIPPED跳过/ERROR失败';

COMMENT ON COLUMN `gen_output`.`file_size` IS '文件字节数（未写盘为 0）';

COMMENT ON COLUMN `gen_output`.`md5` IS '写盘内容 MD5（未写盘为空串）';

COMMENT ON COLUMN `gen_output`.`message` IS '说明（合并块/跳过/失败原因）';

COMMENT ON COLUMN `gen_output`.`operator` IS '操作人';

COMMENT ON COLUMN `gen_output`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `gen_output`.`create_by` IS '创建人';

COMMENT ON COLUMN `gen_output`.`create_time` IS '创建时间';

COMMENT ON COLUMN `gen_output`.`update_by` IS '更新人';

COMMENT ON COLUMN `gen_output`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `gen_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(40) NOT NULL,
  `table_name` varchar(100) NOT NULL,
  `file_path` varchar(500) NOT NULL,
  `backup_path` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `operator` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_history_batch` ON `gen_history` (`batch_no`);

CREATE INDEX IF NOT EXISTS `idx_history_table` ON `gen_history` (`table_name`);

COMMENT ON TABLE `gen_history` IS '代码生成写盘历史';

COMMENT ON COLUMN `gen_history`.`batch_no` IS '批次号（一次 apply 一个批次）';

COMMENT ON COLUMN `gen_history`.`table_name` IS '表名';

COMMENT ON COLUMN `gen_history`.`file_path` IS '落盘绝对路径';

COMMENT ON COLUMN `gen_history`.`backup_path` IS '覆盖前备份路径（文件原本不存在则为空）';

COMMENT ON COLUMN `gen_history`.`status` IS 'WRITTEN/SKIPPED/ERROR';

COMMENT ON COLUMN `gen_history`.`operator` IS '操作人';

COMMENT ON COLUMN `gen_history`.`create_time` IS '操作时间';

ALTER TABLE `gen_history` ALTER COLUMN `id` RESTART WITH 139;

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0',
  `name` varchar(100) NOT NULL,
  `path` varchar(200) DEFAULT NULL,
  `component` varchar(200) DEFAULT NULL,
  `perms` varchar(100) DEFAULT NULL,
  `type` tinyint NOT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `icon` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_parent` ON `sys_menu` (`parent_id`);

COMMENT ON TABLE `sys_menu` IS '菜单';

COMMENT ON COLUMN `sys_menu`.`parent_id` IS '父菜单ID';

COMMENT ON COLUMN `sys_menu`.`name` IS '菜单名称';

COMMENT ON COLUMN `sys_menu`.`path` IS '路由路径';

COMMENT ON COLUMN `sys_menu`.`component` IS '前端组件路径（相对 src/views，如 demo_goods/index）';

COMMENT ON COLUMN `sys_menu`.`perms` IS '权限标识（如 demo_goods:add）';

COMMENT ON COLUMN `sys_menu`.`type` IS '1目录 2菜单 3按钮';

COMMENT ON COLUMN `sys_menu`.`sort` IS '排序';

COMMENT ON COLUMN `sys_menu`.`icon` IS '图标';

COMMENT ON COLUMN `sys_menu`.`status` IS '0禁用 1启用';

COMMENT ON COLUMN `sys_menu`.`create_by` IS '创建人';

COMMENT ON COLUMN `sys_menu`.`update_by` IS '更新人';

ALTER TABLE `sys_menu` ALTER COLUMN `id` RESTART WITH 2112600000000000676;

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_code` UNIQUE (`code`)
);

COMMENT ON TABLE `sys_role` IS '角色';

COMMENT ON COLUMN `sys_role`.`code` IS '角色编码';

COMMENT ON COLUMN `sys_role`.`name` IS '角色名称';

COMMENT ON COLUMN `sys_role`.`status` IS '0禁用 1启用';

ALTER TABLE `sys_role` ALTER COLUMN `id` RESTART WITH 4;

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_role_menu` UNIQUE (`role_id`,`menu_id`)
);

COMMENT ON TABLE `sys_role_menu` IS '角色菜单关联';

COMMENT ON COLUMN `sys_role_menu`.`create_by` IS '创建人';

COMMENT ON COLUMN `sys_role_menu`.`update_by` IS '更新人';

ALTER TABLE `sys_role_menu` ALTER COLUMN `id` RESTART WITH 141;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(128) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_username` UNIQUE (`username`)
);

COMMENT ON TABLE `sys_user` IS '平台用户';

COMMENT ON COLUMN `sys_user`.`username` IS '用户名';

COMMENT ON COLUMN `sys_user`.`password` IS '密码（BCrypt）';

COMMENT ON COLUMN `sys_user`.`nickname` IS '昵称';

COMMENT ON COLUMN `sys_user`.`email` IS '邮箱';

COMMENT ON COLUMN `sys_user`.`phone` IS '手机号';

COMMENT ON COLUMN `sys_user`.`status` IS '0禁用 1启用';

ALTER TABLE `sys_user` ALTER COLUMN `id` RESTART WITH 4;

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_user_role` UNIQUE (`user_id`,`role_id`)
);

CREATE INDEX IF NOT EXISTS `idx_ur_role` ON `sys_user_role` (`role_id`);

COMMENT ON TABLE `sys_user_role` IS '用户角色关联';

ALTER TABLE `sys_user_role` ALTER COLUMN `id` RESTART WITH 4;

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) NOT NULL,
  `dict_name` varchar(128) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_dict_code` UNIQUE (`dict_code`)
);

COMMENT ON TABLE `sys_dict_type` IS '字典类型表';

COMMENT ON COLUMN `sys_dict_type`.`dict_code` IS '字典编码';

COMMENT ON COLUMN `sys_dict_type`.`dict_name` IS '字典名称';

ALTER TABLE `sys_dict_type` ALTER COLUMN `id` RESTART WITH 8;

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) NOT NULL,
  `dict_label` varchar(128) NOT NULL,
  `dict_value` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_dictdata_code` ON `sys_dict_data` (`dict_code`);

COMMENT ON TABLE `sys_dict_data` IS '字典数据表';

ALTER TABLE `sys_dict_data` ALTER COLUMN `id` RESTART WITH 21;

INSERT INTO `sys_user` (`id`,`username`,`password`,`nickname`,`email`,`phone`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1,'admin','$2a$10$nBtf8GMTgvqaZpSotLFpROi8.Z0LRUmFpHhYj9E2wcqUHvYGhVKhW','超级管理员','admin@demo.local','',1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `password`=VALUES(`password`), `nickname`=VALUES(`nickname`), `status`=1, `deleted`=0;

INSERT INTO `sys_role` (`id`,`code`,`name`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1,'admin','超级管理员',1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`), `name`=VALUES(`name`), `status`=1, `deleted`=0;

INSERT INTO `sys_user_role` (`id`,`user_id`,`role_id`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1,1,1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`), `deleted`=0;

INSERT INTO `sys_menu` (`id`,`parent_id`,`name`,`path`,`component`,`perms`,`type`,`sort`,`icon`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1000,0,'代码生成','/gen',NULL,NULL,1,1,'code',1,NOW(),NOW(),1,1,0),
(1010,1000,'代码生成','/gen/table','gen/table/index','gen:table:list',2,1,'form',1,NOW(),NOW(),1,1,0),
(1020,1000,'批量接管','/gen/wizard','gen/wizard/index','gen:wizard:list',2,2,'guide',1,NOW(),NOW(),1,1,0),
(1030,1000,'代码模板','/gen/template','gen/template/index','gen:template:list',2,3,'documentation',1,NOW(),NOW(),1,1,0),
(1040,1000,'生成台账','/gen/output','gen/output/index','gen:output:list',2,4,'list',1,NOW(),NOW(),1,1,0),
(2000,0,'系统管理','/system',NULL,NULL,1,2,'setting',1,NOW(),NOW(),1,1,0),
(2010,2000,'用户管理','/system/user','system/user/index','system:user:list',2,1,'user',1,NOW(),NOW(),1,1,0),
(2020,2000,'角色管理','/system/role','system/role/index','system:role:list',2,2,'peoples',1,NOW(),NOW(),1,1,0),
(2030,2000,'菜单管理','/system/menu','system/menu/index','system:menu:list',2,3,'tree-table',1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `path`=VALUES(`path`), `component`=VALUES(`component`),
  `perms`=VALUES(`perms`), `type`=VALUES(`type`), `sort`=VALUES(`sort`), `icon`=VALUES(`icon`), `status`=1, `deleted`=0;

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`)
SELECT 9000 + m.`id`, 1, m.`id`, NOW(), NOW(), 1, 1, 0 FROM `sys_menu` m
ON DUPLICATE KEY UPDATE `deleted`=0;
