# -*- coding: utf-8 -*-
"""V9.22 生成城管/园区两个行业范式包 JSON（结构与 IndustryTemplateRegistry.TableModel 同形）。
表清单来自城管15系统落地方案.md / 智慧园区55项落地方案.md 的核心表，不做全量 1000 表。
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/industry-templates'

def T(name, comment, cols):
    return {"tableName": name, "tableComment": comment, "columns": [
        {"name": "id", "type": "bigint", "comment": "主键", "pk": True},
        *[{"name": n, "type": t, "comment": c, "pk": False} for n, t, c in cols],
        {"name": "create_time", "type": "datetime", "comment": "创建时间", "pk": False},
        {"name": "update_time", "type": "datetime", "comment": "更新时间", "pk": False},
        {"name": "deleted", "type": "tinyint", "comment": "逻辑删除", "pk": False},
    ]}

def M(name, tables):
    return {"name": name, "tables": tables}

cm_city = {
    "key": "cm_city",
    "name": "智慧城管范式包",
    "category": "城市管理",
    "desc": "城管15系统范式：1事件中心+4监管对象+1考核引擎；底座29表先行，11系统直建、渣土车/灯杆/供排水/指挥调度4类外接",
    "modules": [
        M("事件底座(29表先行)", [
            T("cm_event", "城管事件主表", [("event_no", "varchar(64)", "事件编号"), ("event_type_code", "varchar(32)", "GB/T 30428事件分类"), ("source", "varchar(20)", "来源:巡查/上报/设备/视频"), ("grid_id", "bigint", "所属网格"), ("status", "tinyint", "状态:发现/立案/派遣/处置/核查/结案"), ("deadline", "datetime", "处置时限")]),
            T("cm_event_record", "事件流转记录", [("event_id", "bigint", "事件ID"), ("action", "varchar(32)", "动作"), ("operator_id", "bigint", "操作人"), ("remark", "varchar(512)", "说明")]),
            T("cm_part", "城管部件库", [("part_no", "varchar(64)", "部件编号"), ("part_type", "varchar(32)", "部件类型"), ("grid_id", "bigint", "所在网格"), ("status", "tinyint", "状态")]),
            T("cm_grid", "网格责任区", [("grid_code", "varchar(64)", "网格编码"), ("grid_name", "varchar(128)", "网格名称"), ("parent_id", "bigint", "上级网格"), ("owner_dept", "varchar(128)", "责任部门")]),
            T("cm_dispatch_rule", "派遣规则", [("event_type_code", "varchar(32)", "事件类型"), ("dept_id", "bigint", "派遣部门"), ("deadline_hours", "int", "时限小时")]),
        ]),
        M("智慧环卫与垃圾分类", [
            T("env_vehicle", "环卫车辆", [("plate_no", "varchar(32)", "车牌"), ("vehicle_type", "varchar(32)", "车型"), ("status", "tinyint", "状态")]),
            T("env_clean_task", "清扫任务", [("task_no", "varchar(64)", "任务编号"), ("grid_id", "bigint", "网格"), ("vehicle_id", "bigint", "车辆"), ("status", "tinyint", "状态")]),
            T("gm_station", "垃圾压缩站", [("station_no", "varchar(64)", "站点编号"), ("name", "varchar(128)", "站点名称"), ("capacity", "decimal(12,2)", "日处理能力")]),
            T("gm_collection_task", "收运任务", [("task_no", "varchar(64)", "任务编号"), ("station_id", "bigint", "压缩站"), ("status", "tinyint", "状态")]),
            T("pt_account", "居民积分账户(与商城共用)", [("user_id", "bigint", "用户ID"), ("balance", "int", "积分余额"), ("version", "int", "乐观锁版本")]),
        ]),
        M("建废渣土与治违", [
            T("cw_permit", "建筑废弃物处置许可", [("permit_no", "varchar(64)", "许可证号"), ("project_name", "varchar(128)", "工程名称"), ("amount", "decimal(12,2)", "处置量"), ("status", "tinyint", "状态")]),
            T("cw_transport_record", "渣土运输记录", [("permit_id", "bigint", "许可ID"), ("plate_no", "varchar(32)", "车牌"), ("weight", "decimal(12,2)", "载重"), ("event_id", "bigint", "关联事件")]),
            T("il_case", "违建案卷", [("case_no", "varchar(64)", "案卷号"), ("address", "varchar(256)", "地址"), ("area", "decimal(12,2)", "违建面积"), ("status", "tinyint", "阶段")]),
            T("il_evidence", "执法证据", [("case_id", "bigint", "案卷ID"), ("file_url", "varchar(512)", "证据文件"), ("evidence_type", "varchar(32)", "证据类型")]),
        ]),
        M("设施监管(公厕/园林/灯杆/市容)", [
            T("fac_toilet", "公厕档案", [("toilet_no", "varchar(64)", "公厕编号"), ("name", "varchar(128)", "名称"), ("grid_id", "bigint", "网格"), ("status", "tinyint", "状态")]),
            T("gd_green", "园林绿化档案", [("green_no", "varchar(64)", "绿地编号"), ("area", "decimal(12,2)", "面积"), ("type", "varchar(32)", "绿地类型")]),
            T("lt_pole", "智慧灯杆", [("pole_no", "varchar(64)", "灯杆编号"), ("location", "varchar(256)", "位置"), ("status", "tinyint", "状态")]),
            T("sc_ads", "户外广告牌", [("ads_no", "varchar(64)", "广告编号"), ("location", "varchar(256)", "位置"), ("permit_no", "varchar(64)", "许可号")]),
        ]),
        M("燃气供排水与考核", [
            T("gas_company", "燃气企业", [("company_no", "varchar(64)", "企业编号"), ("name", "varchar(128)", "名称"), ("license_no", "varchar(64)", "经营许可证")]),
            T("gas_inspection", "燃气巡检", [("company_id", "bigint", "企业"), ("location", "varchar(256)", "巡检位置"), ("result", "tinyint", "结果")]),
            T("water_pipe", "供排水管网", [("pipe_no", "varchar(64)", "管段编号"), ("type", "varchar(32)", "给水/排水"), ("length", "decimal(12,2)", "长度")]),
            T("ev_scheme", "考核方案", [("scheme_no", "varchar(64)", "方案编号"), ("name", "varchar(128)", "方案名称"), ("period", "varchar(20)", "考核周期")]),
            T("ev_result", "考核结果", [("scheme_id", "bigint", "方案"), ("dept_id", "bigint", "被考核部门"), ("score", "decimal(6,2)", "得分")]),
        ]),
    ]
}

park_park = {
    "key": "park_park",
    "name": "智慧园区范式包",
    "category": "智慧园区",
    "desc": "园区55项去重后范式：统一身份/一卡通+空间主数据+工单引擎三底座先行；14类IoT走物模型集成，4个驾驶舱=1引擎4视图",
    "modules": [
        M("身份与一卡通底座", [
            T("pk_account", "一卡通账户", [("account_no", "varchar(64)", "账户编号"), ("user_id", "bigint", "用户ID"), ("balance", "decimal(12,2)", "余额"), ("status", "tinyint", "状态")]),
            T("pk_card", "卡片档案", [("card_no", "varchar(64)", "卡号"), ("account_id", "bigint", "账户"), ("status", "tinyint", "状态")]),
            T("pk_recharge", "充值流水", [("account_id", "bigint", "账户"), ("amount", "decimal(12,2)", "金额"), ("channel", "varchar(32)", "渠道")]),
            T("pk_consume", "消费流水", [("account_id", "bigint", "账户"), ("amount", "decimal(12,2)", "金额"), ("merchant", "varchar(128)", "商户")]),
        ]),
        M("空间主数据", [
            T("pk_campus", "园区", [("campus_no", "varchar(64)", "园区编号"), ("name", "varchar(128)", "园区名称"), ("address", "varchar(256)", "地址")]),
            T("pk_building", "楼栋", [("campus_id", "bigint", "园区"), ("building_no", "varchar(64)", "楼栋编号"), ("name", "varchar(128)", "名称")]),
            T("pk_floor", "楼层", [("building_id", "bigint", "楼栋"), ("floor_no", "varchar(32)", "楼层号")]),
            T("pk_room", "房间", [("floor_id", "bigint", "楼层"), ("room_no", "varchar(64)", "房间号"), ("area", "decimal(12,2)", "面积")]),
            T("pk_lease_area", "租区(带生效区间)", [("room_id", "bigint", "房间"), ("tenant_id", "bigint", "承租企业"), ("start_date", "date", "生效日"), ("end_date", "date", "失效日")]),
        ]),
        M("工单引擎", [
            T("wo_ticket", "工单主表", [("ticket_no", "varchar(64)", "工单编号"), ("biz_type", "varchar(32)", "报修/巡检异常/设备告警/投诉"), ("source", "varchar(32)", "来源"), ("status", "tinyint", "状态"), ("assignee", "bigint", "处理人")]),
            T("wo_task", "工单任务", [("ticket_id", "bigint", "工单"), ("task_name", "varchar(128)", "任务"), ("status", "tinyint", "状态")]),
            T("wo_sla", "时效约定", [("biz_type", "varchar(32)", "业务类型"), ("deadline_min", "int", "时限分钟")]),
        ]),
        M("招商租赁与物业", [
            T("lease_tenant", "入驻企业", [("tenant_no", "varchar(64)", "企业编号"), ("name", "varchar(128)", "企业名称"), ("status", "tinyint", "状态")]),
            T("lease_contract", "租赁合同", [("contract_no", "varchar(64)", "合同编号"), ("tenant_id", "bigint", "企业"), ("start_date", "date", "起租日"), ("end_date", "date", "到期日")]),
            T("prop_fee", "物业收费", [("fee_no", "varchar(64)", "账单号"), ("tenant_id", "bigint", "企业"), ("amount", "decimal(12,2)", "金额"), ("status", "tinyint", "状态")]),
            T("ent_profile", "企业画像", [("tenant_id", "bigint", "企业"), ("industry", "varchar(64)", "所属行业"), ("scale", "varchar(32)", "规模")]),
        ]),
        M("IoT物模型与能耗", [
            T("dev_device", "设备台账", [("device_no", "varchar(64)", "设备编号"), ("device_type", "varchar(32)", "设备类型(14类)"), ("model_key", "varchar(64)", "物模型key"), ("status", "tinyint", "状态")]),
            T("dev_alarm", "设备告警", [("device_id", "bigint", "设备"), ("alarm_code", "varchar(64)", "告警码"), ("level", "tinyint", "级别"), ("ticket_id", "bigint", "生成工单")]),
            T("en_meter", "能耗表具", [("meter_no", "varchar(64)", "表具编号"), ("type", "varchar(32)", "水/电/气"), ("room_id", "bigint", "归属房间")]),
            T("en_reading", "能耗读数", [("meter_id", "bigint", "表具"), ("reading", "decimal(12,2)", "读数"), ("read_time", "datetime", "采集时间")]),
        ]),
        M("数据中台与驾驶舱", [
            T("bi_indicator", "指标库", [("indicator_code", "varchar(64)", "指标编码"), ("name", "varchar(128)", "指标名"), ("sql_text", "text", "取数SQL")]),
            T("bi_dashboard", "驾驶舱视图", [("dash_code", "varchar(64)", "视图编码"), ("name", "varchar(128)", "视图名(4视图共用1引擎)"), ("layout", "text", "布局JSON")]),
        ]),
    ]
}

for pack in (cm_city, park_park):
    path = OUT / (pack['key'] + '.json')
    path.write_text(json.dumps(pack, ensure_ascii=False, indent=2), encoding='utf-8')
    print('wrote', path.name, 'modules', len(pack['modules']), 'tables', sum(len(m['tables']) for m in pack['modules']))
