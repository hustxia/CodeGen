-- 智慧村务管理系统 初始化种子数据
-- 由 scripts/village_seed_data.py 生成，可重复执行前请先清空业务表
SET NAMES utf8mb4;

-- ===== 系统管理 =====
INSERT INTO `sys_role` (`id`, `role_name`, `role_key`, `remark`, `deleted`) VALUES
(2, '村党支部书记', 'village_party_secretary', '村务最高业务审批', 0),
(3, '村委会主任', 'village_director', '村务经办与执行', 0),
(4, '网格员', 'grid_member', '网格事件上报与处置', 0),
(5, '便民代办员', 'service_agent', '便民事项受理', 0);

INSERT INTO `sys_user` (`id`, `username`, `password`, `password_algo`, `nickname`, `deleted`) VALUES
(2, 'wangshuji', '$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO', 'bcrypt', '王书记', 0),
(3, 'licunzhang', '$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO', 'bcrypt', '李村长', 0),
(4, 'wanggeyuan', '$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO', 'bcrypt', '张网格员', 0),
(5, 'daiban', '$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO', 'bcrypt', '赵代办员', 0);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(2, 2),
(3, 3),
(4, 4),
(5, 5);

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `perms`, `type`, `sort`, `status`, `deleted`) VALUES
(1, 0, '智慧村务管理系统', '/village_affairs', NULL, NULL, 1, 1, 1, 0),
(2, 1, '基础数据', '/village_affairs/base', NULL, NULL, 1, 1, 1, 0),
(3, 2, 'village_area', '/village_affairs/base/village_area/list', NULL, 'village_affairs:village_area:list', 2, 1, 1, 0),
(4, 2, 'village_household', '/village_affairs/base/village_household/list', NULL, 'village_affairs:village_household:list', 2, 2, 1, 0),
(5, 2, 'village_resident', '/village_affairs/base/village_resident/list', NULL, 'village_affairs:village_resident:list', 2, 3, 1, 0),
(6, 2, 'village_resident_relation', '/village_affairs/base/village_resident_relation/list', NULL, 'village_affairs:village_resident_relation:list', 2, 4, 1, 0),
(7, 2, 'village_population_change', '/village_affairs/base/village_population_change/list', NULL, 'village_affairs:village_population_change:list', 2, 5, 1, 0),
(8, 2, 'village_special_group', '/village_affairs/base/village_special_group/list', NULL, 'village_affairs:village_special_group:list', 2, 6, 1, 0),
(9, 2, 'village_enterprise', '/village_affairs/base/village_enterprise/list', NULL, 'village_affairs:village_enterprise:list', 2, 7, 1, 0),
(10, 1, '房屋土地', '/village_affairs/land', NULL, NULL, 1, 2, 1, 0),
(11, 10, 'village_house', '/village_affairs/land/village_house/list', NULL, 'village_affairs:village_house:list', 2, 1, 1, 0),
(12, 10, 'village_land_parcel', '/village_affairs/land/village_land_parcel/list', NULL, 'village_affairs:village_land_parcel:list', 2, 2, 1, 0),
(13, 10, 'village_land_contract', '/village_affairs/land/village_land_contract/list', NULL, 'village_affairs:village_land_contract:list', 2, 3, 1, 0),
(14, 10, 'village_land_transfer', '/village_affairs/land/village_land_transfer/list', NULL, 'village_affairs:village_land_transfer:list', 2, 4, 1, 0),
(15, 1, '网格档案', '/village_affairs/grid', NULL, NULL, 1, 3, 1, 0),
(16, 15, 'village_grid', '/village_affairs/grid/village_grid/list', NULL, 'village_affairs:village_grid:list', 2, 1, 1, 0),
(17, 15, 'village_grid_member', '/village_affairs/grid/village_grid_member/list', NULL, 'village_affairs:village_grid_member:list', 2, 2, 1, 0),
(18, 15, 'village_grid_event', '/village_affairs/grid/village_grid_event/list', NULL, 'village_affairs:village_grid_event:list', 2, 3, 1, 0),
(19, 15, 'village_archive', '/village_affairs/grid/village_archive/list', NULL, 'village_affairs:village_archive:list', 2, 4, 1, 0),
(20, 15, 'village_archive_file', '/village_affairs/grid/village_archive_file/list', NULL, 'village_affairs:village_archive_file:list', 2, 5, 1, 0),
(21, 15, 'village_archive_borrow', '/village_affairs/grid/village_archive_borrow/list', NULL, 'village_affairs:village_archive_borrow:list', 2, 6, 1, 0),
(22, 1, '党建管理', '/village_affairs/party', NULL, NULL, 1, 4, 1, 0),
(23, 22, 'village_party_org', '/village_affairs/party/village_party_org/list', NULL, 'village_affairs:village_party_org:list', 2, 1, 1, 0),
(24, 22, 'village_party_member', '/village_affairs/party/village_party_member/list', NULL, 'village_affairs:village_party_member:list', 2, 2, 1, 0),
(25, 22, 'village_party_member_change', '/village_affairs/party/village_party_member_change/list', NULL, 'village_affairs:village_party_member_change:list', 2, 3, 1, 0),
(26, 22, 'village_party_meeting', '/village_affairs/party/village_party_meeting/list', NULL, 'village_affairs:village_party_meeting:list', 2, 4, 1, 0),
(27, 22, 'village_party_activity', '/village_affairs/party/village_party_activity/list', NULL, 'village_affairs:village_party_activity:list', 2, 5, 1, 0),
(28, 22, 'village_party_learning', '/village_affairs/party/village_party_learning/list', NULL, 'village_affairs:village_party_learning:list', 2, 6, 1, 0),
(29, 22, 'village_party_fee', '/village_affairs/party/village_party_fee/list', NULL, 'village_affairs:village_party_fee:list', 2, 7, 1, 0),
(30, 22, 'village_party_assess', '/village_affairs/party/village_party_assess/list', NULL, 'village_affairs:village_party_assess:list', 2, 8, 1, 0),
(31, 22, 'village_party_volunteer', '/village_affairs/party/village_party_volunteer/list', NULL, 'village_affairs:village_party_volunteer:list', 2, 9, 1, 0),
(32, 1, '村务公开', '/village_affairs/openness', NULL, NULL, 1, 5, 1, 0),
(33, 32, 'village_disclosure', '/village_affairs/openness/village_disclosure/list', NULL, 'village_affairs:village_disclosure:list', 2, 1, 1, 0),
(34, 32, 'village_disclosure_file', '/village_affairs/openness/village_disclosure_file/list', NULL, 'village_affairs:village_disclosure_file:list', 2, 2, 1, 0),
(35, 32, 'village_disclosure_feedback', '/village_affairs/openness/village_disclosure_feedback/list', NULL, 'village_affairs:village_disclosure_feedback:list', 2, 3, 1, 0),
(36, 32, 'village_meeting', '/village_affairs/openness/village_meeting/list', NULL, 'village_affairs:village_meeting:list', 2, 4, 1, 0),
(37, 32, 'village_motion', '/village_affairs/openness/village_motion/list', NULL, 'village_affairs:village_motion:list', 2, 5, 1, 0),
(38, 32, 'village_vote', '/village_affairs/openness/village_vote/list', NULL, 'village_affairs:village_vote:list', 2, 6, 1, 0),
(39, 32, 'village_vote_record', '/village_affairs/openness/village_vote_record/list', NULL, 'village_affairs:village_vote_record:list', 2, 7, 1, 0),
(40, 1, '矛盾调解', '/village_affairs/dispute', NULL, NULL, 1, 6, 1, 0),
(41, 40, 'village_dispute', '/village_affairs/dispute/village_dispute/list', NULL, 'village_affairs:village_dispute:list', 2, 1, 1, 0),
(42, 40, 'village_dispute_mediator', '/village_affairs/dispute/village_dispute_mediator/list', NULL, 'village_affairs:village_dispute_mediator:list', 2, 2, 1, 0),
(43, 40, 'village_dispute_record', '/village_affairs/dispute/village_dispute_record/list', NULL, 'village_affairs:village_dispute_record:list', 2, 3, 1, 0),
(44, 40, 'village_dispute_visit', '/village_affairs/dispute/village_dispute_visit/list', NULL, 'village_affairs:village_dispute_visit:list', 2, 4, 1, 0),
(45, 1, '三资管理', '/village_affairs/assets', NULL, NULL, 1, 7, 1, 0),
(46, 45, 'village_asset', '/village_affairs/assets/village_asset/list', NULL, 'village_affairs:village_asset:list', 2, 1, 1, 0),
(47, 45, 'village_asset_change', '/village_affairs/assets/village_asset_change/list', NULL, 'village_affairs:village_asset_change:list', 2, 2, 1, 0),
(48, 45, 'village_asset_inventory', '/village_affairs/assets/village_asset_inventory/list', NULL, 'village_affairs:village_asset_inventory:list', 2, 3, 1, 0),
(49, 45, 'village_resource', '/village_affairs/assets/village_resource/list', NULL, 'village_affairs:village_resource:list', 2, 4, 1, 0),
(50, 45, 'village_contract', '/village_affairs/assets/village_contract/list', NULL, 'village_affairs:village_contract:list', 2, 5, 1, 0),
(51, 45, 'village_contract_payment', '/village_affairs/assets/village_contract_payment/list', NULL, 'village_affairs:village_contract_payment:list', 2, 6, 1, 0),
(52, 45, 'village_fund_account', '/village_affairs/assets/village_fund_account/list', NULL, 'village_affairs:village_fund_account:list', 2, 7, 1, 0),
(53, 45, 'village_fund_transaction', '/village_affairs/assets/village_fund_transaction/list', NULL, 'village_affairs:village_fund_transaction:list', 2, 8, 1, 0),
(54, 45, 'village_fund_budget', '/village_affairs/assets/village_fund_budget/list', NULL, 'village_affairs:village_fund_budget:list', 2, 9, 1, 0),
(55, 1, '便民服务', '/village_affairs/service', NULL, NULL, 1, 8, 1, 0),
(56, 55, 'village_service_item', '/village_affairs/service/village_service_item/list', NULL, 'village_affairs:village_service_item:list', 2, 1, 1, 0),
(57, 55, 'village_service_apply', '/village_affairs/service/village_service_apply/list', NULL, 'village_affairs:village_service_apply:list', 2, 2, 1, 0),
(58, 55, 'village_service_material', '/village_affairs/service/village_service_material/list', NULL, 'village_affairs:village_service_material:list', 2, 3, 1, 0),
(59, 55, 'village_service_flow', '/village_affairs/service/village_service_flow/list', NULL, 'village_affairs:village_service_flow:list', 2, 4, 1, 0),
(60, 55, 'village_service_evaluate', '/village_affairs/service/village_service_evaluate/list', NULL, 'village_affairs:village_service_evaluate:list', 2, 5, 1, 0),
(61, 55, 'village_service_agent', '/village_affairs/service/village_service_agent/list', NULL, 'village_affairs:village_service_agent:list', 2, 6, 1, 0),
(62, 1, '人居环境', '/village_affairs/env', NULL, NULL, 1, 9, 1, 0),
(63, 62, 'village_env_inspect', '/village_affairs/env/village_env_inspect/list', NULL, 'village_affairs:village_env_inspect:list', 2, 1, 1, 0),
(64, 62, 'village_env_issue', '/village_affairs/env/village_env_issue/list', NULL, 'village_affairs:village_env_issue:list', 2, 2, 1, 0),
(65, 62, 'village_env_clean_task', '/village_affairs/env/village_env_clean_task/list', NULL, 'village_affairs:village_env_clean_task:list', 2, 3, 1, 0),
(66, 62, 'village_env_facility', '/village_affairs/env/village_env_facility/list', NULL, 'village_affairs:village_env_facility:list', 2, 4, 1, 0),
(67, 1, '任务督办', '/village_affairs/supervise', NULL, NULL, 1, 10, 1, 0),
(68, 67, 'village_task', '/village_affairs/supervise/village_task/list', NULL, 'village_affairs:village_task:list', 2, 1, 1, 0),
(69, 67, 'village_task_assign', '/village_affairs/supervise/village_task_assign/list', NULL, 'village_affairs:village_task_assign:list', 2, 2, 1, 0),
(70, 67, 'village_task_feedback', '/village_affairs/supervise/village_task_feedback/list', NULL, 'village_affairs:village_task_feedback:list', 2, 3, 1, 0),
(71, 67, 'village_task_score', '/village_affairs/supervise/village_task_score/list', NULL, 'village_affairs:village_task_score:list', 2, 4, 1, 0),
(72, 1, '决策分析', '/village_affairs/decision', NULL, NULL, 1, 11, 1, 0),
(73, 72, 'village_dashboard_metric', '/village_affairs/decision/village_dashboard_metric/list', NULL, 'village_affairs:village_dashboard_metric:list', 2, 1, 1, 0),
(74, 72, 'village_dashboard_snapshot', '/village_affairs/decision/village_dashboard_snapshot/list', NULL, 'village_affairs:village_dashboard_snapshot:list', 2, 2, 1, 0),
(75, 72, 'village_mood_log', '/village_affairs/decision/village_mood_log/list', NULL, 'village_affairs:village_mood_log:list', 2, 3, 1, 0),
(76, 72, 'village_mood_visit', '/village_affairs/decision/village_mood_visit/list', NULL, 'village_affairs:village_mood_visit:list', 2, 4, 1, 0),
(77, 72, 'village_mood_tag', '/village_affairs/decision/village_mood_tag/list', NULL, 'village_affairs:village_mood_tag:list', 2, 5, 1, 0),
(78, 72, 'village_assess_plan', '/village_affairs/decision/village_assess_plan/list', NULL, 'village_affairs:village_assess_plan:list', 2, 6, 1, 0),
(79, 72, 'village_assess_score', '/village_affairs/decision/village_assess_score/list', NULL, 'village_affairs:village_assess_score:list', 2, 7, 1, 0),
(80, 72, 'village_assess_result', '/village_affairs/decision/village_assess_result/list', NULL, 'village_affairs:village_assess_result:list', 2, 8, 1, 0),
(81, 72, 'village_notice', '/village_affairs/decision/village_notice/list', NULL, 'village_affairs:village_notice:list', 2, 9, 1, 0),
(82, 72, 'village_stat_report', '/village_affairs/decision/village_stat_report/list', NULL, 'village_affairs:village_stat_report:list', 2, 10, 1, 0),
(83, 1, '系统管理', '/village_affairs/system', NULL, NULL, 1, 12, 1, 0),
(84, 83, 'approval', '/village_affairs/system/approval/list', NULL, 'village_affairs:approval:list', 2, 1, 1, 0),
(85, 83, 'approval_node', '/village_affairs/system/approval_node/list', NULL, 'village_affairs:approval_node:list', 2, 2, 1, 0),
(86, 83, 'attachment', '/village_affairs/system/attachment/list', NULL, 'village_affairs:attachment:list', 2, 3, 1, 0),
(87, 83, 'operation_log', '/village_affairs/system/operation_log/list', NULL, 'village_affairs:operation_log:list', 2, 4, 1, 0),
(88, 83, 'notice', '/village_affairs/system/notice/list', NULL, 'village_affairs:notice:list', 2, 5, 1, 0),
(89, 83, 'document', '/village_affairs/system/document/list', NULL, 'village_affairs:document:list', 2, 6, 1, 0),
(90, 83, 'task', '/village_affairs/system/task/list', NULL, 'village_affairs:task:list', 2, 7, 1, 0),
(91, 83, 'task_item', '/village_affairs/system/task_item/list', NULL, 'village_affairs:task_item:list', 2, 8, 1, 0),
(92, 83, 'feedback', '/village_affairs/system/feedback/list', NULL, 'village_affairs:feedback:list', 2, 9, 1, 0),
(93, 83, 'tag', '/village_affairs/system/tag/list', NULL, 'village_affairs:tag:list', 2, 10, 1, 0),
(94, 83, 'tag_ref', '/village_affairs/system/tag_ref/list', NULL, 'village_affairs:tag_ref:list', 2, 11, 1, 0),
(95, 83, 'dict', '/village_affairs/system/dict/list', NULL, 'village_affairs:dict:list', 2, 12, 1, 0),
(96, 83, 'config', '/village_affairs/system/config/list', NULL, 'village_affairs:config:list', 2, 13, 1, 0),
(97, 83, 'audit_trail', '/village_affairs/system/audit_trail/list', NULL, 'village_affairs:audit_trail:list', 2, 14, 1, 0),
(98, 83, 'message', '/village_affairs/system/message/list', NULL, 'village_affairs:message:list', 2, 15, 1, 0);

INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) SELECT 1,`id` FROM `sys_menu` WHERE `deleted`=0;
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) SELECT 2,`id` FROM `sys_menu` WHERE `deleted`=0;

INSERT INTO `dict` (`id`, `type`, `name`, `value`, `code`, `sort`, `status`, `deleted`) VALUES
(1, 'village_status', '村务状态', '0', '草稿', 1, 1, 0),
(2, 'village_status', '村务状态', '1', '待审批', 2, 1, 0),
(3, 'village_status', '村务状态', '2', '已通过', 3, 1, 0),
(4, 'village_status', '村务状态', '3', '已驳回', 4, 1, 0),
(5, 'village_gender', '性别', '1', '男', 1, 1, 0),
(6, 'village_gender', '性别', '2', '女', 2, 1, 0),
(7, 'household_type', '户类型', '1', '普通农户', 1, 1, 0),
(8, 'household_type', '户类型', '2', '低保户', 2, 1, 0),
(9, 'household_type', '户类型', '3', '五保户', 3, 1, 0),
(10, 'land_type', '土地类型', '1', '耕地', 1, 1, 0),
(11, 'land_type', '土地类型', '2', '林地', 2, 1, 0),
(12, 'land_type', '土地类型', '3', '宅基地', 3, 1, 0);


