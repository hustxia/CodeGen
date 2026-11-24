-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V4.4__delete_cascade_mode.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS gen_table delete_cascade_mode
  [丢弃] 未识别语句: SET @cleanup := IF(@was_not_null = 1, 'UPDATE gen_table SET ...
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_project default_delete_cascade_mode
  [丢弃] 纯 SELECT 诊断语句

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS delete_cascade_mode TINYINT NULL DEFAULT NULL;

COMMENT ON COLUMN `gen_table`.`delete_cascade_mode` IS '删除级联模式：NULL=未配置(回落系统默认) 0=阻止(有子行拒删) 1=级联(同删全部子表) 2=不处理(现状)';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS default_delete_cascade_mode TINYINT NULL;

COMMENT ON COLUMN `sys_project`.`default_delete_cascade_mode` IS '系统级默认删除级联模式，空=1(级联)';

UPDATE gen_template
SET status = 0,
    remark = '全局种子模板（V4.4 分道后停用，由 serviceImplSimple/Tree/Masterdetail 接替）'
WHERE tpl_code = 'serviceImpl' AND table_id = 0 AND status = 1
  AND remark LIKE '全局种子模板%';
