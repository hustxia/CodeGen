-- ============================================================
-- 代码生成器平台（codegen-platform）初始化脚本
-- 数据库：gen_platform
-- 说明：
--   1) 本脚本只包含平台自身所需表：生成器 6 张 + 系统管理 5 张 + 字典 2 张
--   2) 默认账号 admin / admin123
--   3) 可重复执行（DROP TABLE IF EXISTS + CREATE）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `gen_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `gen_platform`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================ 表结构 ============================
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table` (
  `id` bigint NOT NULL COMMENT '主键',
  `table_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名',
  `table_comment` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '表注释（功能名）',
  `class_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实体类名（大驼峰）',
  `module_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '前端模块名（api/<模块>.js、views/<模块>/）',
  `package_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'com.demo' COMMENT '生成 Java 包名',
  `backend_path` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '后端输出基准相对路径',
  `backend_root` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '后端仓库根绝对路径',
  `frontend_root` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '前端仓库根绝对路径',
  `gateway_prefix` varchar(30) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'demo' COMMENT '前端网关服务前缀',
  `author` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '作者',
  `target_module` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标模块key(product/order/...)，空=沿用当前配置',
  `gen_entity` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成entity',
  `gen_mapper` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成mapper',
  `gen_service` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成service+impl',
  `gen_log` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成操作日志@Log',
  `gen_controller` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成controller',
  `gen_page_api` tinyint NOT NULL DEFAULT '1' COMMENT '开关:分页接口',
  `gen_detail_api` tinyint NOT NULL DEFAULT '1' COMMENT '开关:详情接口',
  `gen_add_api` tinyint NOT NULL DEFAULT '1' COMMENT '开关:新增接口',
  `gen_edit_api` tinyint NOT NULL DEFAULT '1' COMMENT '开关:修改接口',
  `gen_delete_api` tinyint NOT NULL DEFAULT '1' COMMENT '开关:删除接口',
  `gen_frontend` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成前端',
  `gen_api_js` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成api.js',
  `gen_vue_page` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成vue页面',
  `gen_query_form` tinyint NOT NULL DEFAULT '1' COMMENT '开关:查询区',
  `gen_add_btn` tinyint NOT NULL DEFAULT '1' COMMENT '开关:新增按钮',
  `gen_edit_btn` tinyint NOT NULL DEFAULT '1' COMMENT '开关:编辑按钮',
  `gen_delete_btn` tinyint NOT NULL DEFAULT '1' COMMENT '开关:删除按钮',
  `tree_parent_column` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '树形父级列(如parent_id)，非空即按树形生成',
  `tree_label_column` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '树节点显示列',
  `master_table_id` bigint DEFAULT NULL COMMENT '作为子表时所属主表的gen_table.id',
  `master_foreign_key` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表中指向主表主键的列',
  `master_depth` tinyint NOT NULL DEFAULT '0' COMMENT 'P3-1 主子表层级深度，0为顶层',
  `relation_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'P3-2 表关系类型:middle 多对多中间表',
  `remark` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '备注',
  `menu_id` bigint DEFAULT NULL COMMENT '已注册菜单ID(sys_menu.id)',
  `last_gen_time` datetime DEFAULT NULL COMMENT '最近一次写盘时间',
  `last_gen_by` bigint DEFAULT NULL COMMENT '最近一次写盘操作人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-表配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column` (
  `id` bigint NOT NULL COMMENT '主键',
  `table_id` bigint NOT NULL COMMENT '表配置ID（gen_table.id）',
  `column_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据库列名',
  `column_comment` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '列注释',
  `column_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据库类型',
  `java_field` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Java属性名（小驼峰）',
  `java_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Java类型',
  `is_pk` tinyint NOT NULL DEFAULT '0' COMMENT '是否主键:0否1是',
  `is_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填:0否1是',
  `is_query` tinyint NOT NULL DEFAULT '0' COMMENT '是否查询条件:0否1是',
  `query_type` varchar(10) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'EQ' COMMENT '查询方式:EQ/LIKE/GE/LE',
  `is_list` tinyint NOT NULL DEFAULT '1' COMMENT '列表是否显示:0否1是',
  `is_form` tinyint NOT NULL DEFAULT '1' COMMENT '表单是否录入:0否1是',
  `html_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'input' COMMENT '琛ㄥ崟鎺т欢:input/textarea/number/select/radio/switch/date/datetime',
  `dict_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '瀛楀吀缂栫爜(瀵瑰簲 sys_dict_type.dict_code)',
  `ref_table` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联下拉来源表',
  `ref_label` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联下拉显示列',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `validate_rule` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '鏍￠獙瑙勫垯:required,maxlen=50,min=1,max=9999,email,phone,url,regex=...',
  `excel_export` tinyint NOT NULL DEFAULT '1' COMMENT '是否导出Excel',
  `excel_import` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许导入',
  `mask_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'P1-1 脱敏类型:phone/idcard/email/name/bankcard，空为不脱敏',
  `unique_flag` tinyint NOT NULL DEFAULT '0' COMMENT 'P1-3 是否参与唯一性校验:0否1是（多列同置1则联合唯一）',
  `link_rule` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'P1-4 表单联动规则 JSON',
  `data_scope` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'P2-1 数据权限标记:dept 按部门/self 按本人',
  `relation_key` tinyint NOT NULL DEFAULT '0' COMMENT 'P3-2 是否中间表外键列:0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-字段配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `gen_template`;
CREATE TABLE `gen_template` (
  `id` bigint NOT NULL COMMENT '主键',
  `tpl_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码:entity/mapper/service/serviceImpl/pageDto/saveDto/vo/controller/apiJs/vuePage/routerSnippet/readme',
  `tpl_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '模板名称',
  `file_group` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '产物分组:backend/frontend/shared',
  `content` mediumtext COLLATE utf8mb4_general_ci COMMENT 'Freemarker 模板内容',
  `path_pattern` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '输出相对路径模式（支持 ${var} 占位）',
  `table_id` bigint NOT NULL DEFAULT '0' COMMENT '归属表:0全局模板 >0表级覆盖',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0停用 1启用',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号，每次保存+1',
  `remark` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_table` (`tpl_code`,`table_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-模板配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `gen_template_history`;
CREATE TABLE `gen_template_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL COMMENT 'gen_template.id',
  `tpl_code` varchar(50) NOT NULL,
  `table_id` bigint NOT NULL DEFAULT '0' COMMENT '0=全局模板',
  `version` int NOT NULL,
  `content` mediumtext NOT NULL,
  `path_pattern` varchar(255) NOT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tpl` (`template_id`,`version`),
  KEY `idx_code` (`tpl_code`,`table_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板版本快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `gen_output`;
CREATE TABLE `gen_output` (
  `id` bigint NOT NULL COMMENT '主键',
  `batch_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '写盘批次号（GEN+yyyyMMddHHmmss，三入口统一生成）',
  `table_id` bigint NOT NULL COMMENT '表配置ID（gen_table.id）',
  `table_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名',
  `file_group` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '产物分组:backend/frontend',
  `rel_path` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '相对路径',
  `abs_path` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '写盘绝对路径（SKIPPED/ERROR 可为空）',
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '动作:WRITTEN新建/OVERWRITTEN覆盖/MERGED合并块/SKIPPED跳过/ERROR失败',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件字节数（未写盘为 0）',
  `md5` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '写盘内容 MD5（未写盘为空串）',
  `message` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '说明（合并块/跳过/失败原因）',
  `operator` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '操作人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-写盘文件级台账';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `gen_history`;
CREATE TABLE `gen_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `batch_no` varchar(40) NOT NULL COMMENT '批次号（一次 apply 一个批次）',
  `table_name` varchar(100) NOT NULL COMMENT '表名',
  `file_path` varchar(500) NOT NULL COMMENT '落盘绝对路径',
  `backup_path` varchar(500) DEFAULT NULL COMMENT '覆盖前备份路径（文件原本不存在则为空）',
  `status` varchar(20) NOT NULL COMMENT 'WRITTEN/SKIPPED/ERROR',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_batch` (`batch_no`),
  KEY `idx_history_table` (`table_name`)
) ENGINE=InnoDB AUTO_INCREMENT=139 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码生成写盘历史';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父菜单ID',
  `name` varchar(100) NOT NULL COMMENT '菜单名称',
  `path` varchar(200) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(200) DEFAULT NULL COMMENT '前端组件路径（相对 src/views，如 demo_goods/index）',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识（如 demo_goods:add）',
  `type` tinyint NOT NULL COMMENT '1目录 2菜单 3按钮',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2112600000000000676 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL COMMENT '角色编码',
  `name` varchar(100) NOT NULL COMMENT '角色名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(128) NOT NULL COMMENT '密码（BCrypt）',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台用户';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典编码',
  `dict_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典名称',
  `status` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `dict_label` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `dict_value` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dictdata_code` (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

-- ============================ 种子数据 ============================

-- 管理员账号：admin / admin123
INSERT INTO `sys_user` (`id`,`username`,`password`,`nickname`,`email`,`phone`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1,'admin','$2a$10$nBtf8GMTgvqaZpSotLFpROi8.Z0LRUmFpHhYj9E2wcqUHvYGhVKhW','超级管理员','admin@demo.local','',1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `password`=VALUES(`password`), `nickname`=VALUES(`nickname`), `status`=1, `deleted`=0;

INSERT INTO `sys_role` (`id`,`code`,`name`,`status`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1,'admin','超级管理员',1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`), `name`=VALUES(`name`), `status`=1, `deleted`=0;

INSERT INTO `sys_user_role` (`id`,`user_id`,`role_id`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`) VALUES
(1,1,1,NOW(),NOW(),1,1,0)
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`), `deleted`=0;

-- 菜单：代码生成（4 页）+ 系统管理（3 页）
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

-- 超管授予全部菜单（实际鉴权对 admin 角色直接放行，这里保证菜单树可用）
INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`)
SELECT 9000 + m.`id`, 1, m.`id`, NOW(), NOW(), 1, 1, 0 FROM `sys_menu` m
ON DUPLICATE KEY UPDATE `deleted`=0;

SET FOREIGN_KEY_CHECKS = 1;