-- ===== 基础数据：村组/户/村民/地块/网格 =====
INSERT INTO `village_area` (`id`, `area_code`, `area_name`, `parent_id`, `area_level`, `area_type`, `population`, `households`, `leader`, `leader_phone`, `sort`, `status`) VALUES
(1, 'V001', '莲花村', 0, '村', '行政村', 2860, 812, '王建国', '13800138001', 1, 1),
(2, 'V001-01', '莲花村一组', 1, '组', '村民小组', 720, 205, '李长福', '13800138002', 1, 1),
(3, 'V001-02', '莲花村二组', 1, '组', '村民小组', 680, 193, '张国平', '13800138003', 1, 2),
(4, 'V001-03', '莲花村三组', 1, '组', '村民小组', 790, 226, '陈有田', '13800138004', 1, 3),
(5, 'V001-04', '莲花村四组', 1, '组', '村民小组', 670, 188, '刘桂花', '13800138005', 1, 4);

INSERT INTO `village_household` (`id`, `household_no`, `head_name`, `head_id_card`, `member_count`, `addr`, `area`, `household_type`, `village_id`, `grid_id`, `status`, `remark`) VALUES
(1, 'H20240001', '李长福', '110101197001011234', 4, '莲花村一组李家巷12号', 0.85, '普通农户', 1, 2, 1, 1),
(2, 'H20240002', '张国平', '110101196803152345', 3, '莲花村二组张家庄8号', 0.62, '普通农户', 1, 3, 1, 1),
(3, 'H20240003', '陈有田', '110101197505203456', 5, '莲花村三组陈家湾21号', 1.2, '普通农户', 1, 4, 1, 1),
(4, 'H20240004', '刘桂花', '110101195811064567', 2, '莲花村四组刘家院3号', 0.45, '低保户', 1, 5, 1, 1),
(5, 'H20240005', '王建国', '110101196610128901', 4, '莲花村一组村委会旁', 0.95, '普通农户', 1, 2, 1, 1);

