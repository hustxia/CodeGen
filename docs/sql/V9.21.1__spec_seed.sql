-- V9.21.1 种子数据：SDD 规格工件链示例（挂项目1 默认系统，村务事件模块场景）
-- 幂等：固定 id 段 9901xx，先删后插；可重复执行
DELETE FROM gen_converge_item   WHERE report_id IN (9901001, 9901002);
DELETE FROM gen_converge_report WHERE id        IN (9901001, 9901002);
DELETE FROM gen_spec_artifact   WHERE id BETWEEN 9901010 AND 9901099;

INSERT INTO gen_spec_artifact (id, project_id, artifact_type, version, title, yaml_meta, content_md, structured_json, status, create_time, deleted) VALUES
(9901011, 1, 'CONSTITUTION', 1, '项目基本法 v1',
 '{"owner":"admin"}',
 '# 项目基本法\n\n本条约束本项目全部规格与生成行为：\n\n1. 枚举列一律 tinyint + 注释「名:值」格式，前后端三处同步\n2. 主键雪花 ID 下发必须字符串化，防 JS 53 位精度截断\n3. 表名/列名规避 MySQL 8 保留字（如 desc、order）\n4. 轨迹类高频数据不进业务库，落位置服务\n5. 生成前 spec 必须确认（CONFIRMED），对账 CONVERGED 才进验收',
 NULL, 'CONFIRMED', '2026-11-27 09:30:00', 0),
(9901012, 1, 'SPEC', 3, '村务事件模块规格 v3',
 '{"domain":"village-governance","module":"event","guardrailPassed":true,"nonGoals":["GIS地图","视频融合"]}',
 '# 村务事件模块规格 v3\n\n## 一、范围\n村民事件上报、网格派单、办结回访。不含 GIS 展示与视频接入（nonGoals）。\n\n## 二、数据模型\n- 事件表 `cm_event`：标题、类型枚举、紧急程度枚举、状态、网格 id、上报人\n- 处理记录表 `cm_event_record`：事件 id、处理意见、处理人、时间\n\n## 三、枚举\n- 事件状态 `cg_event_status`\n- 紧急程度 `cg_urgent_level`\n\n## 四、验收口径\n对账 CONVERGED 后进入截图验收。',
 '{"tables":[{"name":"cm_event","fields":["event_title","event_type","urgent_level","event_status","grid_id","report_user"]},{"name":"cm_event_record","fields":["event_id","record_content","deal_user","deal_time"]}],"enums":["cg_event_status","cg_urgent_level"]}',
 'CONFIRMED', '2026-11-27 10:15:00', 0),
(9901013, 1, 'SPEC', 4, '村务事件模块规格 v4（草稿）',
 '{"domain":"village-governance","module":"event","guardrailPassed":false}',
 '# 村务事件模块规格 v4（草稿）\n\n在 v3 基础上追加：积分激励（上报人加积分，对接志愿商城 pt_account）。\n\n待评审：积分规则与幂等口径。',
 '{"tables":[{"name":"cm_event","fields":["event_title","event_type","urgent_level","event_status","grid_id","report_user","reward_points"]}],"enums":["cg_event_status","cg_urgent_level"]}',
 'DRAFT', '2026-11-27 16:40:00', 0),
(9901014, 1, 'PLAN', 1, '村务事件技术方案 v1',
 '{"template":"v9.4"}',
 '# 技术方案\n\n- 平台：Spring Boot 3.2 单体，MyBatis-Plus\n- 前端：Vue2.7 + Element UI\n- 生成模板：V9.4 四步向导 · 完整系统模式\n- 后处理：枚举 2 处、关联 1 处',
 NULL, 'CONFIRMED', '2026-11-27 10:20:00', 0),
(9901015, 1, 'TABLES', 1, '表设计清单 v1',
 NULL,
 '# 表设计\n\n两张业务表（cm_event / cm_event_record）+ 通用 15 表。DDL 走四步向导 Step2 同源预览。',
 '{"tables":[{"name":"cm_event"},{"name":"cm_event_record"}]}',
 'CONFIRMED', '2026-11-27 10:25:00', 0),
(9901016, 1, 'TASKS', 2, '生成任务清单 v2',
 NULL,
 '# 任务\n\n1. Step1 AI 模块推荐（已完成）\n2. Step2 建模 + DDL（已完成）\n3. 流水线 model→health 12 步（进行中）\n4. 对账 + 截图验收（待办）',
 NULL, 'DRAFT', '2026-11-27 17:00:00', 0);

INSERT INTO gen_converge_report (id, project_id, artifact_id, spec_version, ok_count, missing_count, extra_count, status, create_time, deleted) VALUES
(9901001, 1, 9901012, 3, 41, 2, 1, 'UNCONVERGED', '2026-11-27 11:00:00', 0),
(9901002, 1, 9901012, 3, 44, 0, 0, 'CONVERGED',  '2026-11-27 15:30:00', 0);

INSERT INTO gen_converge_item (id, report_id, item_type, item_key, kind, source_ref, suggestion, deleted) VALUES
(9902001, 9901001, 'TABLE', 'cm_event',        'OK',      'spec v3 §二', '已导入并生成', 0),
(9902002, 9901001, 'TABLE', 'cm_event_record', 'OK',      'spec v3 §二', '已导入并生成', 0),
(9902003, 9901001, 'TABLE', 'cm_worker',       'MISSING', 'spec v3 §二', '表缺失：导入结构或补任务重跑', 0),
(9902004, 9901001, 'TABLE', 'cm_demo_old',     'EXTRA',   '实际产物',     '规格外整表：确认后从产物侧下线', 0),
(9902005, 9901001, 'FIELD', 'cm_event.grid_id','MISSING', 'spec v3 §二', '字段缺失：同步列结构后重跑流水线', 0),
(9902006, 9901001, 'FIELD', 'cm_event.report_user', 'OK', 'spec v3 §二', '已实现', 0),
(9902007, 9901001, 'FIELD', 'cm_event_record.deal_user', 'OK', 'spec v3 §二', '已实现', 0),
(9902008, 9901001, 'ENUM',  'cg_event_status', 'OK',      'spec v3 §三', '字典项 6 条齐备', 0),
(9902009, 9901001, 'ENUM',  'cg_urgent_level', 'MISSING', 'spec v3 §三', '字典缺失：建立字典项后重跑', 0),
(9902010, 9901001, 'FIELD', 'cm_event.urgent_level', 'OK', 'spec v3 §二', '已实现', 0);
