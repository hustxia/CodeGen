-- V9.25.1 M2.1 种子：28 条行业规则 + 8 道澄清问题 + 1 条演示会话
-- 幂等：rule_code / question_code / session id 均有唯一约束或 WHERE NOT EXISTS

INSERT INTO gen_guardrail_rule
    (id, rule_code, industry, category, severity, trigger_type, trigger_expr, title, reason, suggestion, sort_no, enabled, create_time)
VALUES
-- 村务治理 7 条
(9925001,'GOV-B01','gov_affairs','BLACKLIST','BLOCK','KEYWORD','用户管理|角色管理|菜单管理|数据字典|系统配置','基座不得作为业务系统单独生成','用户/角色/菜单/字典属于基座15表，单独生成只会得到空壳后台，没有业务闭环。','新建业务系统（如网格化事件处置），基座随附生成。',1,1,NOW()),
(9925002,'GOV-B02','gov_affairs','BLACKLIST','BLOCK','KEYWORD','手机号明文|身份证明文|不脱敏|明文导出','敏感字段必须脱敏','政务验收必查数据分类分级，身份证/手机号/住址明文展示属等保红线。','字段标 sensitive，列表与导出自动掩码，授权后才可见明文。',2,1,NOW()),
(9925003,'GOV-W01','gov_affairs','WHITELIST','WARN','MISSING','{"moduleKeyword":"网格"}','网格/人口主数据先行','村务业务以网格与人口为挂靠对象，缺主数据会导致重复录入。','先建网格与人口主数据，业务表引用其 id。',3,1,NOW()),
(9925004,'GOV-W02','gov_affairs','WHITELIST','WARN','MISSING','{"moduleKeyword":"脱敏"}','数据分类分级纳入范围','未声明脱敏范围会在验收阶段被要求整改。','在规格里写明敏感字段清单与掩码策略。',4,1,NOW()),
(9925005,'GOV-C01','gov_affairs','CONFLICT','BLOCK','COMBO','{"questionCode":"Q_DEVICE","answer":"含直连设备","keyword":"GIS|地图"}','GIS 直出与外接冲突','GIS 底图有审图号合规要求，业务库直出地图数据既违规又难维护。','统一走天地图/标准地图服务，业务库只存要素编码。',5,1,NOW()),
(9925006,'GOV-BD1','gov_affairs','BOUNDARY','WARN','KEYWORD','视频算法|视频会商|GIS|地图','GIS/视频/算法为外接边界','生成器不承诺地图底图与视频算法能力，硬做会在验收时被判不合格。','标注为外接项，留接口与编码映射，不生成底图服务。',6,1,NOW()),
(9925007,'GOV-Q1','gov_affairs','CLARIFY','INFO','CLARIFY','','国产库与密评要求','澄清国产数据库与商用密码合规口径。','按答案决定方言适配与国密 SDK 封装范围。',7,1,NOW()),
-- 城市管理 7 条
(9925008,'CM-B01','cm_city','BLACKLIST','BLOCK','KEYWORD','用户管理|角色管理|菜单管理|数据字典|系统配置','基座不得作为业务系统单独生成','城管15系统共用一套底座，单独生成通用后台没有业务闭环。','先建底座与事件中心，15 个监管对象挂接其上。',1,1,NOW()),
(9925009,'CM-B02','cm_city','BLACKLIST','BLOCK','KEYWORD','GPS轨迹入库|轨迹直接入库|轨迹存业务库','GPS 轨迹不进业务库','单车 1.7 万点/天，100 辆即 170 万点/天，业务列表必卡。','轨迹落位置服务，业务库只存告警与案件。',2,1,NOW()),
(9925010,'CM-W01','cm_city','WHITELIST','WARN','MISSING','{"moduleKeyword":"事件中心"}','必须挂事件中心','15 系统共用发现→立案→派遣→处置→核查→结案主线。','先建 cm_event 事件中心，其余系统作为监管对象挂接。',3,1,NOW()),
(9925011,'CM-W02','cm_city','WHITELIST','WARN','MISSING','{"moduleKeyword":"指标配置"}','考核指标全配置化','考核办法一年一改，硬编码等于每年重做。','指标/权重/评分规则落配置表，不改代码。',4,1,NOW()),
(9925012,'CM-C01','cm_city','CONFLICT','BLOCK','COMBO','{"questionCode":"Q_DEVICE","answer":"含直连设备","keyword":"渣土|GPS"}','渣土车台账直出与 GPS 外接冲突','台账直出与轨迹外接是互补，勾选直出 GPS 会把高频数据塞进业务库。','台账直出 + 轨迹外接，业务库只存告警与案件。',5,1,NOW()),
(9925013,'CM-BD1','cm_city','BOUNDARY','WARN','KEYWORD','视频会商|单灯控制|SCADA|地磅|PLC','设备实时控制必须外接','设备协议私有，生成器只做台账/告警/工单/策略下发记录。','走设备网关或厂商平台，合同写明开放接口。',6,1,NOW()),
(9925014,'CM-Q1','cm_city','CLARIFY','INFO','CLARIFY','','15 域范围裁剪','明确本期做底座+事件中心还是全量 15 域。','按答案决定分期与外接系统数。',7,1,NOW()),
-- 智慧种植 7 条
(9925015,'AGR-B01','agr_crop','BLACKLIST','BLOCK','KEYWORD','用户管理|角色管理|菜单管理|数据字典|系统配置','基座不得作为业务系统单独生成','种植业务以地块/种植季为地基，通用后台没有业务闭环。','先建地块/种植季主数据与农事作业引擎。',1,1,NOW()),
(9925016,'AGR-B02','agr_crop','BLACKLIST','BLOCK','KEYWORD','AI识别直接下发|识别结果自动打药|自动打药','AI 识别结果不得直接下发','识别准确率不可能 100%，误报一次全田打药就是事故。','识别结果人工确认后生成打药作业单。',2,1,NOW()),
(9925017,'AGR-W01','agr_crop','WHITELIST','WARN','MISSING','{"moduleKeyword":"种植季"}','农事记录挂种植季','同一块地一年两茬，按地块混算成本与产量全废。','先建地块+种植季主数据，农事记录引用 season_id。',3,1,NOW()),
(9925018,'AGR-W02','agr_crop','WHITELIST','WARN','MISSING','{"moduleKeyword":"面积"}','面积口径只有一份','用工/施肥/受灾各录各的亩数，年底对不上。','面积只由地块主数据维护，其他地方引用。',4,1,NOW()),
(9925019,'AGR-C01','agr_crop','CONFLICT','BLOCK','COMBO','{"questionCode":"Q_DEVICE","answer":"含直连设备","keyword":"水肥|灌溉"}','水肥控制不得直驱','灌溉控制失败=作物损失，直驱阀门无人工兜底。','处方下发 → 农技员审核 → 设备执行 → 回读对账，设备端保留脱网兜底。',5,1,NOW()),
(9925020,'AGR-BD1','agr_crop','BOUNDARY','WARN','KEYWORD','病害AI|自动驾驶|无人机|自研识别','识别算法与农机自动驾驶为边界外','垂直 AI 与农机导航是专业厂商生态。','外接成熟算法，平台只做预警到处置闭环。',6,1,NOW()),
(9925021,'AGR-Q1','agr_crop','CLARIFY','INFO','CLARIFY','','水肥机协议开放程度','合同未写明开放接口是联调阶段被厂商拿捏的主因。','确认提供 Modbus/私有 SDK/未约定，决定控制类系统能否生成。',7,1,NOW()),
-- 智慧园区 7 条
(9925022,'PARK-B01','park_park','BLACKLIST','BLOCK','KEYWORD','用户管理|角色管理|菜单管理|数据字典|系统配置','基座不得作为业务系统单独生成','园区55项共用统一身份与空间主数据，通用后台没有业务闭环。','先建统一身份+空间主数据+工单引擎三个底座。',1,1,NOW()),
(9925023,'PARK-B02','park_park','BLACKLIST','BLOCK','KEYWORD','租区不留历史|租区无生效区间|不保留租区历史','租区变更必须留历史','拆租/合租/换租不留历史，能耗分摊与账单全对不上。','租区表带 valid_from/valid_to，变更另起记录。',2,1,NOW()),
(9925024,'PARK-W01','park_park','WHITELIST','WARN','MISSING','{"moduleKeyword":"空间主数据"}','先建空间主数据','招商/租赁/资产/能耗全挂在园区-楼栋-楼层-房间-租区上。','先建空间主数据与变更历史，再建业务系统。',3,1,NOW()),
(9925025,'PARK-W02','park_park','WHITELIST','WARN','MISSING','{"moduleKeyword":"统一身份"}','统一身份与一卡通账户先行','15 套独立账号后期做一张屏要全量重构权限。','人员/车辆/消费/通行使用一张账户。',4,1,NOW()),
(9925026,'PARK-C01','park_park','CONFLICT','BLOCK','COMBO','{"questionCode":"Q_DEVICE","answer":"含直连设备","keyword":"消防"}','消防联动不得平台自研','消防联动控制是法规红线，平台只能监视+转发工单。','联动由消防主机执行，平台只读状态并留联动日志。',5,1,NOW()),
(9925027,'PARK-BD1','park_park','BOUNDARY','WARN','KEYWORD','人脸|门禁|停车|BA|BACnet','安防/门禁/停车/BA 不自研','自研等于跟硬件生态为敌，验收时厂商不配合就死。','接厂商平台/网关，合同写明开放协议。',6,1,NOW()),
(9925028,'PARK-Q1','park_park','CLARIFY','INFO','CLARIFY','','IoT 平台选型','IoT 物模型/告警规则/联动规则是园区55项的公共底座。','确认已选型/待选型/不接，决定物联接入范围。',7,1,NOW())
ON DUPLICATE KEY UPDATE id = id;

