# -*- coding: utf-8 -*-
"""
V9.12 智慧村务管理系统行业模板生成器（幂等，可重跑）。

产出 src/main/resources/industry-templates/village_affairs.json：
  - 70 张村务专属表（基础数据底座 17 / 党建与治理 20 / 业务监管 23 / 决策服务 10）
  - 15 张平台通用能力表（复用 deepen_templates.COMMON_TEXT）
  - 字段二次深化复用 deepen_templates.enrich_table（语义等价组、不碰审计列）

设计目标：解决"表太少 / 字段不全 / 生成系统像玩具"，每张表 ≥8 列、
带 status/remark，主键为首列，业务列不与 BaseEntity 审计列冲突。

用法：
  python scripts/gen_village_template.py            # 生成/覆盖 village_affairs.json
  python scripts/gen_village_template.py --check    # 只统计校验，不写文件
"""
import os
import sys
import json
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import deepen_templates as D  # noqa: E402  (复用 DSL / 字段深化 / 通用表)

OUT = os.path.normpath(os.path.join(
    HERE, "..", "src", "main", "resources", "industry-templates", "village_affairs.json"))

# ---------------------------------------------------------------------------
# 村务专属字段词库：让列注释是中文而不是英文 token
# ---------------------------------------------------------------------------
VILLAGE_CN = {
    # 业务实体中文名（xxx_id 的注释来源）
    "village": "村", "household": "户籍户", "resident": "村民", "grid": "网格",
    "parcel": "地块", "contract": "合同", "asset": "资产", "resource": "资源",
    "meeting": "会议", "motion": "议案", "vote": "表决", "dispute": "纠纷",
    "mediator": "调解员", "disclosure": "公开信息", "archive": "档案",
    "org": "党组织", "member": "党员", "activity": "活动", "task": "任务",
    "item": "服务事项", "apply": "办事申请", "inspect": "检查", "issue": "问题",
    "account": "资金账户", "plan": "考核方案", "metric": "大屏指标",
    "snapshot": "数据快照", "leader": "负责人", "assigner": "派发人",
    "target": "考核对象", "voter": "表决人", "checker": "检查人",
    "clean": "保洁任务", "facility": "环卫设施", "agent": "代办员",
    "blood": "血缘关系",  # placeholder, not used
    # 普通业务字段中文名
    "id_card": "身份证号", "head_name": "户主姓名", "head_id_card": "户主身份证号",
    "ethnicity": "民族", "education": "文化程度", "occupation": "职业",
    "relation": "与户主关系", "change_type": "变动类型", "handler": "经办人",
    "group_type": "群体类别", "subsidy_amount": "补贴金额", "health": "健康状况",
    "guardian": "监护人", "structure": "房屋结构", "build_year": "建成年份",
    "usage": "房屋用途", "owner_name": "产权人", "east_to": "东至", "west_to": "西至",
    "south_to": "南至", "north_to": "北至", "contractor": "承包方",
    "purpose": "承包用途", "transfer_from": "转出方", "transfer_to": "转入方",
    "grid_head": "网格长", "reporter": "上报人", "borrower": "借阅人",
    "secretary": "党组织书记", "recorder": "记录人", "host": "主持人",
    "organizer": "组织人", "fee_month": "缴费月份", "pay_method": "缴费方式",
    "assess_type": "评议类型", "assessor": "评议人", "service_type": "服务类型",
    "service_time": "服务时长", "service_content": "服务内容", "proposer": "提案人",
    "second": "附议人", "yes_count": "赞成票数", "no_count": "反对票数",
    "abstain_count": "弃权票数", "agree_flag": "是否达成一致", "visitor": "回访人",
    "satisfaction": "满意度", "quantity": "数量", "before_value": "变更前价值",
    "after_value": "变更后价值", "actual_qty": "实盘数量", "book_qty": "账面数量",
    "profit_loss": "盘盈盘亏", "pay_type": "付款方式", "payer": "付款方",
    "receiver": "收款方", "account_name": "账户名称", "bank": "开户行",
    "direction": "收支方向", "summary": "摘要", "department": "预算部门",
    "used_amount": "已用金额", "item_code": "事项编码", "item_name": "事项名称",
    "limit_days": "承诺时限", "fee": "收费标准", "channel": "办理渠道",
    "applicant_name": "申请人姓名", "node_name": "环节名称", "evaluate_date": "评价日期",
    "agent_name": "代办员姓名", "scope": "服务范围", "cleaner": "保洁员",
    "clean_type": "清扫类型", "area_size": "清扫面积", "condition": "设施状况",
    "requirement": "任务要求", "metric_code": "指标编码", "metric_name": "指标名称",
    "target": "目标值", "refresh_cycle": "刷新周期", "dimension": "统计维度",
    "log_no": "民情编号", "problem": "反映问题", "tag_name": "标签名称",
    "plan_no": "方案编号", "target_name": "考核对象名称", "indicator": "考核指标",
    "publish_date": "发布日期", "report_no": "报表编号", "period": "统计周期",
    "leader_phone": "负责人电话", "member_name": "成员姓名", "file_name": "文件名称",
    "village_name": "村名", "apply_no": "申请编号", "inventory_no": "盘点单号",
    # 后缀型 / 组合型字段（_col 无内置中文，须显式声明）
    "activity_type": "活动类型", "archive_type": "档案类型", "area_level": "行政层级",
    "area_type": "区域类型", "boundary": "管辖边界", "business_type": "经营范围",
    "category": "类别", "contract_status": "发包状态", "data_json": "扩展数据",
    "description": "描述说明", "dispute_type": "纠纷类型", "event_type": "事件类型",
    "from_addr": "原住址", "handle_status": "办理状态", "household_type": "户籍类型",
    "households": "户数", "is_required": "是否必需", "land_type": "土地类型",
    "learning_type": "学习类型", "legal_person": "法定代表人", "material": "申报材料",
    "meeting_type": "会议类型", "method": "调解方式", "org_type": "组织类型",
    "party_status": "党籍状态", "population": "人口数", "registered_capital": "注册资本",
    "resource_type": "资源类型", "responsibility": "职责范围", "retention": "保管期限",
    "role": "担任角色", "secret_level": "密级", "to_addr": "现住址",
    "transfer_type": "流转方式", "vote_option": "表决选项", "vote_result": "表决结果",
    "vote_type": "表决方式", "scope": "公开范围",
    # 外键/关联字段：注释用业务管理名而非 xxxID，表单与详情才知道显示什么中文标签
    "account_id": "资金账户", "activity_id": "关联活动", "actor_id": "操作人",
    "applicant_id": "申请人", "apply_id": "关联申请", "approval_id": "关联审批",
    "approver_id": "审批人", "archive_id": "关联档案", "asset_id": "关联资产",
    "assignee_id": "处理人", "assigner_id": "派发人", "biz_id": "关联业务",
    "checker_id": "检查人", "contract_id": "关联合同", "contractor_id": "承包方",
    "creator_id": "创建人", "custodian_id": "保管人", "dept_id": "所属部门",
    "disclosure_id": "关联公开信息", "dispute_id": "关联纠纷", "from_org_id": "来源组织",
    "grid_id": "所属网格", "handler_id": "处理人", "household_id": "所属户籍户",
    "ip": "IP地址", "item_id": "关联服务事项", "mediator_id": "调解员",
    "meeting_id": "关联会议", "member_id": "关联党员", "metric_id": "关联指标",
    "motion_id": "关联议案", "operator_id": "操作人", "org_id": "所属党组织",
    "owner_id": "产权人", "parcel_id": "关联地块", "parent_id": "上级节点",
    "plan_id": "关联考核方案", "publisher_id": "发布人", "receiver_id": "接收人",
    "reporter_id": "上报人", "resident_id": "关联村民", "reviewer_id": "评议人",
    "sender_id": "发送人", "tag_id": "关联标签", "target_id": "关联对象",
    "task_id": "关联任务", "to_org_id": "目标组织", "user_id": "关联用户",
    "village_id": "所属村", "visitor_id": "回访人", "vote_id": "关联表决",
    "voter_id": "表决人", "ip_addr": "IP地址",
}
VILLAGE_TYPES = {
    "population": "int", "households": "int", "member_count": "int",
    "employee_count": "int", "floors": "int", "attend_count": "int",
    "case_count": "int", "service_count": "int", "yes_count": "int",
    "no_count": "int", "abstain_count": "int", "quantity": "int",
    "actual_qty": "int", "book_qty": "int", "limit_days": "int",
    "hours": "int", "build_year": "int", "service_time": "int",
    "subsidy_amount": "decimal(12,2)", "registered_capital": "decimal(12,2)",
    "before_value": "decimal(12,2)", "after_value": "decimal(12,2)",
    "used_amount": "decimal(12,2)", "fee": "decimal(12,2)",
    "area_size": "decimal(12,2)", "profit_loss": "decimal(12,2)",
    "fee_month": "date", "leader_phone": "varchar(20)",
    "data_json": "text", "description": "varchar(512)", "is_required": "tinyint",
}
D.FIELD_CN.update(VILLAGE_CN)
D.FIELD_TYPE.update(VILLAGE_TYPES)