INSERT INTO `village_resident` (`id`, `resident_name`, `id_card`, `gender`, `birth_date`, `ethnicity`, `phone`, `education`, `occupation`, `household_id`, `village_id`, `grid_id`, `status`, `remark`) VALUES
(1, '李长福', '110101197001011234', '男', '1970-01-01', '汉族', '13800138002', '初中', '务农', 1, 1, 2, 1, '户主'),
(2, '李秀英', '110101197203088765', '女', '1972-03-08', '汉族', NULL, '小学', '务农', 1, 1, 2, 1, '配偶'),
(3, '李小明', '110101199509126789', '男', '1995-09-12', '汉族', '13800138333', '本科', '外出务工', 1, 1, 2, 1, '子女'),
(4, '张国平', '110101196803152345', '男', '1968-03-15', '汉族', '13800138003', '高中', '务农', 2, 1, 3, 1, '户主'),
(5, '张丽', '110101199207134321', '女', '1992-07-13', '汉族', NULL, '大专', '本地就业', 2, 1, 3, 1, '子女'),
(6, '陈有田', '110101197505203456', '男', '1975-05-20', '汉族', '13800138004', '初中', '务农', 3, 1, 4, 1, '户主'),
(7, '刘桂花', '110101195811064567', '女', '1958-11-06', '汉族', '13800138005', '小学', '无', 4, 1, 5, 1, '低保对象'),
(8, '王建国', '110101196610128901', '男', '1966-10-12', '汉族', '13800138001', '高中', '村支书', 5, 1, 2, 1, '户主/村干部');

