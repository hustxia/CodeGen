-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V4.2__unify_gen_ledger.sql
-- 转换记录:
  [丢弃] CONVERT TO CHARACTER SET（字符集统一，demo 不需要）
  [丢弃] 多表 UPDATE ... JOIN（H2 不支持；demo 空库无存量数据）
  [存储过程] 提取 ADD COLUMN -> gen_output `backup_path`
  [存储过程] 提取 ADD COLUMN -> gen_output `prev_md5`

ALTER TABLE `gen_output` ADD COLUMN IF NOT EXISTS `backup_path` varchar(500) DEFAULT NULL;

COMMENT ON COLUMN `gen_output`.`backup_path` IS '覆盖前原文件备份路径';

ALTER TABLE `gen_output` ADD COLUMN IF NOT EXISTS `prev_md5` varchar(32) DEFAULT NULL;

COMMENT ON COLUMN `gen_output`.`prev_md5` IS '覆盖前原文件MD5，供漂移判断';