# ---------------------------------------------------------------------------
# 70 张村务专属表（DSL：表名|中文名|字段列表）
# ---------------------------------------------------------------------------
BASE_TEXT = """
village_area|村组区划|area_code,area_name,parent_id,area_level,area_type,population,households,leader,leader_phone,sort,status,remark
village_household|户籍户档案|household_no,head_name,head_id_card,member_count,addr,area,household_type,village_id,grid_id,status,remark
village_resident|村民人口档案|resident_name,id_card,gender,birth_date,ethnicity,phone,education,occupation,household_id,village_id,grid_id,status,remark
village_resident_relation|家庭成员关系|resident_id,household_id,relation,name,id_card,phone,source,status,remark
village_population_change|人口变动记录|resident_id,change_type,change_date,from_addr,to_addr,reason,handler,status,remark
village_special_group|特殊群体台账|resident_id,group_type,category,subsidy_amount,level,health,guardian,phone,status,remark
village_enterprise|村集体经营主体|enterprise_name,credit_code,legal_person,business_type,registered_capital,addr,contact,phone,employee_count,status,remark
village_house|农村房屋台账|house_no,household_id,addr,structure,area,floors,build_year,usage,owner_name,status,remark
village_land_parcel|土地确权地块|parcel_no,name,area,land_type,location,east_to,west_to,south_to,north_to,owner_id,status,remark
village_land_contract|土地承包合同|contract_no,parcel_id,contractor,contractor_id,area,start,end,amount,purpose,sign_date,status,remark
village_land_transfer|土地流转备案|transfer_no,parcel_id,transfer_from,transfer_to,transfer_type,area,amount,start,end,status,remark
village_grid|治理网格|grid_code,grid_name,village_id,grid_head,phone,population,households,boundary,sort,status,remark
village_grid_member|网格员管理|grid_id,member_name,id_card,phone,role,responsibility,join_date,status,remark
village_grid_event|网格事件上报|event_no,grid_id,event_type,title,desc,location,reporter,report_time,handler_id,level,result,status,remark
village_archive|村务档案目录|archive_no,title,category,archive_type,owner_name,file_date,retention,secret_level,status,remark
village_archive_file|档案附件清单|archive_id,file_name,file_path,file_type,file_size,upload_by,upload_time,status,remark
village_archive_borrow|档案借阅登记|archive_id,borrower,borrow_date,return_date,purpose,approver_id,status,remark
"""

