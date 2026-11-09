-- ---------- V4.3 操作日志记录省份 / 城市 ----------
-- 背景：审计时只看 oper_ip 无法判断操作来源地域，需要按 IP 反查省、市并落库。
-- 解析在 IpRegionResolver 里做：内网/回环直接给「内网」「本机」，公网走可配置接口并带 24h 缓存。
-- 幂等写法：字段已存在时跳过，可重复执行。

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_oper_log ADD COLUMN province varchar(32) NULL COMMENT ''省份（IP 解析）'' AFTER oper_ip',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'province');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_oper_log ADD COLUMN city varchar(32) NULL COMMENT ''城市（IP 解析）'' AFTER province',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'city');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
