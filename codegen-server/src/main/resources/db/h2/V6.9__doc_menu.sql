-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.9__doc_menu.sql
-- 转换记录:

DELETE FROM sys_menu WHERE path = '/doc';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES
  (3000, 0,    '文档中心', '/doc',        'doc/index',   'doc:list',   2, 3, 'documentation', 1, 0),
  (3010, 3000, '上传文档', NULL,          NULL,          'doc:upload', 3, 1, NULL,            1, 0),
  (3020, 3000, '删除文档', NULL,          NULL,          'doc:remove', 3, 2, NULL,            1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3000), (1, 3010), (1, 3020);