PARTY_TEXT = """
village_party_org|村党组织|org_name,org_type,parent_id,village_id,secretary,member_count,phone,addr,status,remark
village_party_member|党员档案|member_no,name,gender,birth_date,id_card,phone,education,join_date,party_status,org_id,status,remark
village_party_member_change|党员组织关系变动|member_id,change_type,from_org_id,to_org_id,change_date,reason,handler,status,remark
village_party_meeting|三会一课记录|meeting_no,meeting_type,title,subject,meeting_date,host,recorder,attend_count,location,content,status,remark
village_party_activity|主题党日活动|activity_no,title,activity_type,start,end,location,organizer,attend_count,content,status,remark
village_party_learning|党员学习记录|title,learning_type,content,start,end,attend_count,material,status,remark
village_party_fee|党费缴纳记录|member_id,fee_month,amount,pay_date,pay_method,recorder,status,remark
village_party_assess|党员民主评议|member_id,year,score,level,assess_type,assessor,assess_date,comment,status,remark
village_party_volunteer|党员志愿服务|activity_id,member_id,service_type,service_time,hours,service_content,location,status,remark
village_disclosure|三务公开信息|disclosure_no,title,category,type,content,publisher_id,publish_time,scope,priority,status,remark
village_disclosure_file|公开信息附件|disclosure_id,file_name,file_path,file_type,file_size,sort,status,remark
village_disclosure_feedback|公开意见反馈|disclosure_id,user_id,content,type,reply,reply_by,reply_time,status,remark
village_meeting|村民代表会议|meeting_no,meeting_type,title,subject,meeting_date,host,location,attend_count,content,result,status,remark
village_motion|村民议议案|motion_no,meeting_id,title,content,proposer,second,vote_result,status,remark
village_vote|民主表决事项|vote_no,motion_id,title,vote_type,yes_count,no_count,abstain_count,result,vote_time,status,remark
village_vote_record|表决明细记录|vote_id,voter_id,voter_name,vote_option,vote_time,status,remark
village_dispute|矛盾纠纷案件|case_no,title,dispute_type,party_a,party_b,desc,source,level,status,remark
village_dispute_mediator|人民调解员档案|mediator_name,id_card,phone,specialty,org,case_count,level,status,remark
village_dispute_record|纠纷调解记录|dispute_id,mediator_id,method,content,result,agree_flag,record_date,status,remark
village_dispute_visit|调解结案回访|dispute_id,visit_date,visitor,satisfaction,content,result,status,remark
"""