INSERT INTO `village_land_parcel` (`id`, `parcel_no`, `name`, `area`, `land_type`, `location`, `east_to`, `west_to`, `south_to`, `north_to`, `owner_id`, `status`) VALUES
(1, 'DK2024001', '东湾责任田', 12.5, '耕地', '莲花村东湾', '大路', '水渠', '张家地', '陈家地', 1, 1),
(2, 'DK2024002', '南坡林地', 30.0, '林地', '莲花村南坡', '山脊', '村道', '李某地', '荒坡', 1, 1),
(3, 'DK2024003', '西塘水田', 8.2, '耕地', '莲花村西塘', '塘埂', '机耕路', '水渠', '王某地', 1, 1),
(4, 'DK2024004', '村委会宅基地', 0.6, '宅基地', '莲花村村委会', '村道', '李某房', '空地', '水渠', 1, 1);

INSERT INTO `village_grid` (`id`, `grid_code`, `grid_name`, `village_id`, `grid_head`, `population`, `status`, `sort`) VALUES
(1, 'WG001', '第一网格', 1, '王建国', 812, 1, 1),
(2, 'WG002', '第二网格', 1, '张网格员', 680, 1, 2),
(3, 'WG003', '第三网格', 1, '陈有田', 790, 1, 3);

INSERT INTO `village_grid_member` (`id`, `member_name`, `phone`, `grid_id`, `role`, `status`) VALUES
(1, '王建国', '13800138001', 1, '网格长', 1),
(2, '张网格员', '13800138888', 2, '网格员', 1),
(3, '陈有田', '13800138004', 3, '网格员', 1);


-- ===== 三资管理：资产/合同/资金/流水 =====
INSERT INTO `village_asset` (`id`, `asset_no`, `asset_name`, `category`, `type`, `model`, `amount`, `quantity`, `purchase_date`, `custodian_id`, `location`, `status`, `remark`) VALUES
(1, 'ZC2024001', '村委会办公楼', '房屋建筑', '固定资产', '砖混三层', 850000, 1, '2015-06-01', 1, '莲花村村委会', 1, '在用'),
(2, 'ZC2024002', '村集体拖拉机等农机', '机械设备', '固定资产', '东方红', 120000, 3, '2019-03-15', 2, '农机库', 1, '在用'),
(3, 'ZC2024003', '村集体鱼塘', '资源性资产', '农业设施', '30亩', 200000, 1, '2018-09-10', 3, '莲花村南塘', 1, '发包中'),
(4, 'ZC2024004', '文化活动广场', '公共设施', '固定资产', '水泥硬化', 320000, 1, '2020-10-01', 1, '莲花村中心', 1, '在用');

