-- 测试库结构：只建契约测试需要的 6 张表
-- 来源：gen_platform 的 SHOW CREATE TABLE（2026-10-22），去掉 AUTO_INCREMENT 值
-- 用法：application-test.yml 里 spring.sql.init 在启动时执行；
--       库名必须是 gen_platform_test（见 GenTestDatabaseGuard，防误连开发库）

DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table` (
  `id` bigint NOT NULL COMMENT '主键',
  `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名',
  `table_comment` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '表注释（功能名）',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实体类名（大驼峰）',
  `module_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '前端模块名（api/<模块>.js、views/<模块>/）',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'com.demo' COMMENT '生成 Java 包名',
  `backend_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '后端输出基准相对路径',
  `backend_root` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '后端仓库根绝对路径',
  `frontend_root` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '前端仓库根绝对路径',
  `gateway_prefix` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'demo' COMMENT '前端网关服务前缀',
  `author` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '作者',
  `target_module` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标模块key(product/order/...)，空=沿用当前配置',
  `gen_entity` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成entity',
  `gen_mapper` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成mapper',
  `gen_service` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成service+impl',
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
  `tree_parent_column` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '树形父级列(如parent_id)，非空即按树形生成',
  `tree_label_column` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '树节点显示列',
  `master_table_id` bigint DEFAULT NULL COMMENT '作为子表时所属主表的gen_table.id',
  `master_foreign_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表中指向主表主键的列',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '备注',
  `menu_id` bigint DEFAULT NULL COMMENT '已注册菜单ID(sys_menu.id)',
  `last_gen_time` datetime DEFAULT NULL COMMENT '最近一次写盘时间',
  `last_gen_by` bigint DEFAULT NULL COMMENT '最近一次写盘操作人',
  `delete_cascade_mode` tinyint DEFAULT NULL COMMENT '删除级联模式：NULL=未配置(回落系统默认) 0=阻止(有子行拒删) 1=级联(同删全部子表) 2=不处理(现状)',
  `gen_print` tinyint DEFAULT NULL COMMENT '打印单据:NULL=跟随系统默认 1=生成 0=不生成',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `master_depth` tinyint NOT NULL DEFAULT '0' COMMENT '主子表层级深度',
  `relation_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表关系类型：middle 中间表',
  `gen_log` tinyint NOT NULL DEFAULT '1' COMMENT '开关:生成操作日志@Log',
  `flow_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'P3-3 绑定的审批流程编码，空为不启用',
  `project_id` bigint DEFAULT NULL COMMENT '所属系统 sys_project.id，空表示沿用全局 yml 配置',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_table_name` (`table_name`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-表配置'
;

DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column` (
  `id` bigint NOT NULL COMMENT '主键',
  `table_id` bigint NOT NULL COMMENT '表配置ID（gen_table.id）',
  `column_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据库列名',
  `column_comment` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '列注释',
  `column_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据库类型',
  `java_field` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Java属性名（小驼峰）',
  `java_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Java类型',
  `is_pk` tinyint NOT NULL DEFAULT '0' COMMENT '是否主键:0否1是',
  `is_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填:0否1是',
  `is_query` tinyint NOT NULL DEFAULT '0' COMMENT '是否查询条件:0否1是',
  `query_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'EQ' COMMENT '查询方式:EQ/LIKE/GE/LE',
  `is_list` tinyint NOT NULL DEFAULT '1' COMMENT '列表是否显示:0否1是',
  `is_form` tinyint NOT NULL DEFAULT '1' COMMENT '表单是否录入:0否1是',
  `html_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'input' COMMENT '琛ㄥ崟鎺т欢:input/textarea/number/select/radio/switch/date/datetime',
  `dict_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '瀛楀吀缂栫爜(瀵瑰簲 sys_dict_type.dict_code)',
  `ref_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联下拉来源表',
  `ref_label` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联下拉显示列',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `validate_rule` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '鏍￠獙瑙勫垯:required,maxlen=50,min=1,max=9999,email,phone,url,regex=...',
  `excel_export` tinyint NOT NULL DEFAULT '1' COMMENT '是否导出Excel',
  `excel_import` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许导入',
  `mask_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏类型',
  `unique_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否参与唯一性校验:0否1是',
  `link_rule` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表单联动规则 JSON',
  `data_scope` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据权限标记',
  `relation_key` tinyint NOT NULL DEFAULT '0' COMMENT '是否中间表外键列:0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-字段配置'
;

DROP TABLE IF EXISTS `gen_output`;
CREATE TABLE `gen_output` (
  `id` bigint NOT NULL COMMENT '主键',
  `batch_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '写盘批次号（GEN+yyyyMMddHHmmss，三入口统一生成）',
  `table_id` bigint NOT NULL COMMENT '表配置ID（gen_table.id）',
  `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表名',
  `file_group` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '产物分组:backend/frontend',
  `rel_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '相对路径',
  `abs_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '写盘绝对路径（SKIPPED/ERROR 可为空）',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '动作:WRITTEN新建/OVERWRITTEN覆盖/MERGED合并块/SKIPPED跳过/ERROR失败',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件字节数（未写盘为 0）',
  `md5` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '写盘内容 MD5（未写盘为空串）',
  `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '说明（合并块/跳过/失败原因）',
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '操作人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `project_id` bigint DEFAULT NULL COMMENT '生成时所属系统',
  `backup_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '覆盖前原文件备份路径',
  `prev_md5` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '覆盖前原文件MD5，供漂移判断',
  PRIMARY KEY (`id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码生成器-写盘文件级台账'
;

DROP TABLE IF EXISTS `gen_template`;
CREATE TABLE `gen_template` (
  `id` bigint NOT NULL COMMENT '主键',
  `tpl_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码:entity/mapper/service/serviceImpl/pageDto/saveDto/vo/controller/apiJs/vuePage/routerSnippet/readme',
  `tpl_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '模板名称',
  `file_group` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '产物分组:backend/frontend/shared',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'Freemarker 模板内容',
  `path_pattern` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '输出相对路径模式（支持 ${var} 占位）',
  `table_id` bigint NOT NULL DEFAULT '0' COMMENT '归属表:0全局模板 >0表级覆盖',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0停用 1启用',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号，每次保存+1',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_table` (`tpl_code`,`table_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成器-模板配置'
;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码生成写盘历史'
;

DROP TABLE IF EXISTS `sys_project`;
CREATE TABLE `sys_project` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_code` varchar(64) NOT NULL COMMENT '系统编码（英文小写，生成物与字典前缀用）',
  `project_name` varchar(64) NOT NULL COMMENT '系统名称',
  `backend_root` varchar(255) DEFAULT NULL COMMENT '后端工程根绝对路径',
  `frontend_root` varchar(255) DEFAULT NULL COMMENT '前端工程根绝对路径',
  `backend_path` varchar(255) DEFAULT NULL COMMENT '后端相对路径（如 demo-server/src/main/java/com/demo）',
  `frontend_path` varchar(255) DEFAULT NULL COMMENT '前端相对路径（如 demo-web）',
  `root_package` varchar(128) DEFAULT NULL COMMENT '根包名（如 com.demo）',
  `gateway_prefix` varchar(64) DEFAULT NULL COMMENT '网关/接口前缀（如 demo）',
  `selfcheck_classpath` varchar(512) DEFAULT NULL COMMENT '该系统的依赖 classpath（支持 dir/* 通配符）',
  `project_structure` varchar(20) DEFAULT NULL COMMENT '工程结构（standalone/container，V5.0）',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '1 为默认系统，新建表时预选',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1 启用 0 停用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `default_gen_entity` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_mapper` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_service` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_log` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_controller` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_page_api` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_detail_api` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_add_api` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_edit_api` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_delete_api` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_frontend` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_api_js` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_vue_page` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_query_form` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_add_btn` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_edit_btn` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_gen_delete_btn` tinyint DEFAULT NULL COMMENT '生成开关默认值：NULL 表示未配置，回落 1',
  `default_delete_cascade_mode` tinyint DEFAULT NULL COMMENT '系统级默认删除级联模式，空=1(级联)',
  `default_gen_print` tinyint DEFAULT NULL COMMENT '打印单据默认:NULL=未配置(关) 1=默认生成 0=默认不生成',
  `flow_engine` varchar(16) DEFAULT NULL COMMENT '产物流程引擎：null/builtin=内置简易流，activiti=Activiti 8 引擎',
  `default_gen_deploy` tinyint DEFAULT NULL COMMENT '部署物默认:NULL=未配置(关) 1=生成 0=不生成',
  `pay_enabled` tinyint DEFAULT NULL COMMENT '支付能力包:NULL=未配置(关) 1=生成 0=不生成',
  `mobile_enabled` tinyint DEFAULT NULL COMMENT '移动端工程:NULL=未配置(关) 1=生成 uni-app 单源 H5+微信小程序',
  `file_enabled` tinyint DEFAULT NULL COMMENT '文件能力包:NULL=未配置(关) 1=生成上传下载基座 0=不生成',
  `test_enabled` tinyint DEFAULT NULL COMMENT '测试骨架:NULL=未配置(开) 1=生成集成测试骨架 0=不生成',
  `app_name` varchar(64) DEFAULT NULL COMMENT '应用显示名(空=项目名)',
  `app_logo` varchar(255) DEFAULT NULL COMMENT 'Logo图片URL(空=内置logo)',
  `app_theme_color` varchar(16) DEFAULT NULL COMMENT '主题主色(空=#e1251b)',
  `default_language` varchar(32) DEFAULT NULL COMMENT '默认语言BCP47标签(空=zh-CN)',
  `supported_languages` text DEFAULT NULL COMMENT '启用语言码,逗号分隔(空=全部启用)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`,`deleted`),
  KEY `idx_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统台账（多系统输出的根配置）'
;

-- V7.0 起新增：流水线主表（预览/应用契约需要 gen_pipeline 行）
DROP TABLE IF EXISTS `gen_pipeline`;
CREATE TABLE `gen_pipeline` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `pipeline_no` varchar(32) NOT NULL COMMENT '流水线号 PPL+时间戳',
  `project_id` bigint DEFAULT NULL COMMENT '关联 sys_project',
  `system_name` varchar(64) NOT NULL COMMENT '录入的系统名称',
  `system_desc` varchar(512) DEFAULT NULL COMMENT '一句话业务描述',
  `db_name` varchar(64) NOT NULL COMMENT '目标数据库名',
  `backend_port` int DEFAULT NULL,
  `frontend_port` int DEFAULT NULL,
  `mode` varchar(16) NOT NULL DEFAULT 'ai',
  `seed_mode` varchar(10) DEFAULT NULL COMMENT '数据初始化：rule/ai/off，NULL=off',
  `status` varchar(16) NOT NULL DEFAULT 'draft',
  `current_step` varchar(32) DEFAULT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `frontend_url` varchar(256) DEFAULT NULL,
  `backend_pid` bigint DEFAULT NULL,
  `frontend_pid` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`pipeline_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='V5.0 一句话建系统流水线'
;

-- V7.4 起补齐：操作日志表 + 流水线步骤明细
-- 原因：此前测试库快照只有 7 张表，没有这两张，导致 240 例测试全绿也证明不了
--       docs/sql/ 全量脚本在空库能跑通（坑库 mysql.md:181 的同款教训）。
-- 结构必须与 docs/sql/V2.0__quick_build.sql（已补 4 审计列）和 V5.0__pipeline.sql 一致。
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(64) NOT NULL COMMENT '模块标题',
  `oper_type` varchar(16) NOT NULL COMMENT 'INSERT/UPDATE/DELETE/IMPORT/EXPORT/OTHER',
  `method` varchar(200) DEFAULT NULL,
  `request_uri` varchar(255) DEFAULT NULL,
  `request_method` varchar(10) DEFAULT NULL,
  `oper_param` text,
  `json_result` text,
  `status` tinyint NOT NULL DEFAULT '1',
  `error_msg` text,
  `oper_user_id` bigint DEFAULT NULL,
  `oper_name` varchar(64) DEFAULT NULL,
  `oper_ip` varchar(64) DEFAULT NULL,
  `cost_ms` bigint DEFAULT NULL,
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_oper_user` (`oper_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志'
;

DROP TABLE IF EXISTS `gen_pipeline_step`;
CREATE TABLE `gen_pipeline_step` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `pipeline_id` bigint NOT NULL COMMENT 'gen_pipeline.id',
  `step_key` varchar(32) NOT NULL COMMENT '步骤key',
  `step_name` varchar(64) NOT NULL COMMENT '步骤中文名',
  `step_order` int NOT NULL COMMENT '执行顺序',
  `side` varchar(16) NOT NULL DEFAULT 'pipeline',
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `message` varchar(1024) DEFAULT NULL,
  `log_excerpt` text,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pipe_step` (`pipeline_id`, `step_key`),
  KEY `idx_pipeline` (`pipeline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V5.0 流水线步骤明细'
;

-- V7.5 项目语言配置：测试库镜像表（运行时不查此表，但保证空库按序执行全量脚本后结构一致）
DROP TABLE IF EXISTS `sys_language`;
CREATE TABLE `sys_language` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `lang_code` varchar(32) NOT NULL COMMENT '语言码(ISO 639-1 或 BCP47 变体)',
  `iso639_2` varchar(8) DEFAULT NULL COMMENT 'ISO 639-2 三字母码',
  `locale_tag` varchar(32) NOT NULL COMMENT '默认 BCP47 标签',
  `name_en` varchar(128) NOT NULL COMMENT '英文名',
  `name_zh` varchar(128) NOT NULL COMMENT '简体中文名',
  `name_native` varchar(128) NOT NULL COMMENT '本地语言自称',
  `region` varchar(16) DEFAULT NULL COMMENT '默认地区码',
  `script` varchar(16) NOT NULL DEFAULT 'Latn' COMMENT '文字体系(ISO 15924)',
  `dir` varchar(8) NOT NULL DEFAULT 'ltr' COMMENT '书写方向 ltr/rtl',
  `readme_file` varchar(64) DEFAULT NULL COMMENT '对应 README 语言版文件名',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lang_code` (`lang_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目语言配置表'
;