BUSINESS_TEXT = """
village_asset|村集体固定资产|asset_no,asset_name,category,type,model,amount,quantity,purchase_date,custodian_id,location,status,remark
village_asset_change|资产变动登记|asset_id,change_type,reason,before_value,after_value,change_date,handler_id,status,remark
village_asset_inventory|资产盘点记录|inventory_no,asset_id,inventory_date,checker_id,actual_qty,book_qty,profit_loss,result,status,remark
village_resource|村集体资源性资产|resource_no,name,resource_type,location,area,owner_id,contract_status,amount,status,remark
village_contract|集体经济合同|contract_no,name,type,party_a,party_b,start,end,amount,pay_type,sign_date,status,remark
village_contract_payment|合同款项收付|contract_id,payment_no,amount,pay_date,pay_method,payer,receiver,status,remark
village_fund_account|村集体资金账户|account_no,account_name,bank,type,balance,owner_id,open_date,status,remark
village_fund_transaction|资金收支流水|transaction_no,account_id,type,direction,amount,before_balance,after_balance,summary,transaction_date,status,remark
village_fund_budget|村级年度财务预算|budget_no,year,department,category,amount,used_amount,balance,status,remark
village_service_item|便民服务事项|item_code,item_name,category,type,department,limit_days,fee,material,channel,status,remark
village_service_apply|便民办事申请|apply_no,item_id,applicant_id,applicant_name,phone,apply_date,material,handler_id,result,status,remark
village_service_material|办事申报材料|apply_id,material_name,file_path,file_type,is_required,submit_date,reviewer_id,status,remark
village_service_flow|办事办理环节|apply_id,node_name,node_order,handler_id,action,result,comment,handle_time,status,remark
village_service_evaluate|办件评价反馈|apply_id,user_id,score,level,content,evaluate_date,reply,status,remark
village_service_agent|帮办代办员管理|agent_name,id_card,phone,village_id,scope,service_count,level,status,remark
village_env_inspect|人居环境检查|inspect_no,type,location,checker_id,inspect_date,result,level,problem,status,remark
village_env_issue|环境问题工单|issue_no,type,location,description,level,reporter_id,handler_id,deadline,result,status,remark
village_env_clean_task|保洁清运任务|task_no,area,cleaner,clean_date,clean_type,area_size,hours,result,status,remark
village_env_facility|环卫设施台账|facility_no,name,type,location,quantity,install_date,custodian_id,condition,status,remark
village_task|重点工作督办|task_no,title,type,source,content,assignee_id,start,due_time,priority,progress,result,status,remark
village_task_assign|督办任务分派|task_id,assignee_id,assigner_id,assign_date,requirement,deadline,result,status,remark
village_task_feedback|督办办理反馈|task_id,user_id,content,file_path,feedback_date,result,status,remark
village_task_score|督办考核评分|task_id,score,level,reviewer_id,score_date,comment,status,remark
"""