INSERT INTO `village_contract` (`id`, `contract_no`, `name`, `type`, `party_a`, `party_b`, `start`, `end`, `amount`, `pay_type`, `sign_date`, `status`, `remark`) VALUES
(1, 'HT2024001', '东湾责任田承包合同', '土地承包', '莲花村集体经济合作社', '李长福', '2024-01-01 00:00:00', '2028-12-31 23:59:59', 12500, '年付', '2024-01-01', 1, '履行中'),
(2, 'HT2024002', '南塘鱼塘发包合同', '鱼塘发包', '莲花村集体经济合作社', '张国平', '2024-03-01 00:00:00', '2027-02-28 23:59:59', 60000, '年付', '2024-03-01', 1, '履行中'),
(3, 'HT2024003', '村集体门面房租赁合同', '房屋租赁', '莲花村集体经济合作社', '王建国', '2025-01-01 00:00:00', '2027-12-31 23:59:59', 36000, '半年付', '2025-01-01', 1, '履行中');

INSERT INTO `village_fund_account` (`id`, `account_no`, `account_name`, `bank`, `type`, `balance`, `owner_id`, `open_date`, `status`, `remark`) VALUES
(1, 'ZH2024001', '莲花村集体经济基本户', '中国农业银行莲花支行', '基本户', 1256800.0, 1, '2018-01-01', 1, '村集体资金主账户'),
(2, 'ZH2024002', '莲花村专户（惠农补贴）', '农村商业银行莲花支行', '专户', 286500.0, 1, '2020-05-01', 1, '专项资金');

INSERT INTO `village_fund_transaction` (`id`, `transaction_no`, `account_id`, `type`, `direction`, `amount`, `before_balance`, `after_balance`, `summary`, `transaction_date`, `status`) VALUES
(1, 'JY20240101', 1, '收入', '承包款', 12500.0, '1244300.00', '1256800.00', '东湾责任田承包款', '2024-01-10', 1),
(2, 'JY20240102', 1, '支出', '办公费', 3500.0, '1256800.00', '1253300.00', '村委会办公用品采购', '2024-01-15', 2),
(3, 'JY20240103', 1, '收入', '租赁收入', 18000.0, '1253300.00', '1271300.00', '门面房半年租金', '2024-01-20', 2),
(4, 'JY20240201', 2, '收入', '财政拨款', 50000.0, '236500.00', '286500.00', '惠农专项资金拨款', '2024-02-01', 2),
(5, 'JY20240202', 1, '支出', '公益支出', 12000.0, '1271300.00', '1259300.00', '文化活动广场维护', '2024-02-05', 2);


-- ===== 党建治理：党组织/党员/公开/纠纷 =====
INSERT INTO `village_party_org` (`id`, `org_name`, `org_type`, `secretary`, `member_count`, `parent_id`, `status`) VALUES
(1, '中共莲花村支部委员会', '党支部', '王建国', 28, 0, 1),
(2, '莲花村第一党小组', '党小组', '李长福', 14, 1, 1),
(3, '莲花村第二党小组', '党小组', '张国平', 14, 1, 1);

INSERT INTO `village_party_member` (`id`, `member_no`, `name`, `gender`, `birth_date`, `id_card`, `phone`, `education`, `join_date`, `party_status`, `org_id`, `status`, `remark`) VALUES
(1, 'D001', '王建国', '男', '1966-10-12', '110101196610128901', '13800138001', '高中', '1998-07-01', '正式党员', 1, 1, '支部书记'),
(2, 'D002', '李长福', '男', '1970-01-01', '110101197001011234', '13800138002', '初中', '2005-06-15', '正式党员', 1, 1, '党小组长'),
(3, 'D003', '张国平', '男', '1968-03-15', '110101196803152345', '13800138003', '高中', '2008-11-20', '正式党员', 1, 1, '党小组长'),
(4, 'D004', '陈有田', '男', '1975-05-20', '110101197505203456', '13800138004', '初中', '2012-05-04', '正式党员', 1, 1, '党员'),
(5, 'D005', '刘桂花', '女', '1958-11-06', '110101195811064567', '13800138005', '小学', '1985-09-10', '正式党员', 1, 1, '老党员');