-- 通用 4 问 + 行业专属 4 问：向导取 4 + 1 = 5 问
INSERT INTO gen_clarify_question
    (id, question_code, industry, sort_no, question, options_json, hint, enabled, create_time)
VALUES
(9925101,'Q_DOMAIN','',1,'这次要建的核心业务对象是什么？（如事件/工单/地块/租区）','','决定模块拆分是否围绕业务对象',1,NOW()),
(9925102,'Q_MASTER','',2,'主数据由谁维护？','["本系统建","外接主数据平台","尚未确定"]','决定是否要先建底座',1,NOW()),
(9925103,'Q_APPROVAL','',3,'是否包含审批流？','["含","不含","待定"]','决定是否引入工作流引擎',1,NOW()),
(9925104,'Q_DEVICE','',4,'是否涉及设备接入与控制？','["只台账","含控制","含直连设备"]','决定边界与外接项',1,NOW()),
(9925105,'GOV-Q-SCOPE','gov_affairs',5,'国产数据库与密评要求是什么？','["达梦/金仓+SM系列","MySQL+国密SDK","暂不考虑"]','决定方言适配与国密封装范围',1,NOW()),
(9925106,'CM-Q-SCOPE','cm_city',5,'本期覆盖哪些城管域？','["底座+事件中心","含渣土车与照明","全量15域"]','决定分期与外接系统数',1,NOW()),
(9925107,'AGR-Q-PROTOCOL','agr_crop',5,'水肥机厂商协议是否开放？','["提供Modbus接口文档","提供私有SDK","合同未约定"]','决定控制类系统能否生成',1,NOW()),
(9925108,'PARK-Q-IOT','park_park',5,'IoT 平台是否已选型？','["已定厂商+协议","待选型","不接IoT"]','决定物联接入范围',1,NOW())
ON DUPLICATE KEY UPDATE id = id;

-- 演示会话：智慧种植，已答 4 通用问，停在行业专属第 5 问
INSERT INTO gen_clarify_session
    (id, project_id, industry, system_name, system_desc, module_names, answers_json, hits_json, overridden_codes, advanced_mode, question_index, status, create_time)
SELECT 9925201, 1, 'agr_crop', '智慧种植 + 水肥一体化',
       '地块/种植季主数据 + 农事作业引擎 + 水肥一体化处方下发',
       JSON_ARRAY('地块与种植季','农事作业','水肥一体化','病虫害预警'),
       JSON_OBJECT('Q_DOMAIN','地块与种植季','Q_MASTER','本系统建','Q_APPROVAL','含','Q_DEVICE','含控制'),
       JSON_ARRAY(), '', 0, 4, 'CLARIFYING', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_clarify_session WHERE id = 9925201);