DECISION_TEXT = """
village_dashboard_metric|村情大屏指标|metric_code,metric_name,category,type,value,unit,target,refresh_cycle,sort,status,remark
village_dashboard_snapshot|大屏数据快照|snapshot_date,metric_id,value,dimension,data_json,status,remark
village_mood_log|民情日志记录|log_no,resident_id,type,content,location,level,reporter_id,handle_status,result,status,remark
village_mood_visit|民情走访记录|resident_id,visitor_id,visit_date,type,content,problem,result,satisfaction,status,remark
village_mood_tag|民情标签管理|tag_name,type,resident_id,color,sort,status,remark
village_assess_plan|村干考核方案|plan_no,name,type,year,target,indicator,start,end,status,remark
village_assess_score|考核打分明细|plan_id,target_id,target_name,score,level,reviewer_id,assess_date,comment,status,remark
village_assess_result|考核结果汇总|plan_id,target_id,target_name,total_score,level,rank,result,publish_date,status,remark
village_notice|村务通知公告|notice_no,title,type,content,publisher_id,publish_time,expire_time,scope,priority,status,remark
village_stat_report|村务统计报表|report_no,name,type,period,start,end,data_json,file_path,creator_id,status,remark
"""

BLOCKS = (
    ("基础数据底座", BASE_TEXT),
    ("党建与治理", PARTY_TEXT),
    ("业务监管", BUSINESS_TEXT),
    ("决策服务", DECISION_TEXT),
    ("平台通用能力", D.COMMON_TEXT),
)


def build():
    modules = []
    seen_tables = set()
    for name, text in BLOCKS:
        tables = D._parse_block(text)
        for t in tables:
            if t["tableName"] in seen_tables:
                raise ValueError("重复表名：" + t["tableName"])
            seen_tables.add(t["tableName"])
            D.enrich_table(t)
        modules.append({"name": name, "tables": tables})
    return OrderedDict([
        ("key", "village_affairs"),
        ("name", "智慧村务管理"),
        ("category", "政务民生"),
        ("desc", "智慧村务管理系统：人口户籍、三资管理、党建治理、三务公开、便民服务、人居环境、民情决策等村级治理全场景"),
        ("modules", modules),
    ])


def audit(vo):
    # 与 IndustryTemplateRegistryTest 一致：审计列排除主键 id（BaseEntity 只管这 5 列）
    audit_cols = {"create_time", "update_time", "create_by", "update_by", "deleted"}
    n_tables = n_cols = n_status = n_remark = 0
    problems = []
    for m in vo["modules"]:
        for t in m["tables"]:
            n_tables += 1
            cols = t["columns"]
            n_cols += len(cols)
            names = {c["name"] for c in cols}
            if len(names) != len(cols):
                problems.append(t["tableName"] + " 重复列")
            if cols[0]["name"] != "id" or not cols[0].get("pk"):
                problems.append(t["tableName"] + " 首列非主键")
            if len(cols) < 8:
                problems.append("%s 仅 %d 列" % (t["tableName"], len(cols)))
            if any(c in audit_cols for c in names):
                problems.append(t["tableName"] + " 撞审计列")
            if any(n == "status" or n.endswith("_status") for n in names):
                n_status += 1
            if any("remark" in n for n in names):
                n_remark += 1
            for c in cols:
                if not c.get("comment"):
                    problems.append("%s.%s 缺注释" % (t["tableName"], c["name"]))
                elif c["comment"] == c["name"] and not c["name"].endswith("_id"):
                    problems.append("%s.%s 注释未中文化" % (t["tableName"], c["name"]))
    return n_tables, n_cols, n_status, n_remark, problems


def main():
    vo = build()
    n_tables, n_cols, n_status, n_remark, problems = audit(vo)
    print("模板=%s 模块=%d 表=%d 字段=%d 平均字段=%.2f" % (
        vo["key"], len(vo["modules"]), n_tables, n_cols, n_cols / n_tables))
    print("status 覆盖=%.1f%% remark 覆盖=%.1f%%" % (
        n_status * 100.0 / n_tables, n_remark * 100.0 / n_tables))
    if problems:
        print("校验问题 %d 项：" % len(problems))
        for p in problems[:50]:
            print("  - " + p)
        sys.exit(1)
    print("校验通过，无重复表/列、无审计列冲突、注释完整")
    if "--check" in sys.argv:
        print("模式：检查（未写文件）")
        return
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(vo, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("已写出：" + OUT)


if __name__ == "__main__":
    main()