INSERT INTO `village_disclosure` (`id`, `disclosure_no`, `title`, `category`, `type`, `content`, `publisher_id`, `publish_time`, `scope`, `status`, `remark`) VALUES
(1, 'GK2024001', '2024年度村集体经济收益分配方案', '财务公开', '收益分配', '经村民代表大会表决通过，年度收益按人口与贡献分配', 1, '2025-01-15 00:00:00', '全村', 2, '已公开'),
(2, 'GK2024002', '莲花村2024年度财务收支决算', '财务公开', '财务决算', '全年收支决算情况公示', 1, '2025-01-20 00:00:00', '全村', 2, '已公开'),
(3, 'GK2024003', '村内道路硬化工程招标公示', '村务公开', '工程招标', '村内主干道硬化工程公开招标', 2, '2025-03-01 00:00:00', '全村', 1, '待审核');

INSERT INTO `village_dispute` (`id`, `case_no`, `title`, `dispute_type`, `party_a`, `party_b`, `create_time`, `status`, `remark`) VALUES
(1, 'JF2024001', '李某与张某宅基地边界纠纷', '邻里纠纷', '李长福', '张国平', '2024-05-10', 1, '已调解'),
(2, 'JF2024002', '鱼塘承包款支付纠纷', '经济纠纷', '张国平', '村委会', '2024-08-20', 2, '调解中');


-- ===== 便民服务：事项/申请/环节 =====
INSERT INTO `village_service_item` (`id`, `item_code`, `item_name`, `category`, `type`, `department`, `limit_days`, `fee`, `material`, `channel`, `status`, `remark`) VALUES
(1, 'FW001', '生育登记服务', '社会保障', '个人', '卫健办', 3, 0, '身份证、结婚证', '窗口办理', 1, '即办'),
(2, 'FW002', '农村低保申请', '社会救助', '个人', '民政办', 15, 0, '申请表、收入证明', '窗口办理', 1, '需审核'),
(3, 'FW003', '宅基地使用权申请', '国土规划', '个人', '国土所', 20, 0, '申请表、户籍证明', '窗口办理', 1, '需审批'),
(4, 'FW004', '新型农村合作医疗参保', '医疗卫生', '个人', '医保办', 5, 0, '身份证、户口本', '网上办理', 1, '即办'),
(5, 'FW005', '证明类事项代办', '便民代办', '个人', '村委会', 2, 0, '身份证', '帮办代办', 1, '即办');

INSERT INTO `village_service_apply` (`id`, `apply_no`, `item_id`, `applicant_id`, `applicant_name`, `phone`, `apply_date`, `material`, `handler_id`, `result`, `status`, `remark`) VALUES
(1, 'SQ20240001', 2, 1, '李长福', '13800138002', '2024-06-01', '申请表、收入证明', 1, '已办理完成', 2, '低保申请'),
(2, 'SQ20240002', 3, 4, '张国平', '13800138003', '2024-07-15', '申请表、户籍证明', 2, '待村委会审核', 1, '宅基地申请'),
(3, 'SQ20240003', 1, 3, '陈有田', '13800138004', '2024-09-20', '身份证、结婚证', 2, '待受理', 1, '生育登记'),
(4, 'SQ20240004', 5, 5, '刘桂花', '13800138005', '2024-10-08', '身份证', 5, '已办结', 2, '代办证明');

INSERT INTO `village_service_flow` (`id`, `apply_id`, `node_name`, `node_order`, `handler_id`, `action`, `result`, `comment`, `status`) VALUES
(1, 1, '受理登记', '1', 1, '受理', '材料齐全，予以受理', '已核对材料', 2),
(2, 1, '村委会初审', '2', 2, '初审通过', '符合低保条件', '入户调查属实', 2),
(3, 1, '办结出件', '3', 1, '办结', '低保申请已批准', '通知申请人', 2),
(4, 2, '受理登记', '1', 5, '受理', '已受理', '', 2),
(5, 2, '现场勘查', '2', 2, '勘查中', '', '待国土资源所联合勘查', 1);


-- ===== 任务督办/决策：任务/指标/通知/民情 =====
INSERT INTO `village_task` (`id`, `task_no`, `title`, `type`, `source`, `content`, `assignee_id`, `start`, `due_time`, `priority`, `progress`, `result`, `status`, `remark`) VALUES
(1, 'RW2024001', '村内主干道硬化工程推进', '重点工程', '乡镇督办', '完成村内2公里主干道硬化', 2, '2024-03-01 00:00:00', '2024-10-31 23:59:59', 2, 85, '施工中', 1, '已完工85%'),
(2, 'RW2024002', '人居环境整治专项行动', '环境整治', '乡镇督办', '清理垃圾死角、整治村容村貌', 3, '2024-05-01 00:00:00', '2024-09-30 23:59:59', 3, 100, '已完成', 2, '通过验收'),
(3, 'RW2024003', '低保户入户走访排查', '民生保障', '村委自定', '对全村低保户逐户走访', 4, '2024-08-01 00:00:00', '2024-09-15 23:59:59', 4, 100, '已完成', 2, '走访12户');

