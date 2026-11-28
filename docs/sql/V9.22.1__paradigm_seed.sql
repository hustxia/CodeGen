-- V9.22.1 四包范式元数据种子（幂等：INSERT ... ON DUPLICATE KEY 不覆盖已有）
-- 说明：村务/种植复用已有行业包(village_affairs/agr_crop)，城管/园区为V9.22新增(cm_city/park_park)
INSERT INTO gen_industry_paradigm
    (id, pack_key, industry, category, version, hit_rate, direct_tables, external_systems, dedup_note, boundary_note, core_chain, sort_no, enabled, create_time)
VALUES
(9902201, 'village_affairs', '村务治理', '政务民生', '1.0', 92, 85, 0, '村务12域61页合并为5模块；通用15表一次建成', '不接GIS平台/视频算法/涉密系统', '基础数据→党建治理→业务监管→决策服务', 1, 1, NOW()),
(9902202, 'cm_city', '城市管理', '城市管理', '1.0', 88, 23, 4, '15系统=1事件中心+4监管对象+1考核引擎；底座29表先行', '渣土车GPS/灯杆单灯控制/SCADA/视频会商必须外接', '发现→立案→派遣→处置→核查→结案→考核', 2, 1, NOW()),
(9902203, 'agr_crop', '智慧种植', '农业环保', '1.0', 90, 35, 1, '农事作业引擎1张主表承接施肥/灌溉/打药，不拆8子表', '水肥一体化控制权在设备厂商；病害AI识别人工确认', '地块→种植季→农事作业→病虫害预警→灾情评估', 3, 1, NOW()),
(9902204, 'park_park', '智慧园区', '智慧园区', '1.0', 85, 22, 14, '55项去重：智能感知=物联平台、4驾驶舱=1引擎4视图', '消防联动由消防主机做；人脸敏感信息单独同意', '统一身份+空间主数据+工单引擎→IoT集成→招商运营→驾驶舱', 4, 1, NOW())
ON DUPLICATE KEY UPDATE id = id;

-- 演示安装记录：项目1 装村务范式（只生成plan，不建库）
INSERT INTO gen_industry_install
    (id, project_id, pack_key, pack_version, scope, module_names, table_count, plan_summary, status, install_time, create_time)
SELECT 9902301, 1, 'village_affairs', '1.0', 'new',
       JSON_ARRAY('基础数据底座','党建与治理','业务监管','决策服务','平台通用能力'), 85,
       JSON_OBJECT('moduleCount', 5, 'tableCount', 85, 'excludedCount', 0, 'source', 'template'),
       'INSTALLED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_industry_install WHERE id = 9902301);
