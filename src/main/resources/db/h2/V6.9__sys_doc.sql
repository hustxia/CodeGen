-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.9__sys_doc.sql
-- 转换记录:

CREATE TABLE IF NOT EXISTS `sys_doc` (
  `id` bigint NOT NULL,
  `original_name` varchar(255) NOT NULL,
  `storage_name` varchar(128) NOT NULL,
  `relative_path` varchar(255) NOT NULL,
  `ext` varchar(20) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT 0,
  `md5` char(32) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_relative_path` ON `sys_doc` (`relative_path`);

CREATE INDEX IF NOT EXISTS `idx_ext` ON `sys_doc` (`ext`);

COMMENT ON TABLE `sys_doc` IS '平台文档（V6.9 文档中心）';

COMMENT ON COLUMN `sys_doc`.`id` IS '文档ID';

COMMENT ON COLUMN `sys_doc`.`original_name` IS '原始文件名（含扩展名）';

COMMENT ON COLUMN `sys_doc`.`storage_name` IS '落盘文件名（uuid.ext）';

COMMENT ON COLUMN `sys_doc`.`relative_path` IS '相对 upload-dir 的路径（yyyyMM/uuid.ext）';

COMMENT ON COLUMN `sys_doc`.`ext` IS '小写扩展名（不带点，与白名单同口径）';

COMMENT ON COLUMN `sys_doc`.`content_type` IS 'MIME类型';

COMMENT ON COLUMN `sys_doc`.`file_size` IS '字节数';

COMMENT ON COLUMN `sys_doc`.`md5` IS 'MD5（预留去重/缓存键）';

COMMENT ON COLUMN `sys_doc`.`remark` IS '备注';

COMMENT ON COLUMN `sys_doc`.`deleted` IS '逻辑删除';

COMMENT ON COLUMN `sys_doc`.`create_by` IS '上传人';

COMMENT ON COLUMN `sys_doc`.`create_time` IS '上传时间';

COMMENT ON COLUMN `sys_doc`.`update_by` IS '更新人';

COMMENT ON COLUMN `sys_doc`.`update_time` IS '更新时间';