INSERT INTO `village_dashboard_metric` (`id`, `metric_code`, `metric_name`, `value`, `unit`, `remark`, `status`) VALUES
(1, 'POPULATION', '常住人口', 2860, '人', '2025-01-01', 1),
(2, 'HOUSEHOLDS', '户籍户数', 812, '户', '2025-01-01', 1),
(3, 'LAND_AREA', '耕地面积', 1280.5, '亩', '2025-01-01', 1),
(4, 'COLLECTIVE_INCOME', '集体经济收入', 1256800.0, '元', '2025-01-01', 1),
(5, 'PARTY_MEMBERS', '党员人数', 28, '人', '2025-01-01', 1),
(6, 'GRID_COUNT', '治理网格数', 3, '个', '2025-01-01', 1);

INSERT INTO `village_notice` (`id`, `notice_no`, `title`, `type`, `content`, `publisher_id`, `publish_time`, `status`) VALUES
(1, 'TZ2024001', '关于2025年春节放假安排的通知', '通知', '春节期间村委会放假7天，如有急事请联系值班人员', 1, '2025-01-20 00:00:00', 2),
(2, 'TZ2024002', '关于开展人居环境整治的通知', '公告', '全村开展人居环境整治，请各户积极配合', 3, '2025-03-01 00:00:00', 2);

INSERT INTO `village_mood_log` (`id`, `log_no`, `resident_id`, `content`, `create_time`, `reporter_id`, `status`) VALUES
(1, 'MQ2024001', 1, '反映东湾灌溉水渠年久失修，希望村委协调修缮', '2024-06-15', 2, 2),
(2, 'MQ2024002', 7, '低保金发放及时，对生活保障满意', '2024-08-20', 1, 2),
(3, 'MQ2024003', 4, '建议村内增设路灯，夜间出行不便', '2024-09-10', 3, 1);


-- ===== 流程引擎：sys_flow_def + 审批单 + 节点 =====
INSERT INTO `sys_flow_def` (`id`, `flow_code`, `flow_name`, `table_name`, `status_col`, `nodes`, `enabled`, `remark`) VALUES
(1, 'village_service_apply', '便民办事审批流', 'village_service_apply', 'status', '[{"from":"0","to":"1","name":"提交申请"},{"from":"1","to":"2","name":"村委审批","roleCode":"village_director","rejectTo":"3"},{"from":"1","to":"2","name":"代办办结","roleCode":"service_agent"}]', 1, '便民事项从提交到办结'),
(2, 'village_approval', '村务通用审批流', 'approval', 'status', '[{"from":"0","to":"1","name":"提交审批"},{"from":"1","to":"2","name":"书记审批","roleCode":"village_party_secretary","rejectTo":"3"}]', 1, '村务事项审批');

INSERT INTO `approval` (`id`, `no`, `title`, `type`, `amount`, `applicant_id`, `current_node`, `status`, `result`, `submit_time`, `finish_time`) VALUES
(1, 'SP20240001', '村委会办公楼维修申请', '费用报销', 35000, 1, '书记审批', 2, '同意维修，从办公经费列支', '2024-05-10', '2024-05-15'),
(2, 'SP20240002', '文化活动广场设备采购', '采购申请', 18000, 1, '书记审批', 1, '', '2024-08-20', NULL),
(3, 'SP20240003', '困难户临时救助申请', '救助审批', 5000, 4, '书记审批', 3, '不符合条件，驳回', '2024-09-01', '2024-09-03');

INSERT INTO `approval_node` (`id`, `approval_id`, `node_name`, `node_order`, `assignee_id`, `status`, `result`, `comment`) VALUES
(1, 1, '提交申请', 1, 1, 2, '已提交', ''),
(2, 1, '书记审批', 2, 2, 2, '同意', '从办公经费列支'),
(3, 2, '提交申请', 1, 3, 2, '已提交', ''),
(4, 2, '书记审批', 2, 2, 1, '待审批', ''),
(5, 3, '提交申请', 1, 4, 2, '已提交', ''),
(6, 3, '书记审批', 2, 2, 3, '驳回', '不符合救助条件');


INSERT INTO `sys_flow_task` (`id`, `flow_code`, `biz_table`, `biz_id`, `from_status`, `to_status`, `action`, `comment`, `oper_user_id`, `oper_name`) VALUES
(1, 'village_service_apply', 'village_service_apply', 1, '0', '1', 'SUBMIT', '提交低保申请', 1, '李长福'),
(2, 'village_service_apply', 'village_service_apply', 1, '1', '2', 'APPROVE', '低保申请已批准', 2, '王书记'),
(3, 'village_service_apply', 'village_service_apply', 2, '0', '1', 'SUBMIT', '提交宅基地申请', 4, '张国平'),
(4, 'village_approval', 'approval', 1, '0', '1', 'SUBMIT', '提交维修申请', 1, '李长福'),
(5, 'village_approval', 'approval', 1, '1', '2', 'APPROVE', '同意维修', 2, '王书记');
