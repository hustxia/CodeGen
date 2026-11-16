# -*- coding: utf-8 -*-
"""
V9.11 行业模板深度扩展：解决"表太少 / 字段不全 / 生成系统像玩具"。

背景：V9.10 之前 254 套模板平均仅 8.6 张表、7.87 字段/表，
一个"医院"模板只有 9 张表，生成的系统必然空壳。

做法（保持 254 套 / 19 分类 / 幂等 / 可重跑）：
  1) 给每个模板追加【通用平台能力表】15 张：
     审批流、审批节点、附件、操作日志、通知、文档、任务、任务明细、
     反馈、标签、标签关联、数据字典、系统配置、审计追踪、站内消息。
     —— 这是任何真实业务系统都有的支撑域，不是占位表。
  2) 按模板所属分类追加【行业专属业务表】10~15 张（19 分类各一套），
     例：医疗健康补挂号/就诊/检查/检验/住院/床位/收费/手术/随访。
  3) 字段二次深化：每张表补责任人/优先级/来源/版本/生效期/附件数等通用业务列，
     与 expand_template_fields.py 的语义等价组一致，绝不碰审计列。

用法：
  python scripts/deepen_templates.py            # 原地处理全部模板（幂等）
  python scripts/deepen_templates.py --check    # 只统计，不写文件
"""
import os
import sys
import copy
import glob
import json
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "industry-templates"))

# ---------- 紧凑字段 DSL ----------
# token 规则：① 宏名直接用（类型+中文注释）② xxx_id → bigint ③ xxx_no → varchar(64)
#             ④ xxx_time/date/code/amount/qty/name 等后缀推导 ⑤ a:b → 名字 a，类型取宏 b
MACRO = {
    "code": ("varchar(64)", "编码"), "no": ("varchar(64)", "单号"),
    "name": ("varchar(128)", "名称"), "title": ("varchar(255)", "标题"),
    "subject": ("varchar(255)", "主题"), "desc": ("varchar(512)", "描述"),
    "remark": ("varchar(512)", "备注"), "content": ("text", "内容"),
    "status": ("tinyint", "状态"), "type": ("varchar(64)", "类型"),
    "level": ("tinyint", "级别"), "sort": ("int", "排序"),
    "amount": ("decimal(12,2)", "金额"), "price": ("decimal(12,2)", "单价"),
    "qty": ("int", "数量"), "count": ("int", "数量"),
    "rate": ("decimal(5,2)", "比例"), "score": ("decimal(5,2)", "评分"),
    "time": ("datetime", "时间"), "date": ("date", "日期"),
    "start": ("datetime", "开始时间"), "end": ("datetime", "结束时间"),
    "deadline": ("datetime", "截止时间"), "phone": ("varchar(20)", "手机号"),
    "email": ("varchar(128)", "邮箱"), "addr": ("varchar(255)", "地址"),
    "url": ("varchar(512)", "链接"), "person": ("varchar(64)", "负责人"),
    "ip": ("varchar(64)", "IP地址"), "result": ("varchar(512)", "处理结果"),
    "reason": ("varchar(512)", "原因"), "flag": ("tinyint", "是否标志"),
    "version": ("varchar(32)", "版本"), "json": ("text", "扩展数据"),
    "color": ("varchar(32)", "颜色"), "value": ("varchar(512)", "值"),
    "progress": ("int", "进度"), "text": ("varchar(255)", "文本"),
    "month": ("date", "月份"), "year": ("int", "年份"),
    "comment": ("varchar(512)", "意见"), "reply": ("varchar(512)", "回复"),
    "file": ("varchar(512)", "文件路径"), "ip_addr": ("varchar(64)", "IP地址"),
}

# 关联字段中文名（让 xxx_id 的注释语义化，而不是干巴巴 "关联ID"）
ID_CN = {
    "patient": "患者", "doctor": "医生", "user": "用户", "dept": "部门",
    "employee": "员工", "student": "学生", "teacher": "教师", "customer": "客户",
    "supplier": "供应商", "product": "商品", "order": "订单", "task": "任务",
    "project": "项目", "course": "课程", "class": "班级", "school": "学校",
    "room": "房间", "bed": "床位", "warehouse": "仓库", "stock": "库存",
    "material": "物料", "equipment": "设备", "contract": "合同", "approval": "审批",
    "parent": "上级", "role": "角色", "org": "组织", "company": "公司",
    "shop": "店铺", "member": "会员", "driver": "司机", "vehicle": "车辆",
    "route": "路线", "trip": "行程", "case": "案件", "client": "客户",
    "lawyer": "律师", "owner": "业主", "property": "房产", "building": "楼宇",
    "batch": "批次", "plan": "计划", "dict": "字典", "tag": "标签",
    "file": "文件", "campaign": "活动", "course": "课程", "lesson": "课时",
    "exam": "考试", "question": "题目", "paper": "试卷", "grade": "班级",
    "visa": "签证", "ticket": "票据", "invoice": "发票", "account": "账户",
    "ledger": "账目", "voucher": "凭证", "fund": "资金", "asset": "资产",
    "device": "设备", "sensor": "传感器", "plot": "地块", "crop": "作物",
    "dish": "菜品", "table": "餐桌", "guest": "宾客", "hotel": "酒店",
    "booking": "预订", "attraction": "景点", "guide": "导游", "funding": "资助",
}

# 常见业务字段中文名（避免生成出的列注释是 applicant_id 这种英文）
FIELD_CN = {
    "action": "动作", "actor_id": "操作人ID", "after_balance": "变更后余额",
    "after_data": "变更后数据", "after_sale_id": "售后单ID", "applicant_id": "申请人ID",
    "application_id": "申请ID", "approver_id": "审批人ID", "area": "面积",
    "assignee_id": "处理人ID", "auditor_id": "审计人ID", "author_id": "作者ID",
    "authors": "作者", "balance": "余额", "base_salary": "基本工资",
    "before_balance": "变更前余额", "before_data": "变更前数据", "biz_id": "业务ID",
    "biz_type": "业务类型", "bom_id": "BOM ID", "bonus": "奖金",
    "budget": "预算", "budget_id": "预算ID", "candidate_id": "候选人ID",
    "carrier": "承运商", "carrier_id": "承运商ID", "check_id": "检查ID",
    "check_in": "签到时间", "check_out": "签退时间", "checker_id": "检查人ID",
    "chief_complaint": "主诉", "city": "城市", "config_key": "配置键",
    "contact": "联系人", "cost": "成本", "coupon_id": "优惠券ID",
    "court": "法院", "creator_id": "创建人ID", "credit": "信用额度",
    "current_node": "当前节点", "custodian_id": "保管人ID", "customer_level": "客户等级",
    "cycle": "周期", "days": "天数", "debit": "借方金额", "deduction": "扣款",
    "detail": "详情", "diagnosis": "诊断", "discount": "折扣", "distance": "距离",
    "dosage": "剂量", "drug_id": "药品ID", "duration": "时长",
    "end_addr": "终点地址", "experiment_id": "实验ID", "facility_id": "设施ID",
    "farm_id": "农场ID", "fee_id": "费用ID", "file_path": "文件路径",
    "file_size": "文件大小", "file_type": "文件类型", "floor": "楼层",
    "floors": "楼层数", "from_warehouse": "源仓库", "gender": "性别",
    "grade": "年级", "handler_id": "处理人ID", "headcount": "招聘人数",
    "homework_id": "作业ID", "hours": "小时数", "images": "图片",
    "ingredient_id": "食材ID", "inspector_id": "检验人ID", "interviewer_id": "面试官ID",
    "is_default": "是否默认", "item": "项目", "job_id": "岗位ID",
    "journal": "期刊", "latitude": "纬度", "leader_id": "负责人ID",
    "lease_id": "租赁ID", "location": "位置", "longitude": "经度",
    "loss_rate": "损耗率", "manager_id": "负责人ID", "min_points": "最低积分",
    "model": "型号", "node_order": "节点顺序", "nurse_id": "护士ID",
    "object_id": "对象ID", "operation": "操作", "operator_id": "操作人ID",
    "operator_name": "操作人名称", "overtime_hours": "加班时长", "part": "部位",
    "party_a": "甲方", "party_b": "乙方", "passenger_id": "乘客ID",
    "payee_id": "收款人ID", "period": "期间", "persons": "人数", "place": "地点",
    "plan_end": "计划结束时间", "plan_start": "计划开始时间", "points": "积分",
    "position": "岗位", "preparer_id": "制单人ID", "prescription_id": "处方ID",
    "priority": "优先级", "probation": "试用期", "process_id": "工序ID",
    "production_order_id": "生产工单ID", "provider_id": "服务商ID",
    "province": "省份", "publisher": "发布人", "publisher_id": "发布人ID",
    "rank": "排名", "receiver_id": "接收人ID", "reference": "参考值",
    "reply_by": "回复人", "requirement": "要求", "resume": "简历",
    "reviewer_id": "评审人ID", "road": "道路", "room": "房间", "round": "轮次",
    "salary": "薪资", "salary_range": "薪资范围", "season": "季节",
    "seats": "座位数", "semester": "学期", "sender_id": "发送人ID",
    "sequence": "顺序", "service_id": "服务ID", "severity": "严重程度",
    "size": "大小", "sku_id": "SKU ID", "source": "来源", "spec": "规格",
    "specialty": "专长", "staff_id": "员工ID", "standard": "标准",
    "start_addr": "起点地址", "stock": "库存", "threshold": "门槛",
    "to_warehouse": "目标仓库", "total_score": "总分", "trainer_id": "培训师ID",
    "training_id": "培训ID", "unit": "单位", "unit_id": "单元ID",
    "upload_by": "上传人", "usage": "用法", "visit_id": "就诊ID",
    "visitor_id": "访客ID", "volume": "体积", "ward": "病区",
    "waybill_id": "运单ID", "weekday": "星期", "weight": "重量",
    "work_hours": "工作时长", "workshop_id": "车间ID", "before": "变更前数据",
    "after": "变更后数据", "detail": "详情", "file": "文件",
}

# FIELD_CN 中需要指定非默认类型的字段
FIELD_TYPE = {
    "priority": "tinyint", "is_default": "tinyint", "level": "tinyint",
    "images": "text", "resume": "text",
    "before_data": "text", "after_data": "text", "before": "text", "after": "text",
    "file_path": "varchar(512)", "file": "varchar(512)",
    "check_in": "datetime", "check_out": "datetime",
    "plan_start": "datetime", "plan_end": "datetime",
    "headcount": "int", "hours": "int", "days": "int", "persons": "int",
    "seats": "int", "points": "int", "min_points": "int", "floors": "int",
    "rank": "int", "sequence": "int", "size": "bigint", "file_size": "bigint",
    "latitude": "decimal(12,2)", "longitude": "decimal(12,2)",
    "area": "decimal(12,2)", "distance": "decimal(12,2)",
    "cost": "decimal(12,2)", "balance": "decimal(12,2)",
    "base_salary": "decimal(12,2)", "bonus": "decimal(12,2)",
    "deduction": "decimal(12,2)", "debit": "decimal(12,2)",
    "requirement": "varchar(512)", "reference": "varchar(512)",
    "standard": "varchar(512)", "usage": "varchar(255)",
    "position": "varchar(128)", "specialty": "varchar(128)",
    "detail": "varchar(512)", "location": "varchar(255)",
    "subject": "varchar(128)", "item": "varchar(128)",
}

AUDIT = frozenset({"id", "create_time", "update_time", "create_by", "update_by", "deleted"})


def _col(token):
    """字段 token → (name, type, comment)"""
    token = token.strip()
    if not token:
        return None
    if ":" in token:
        name, macro = token.split(":", 1)
        if macro not in MACRO:
            raise ValueError("未知字段宏 %s（token=%s）" % (macro, token))
        typ, cm = MACRO[macro]
        return (name, typ, cm)
    if token in MACRO:
        typ, cm = MACRO[token]
        return (token, typ, cm)
    if token in FIELD_CN:
        name = token
        typ = FIELD_TYPE.get(name) or (
            "bigint" if name.endswith("_id") else "varchar(64)")
        return (name, typ, FIELD_CN[token])
    if token.endswith("_id"):
        stem = token[:-3]
        return (token, "bigint", ID_CN.get(stem, stem) + "ID")
    if token.endswith("_no"):
        return (token, "varchar(64)", "单号")
    if token.endswith("_code"):
        return (token, "varchar(64)", "编码")
    if token.endswith("_name"):
        return (token, "varchar(128)", "名称")
    if token.endswith("_time"):
        return (token, "datetime", "时间")
    if token.endswith("_date"):
        return (token, "date", "日期")
    if token.endswith("_amount"):
        return (token, "decimal(12,2)", "金额")
    if token.endswith("_qty") or token.endswith("_count"):
        return (token, "int", "数量")
    return (token, "varchar(64)", token)


def _parse_block(text):
    """table|中文名|f1,f2,... → [table dict]"""
    out = []
    for line in text.strip().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) != 3:
            raise ValueError("表定义行格式错误: " + line)
        table_name, table_comment, field_str = parts
        cols = [{"name": "id", "type": "bigint", "comment": "主键", "pk": True}]
        for token in field_str.split(","):
            col = _col(token)
            if col is None or col[0] == "id":
                continue
            if any(c["name"] == col[0] for c in cols):
                continue
            cols.append({"name": col[0], "type": col[1], "comment": col[2]})
        out.append({"tableName": table_name, "tableComment": table_comment, "columns": cols})
    return out


# ---------- 通用平台能力表（任何真实业务系统必备的支撑域） ----------
COMMON_TEXT = """
approval|审批单|no,title,type,applicant_id,dept_id,amount,status,current_node,result,reason,submit_time,finish_time,remark
approval_node|审批节点|approval_id,node_name,node_order,assignee_id,status,result,comment,handle_time,remark
attachment|附件管理|biz_type,biz_id,file_name,file_path,file_size,file_type,upload_by,upload_time,status,remark
operation_log|操作日志|biz_type,biz_id,operation,operator_id,operator_name,before_data,after_data,ip,operate_time,remark
notice|通知公告|title,type,content,publisher_id,publish_time,expire_time,status,priority,read_count,remark
document|文档管理|no,title,type,version,file_path,owner_id,dept_id,status,effective_date,expire_date,remark
task|任务管理|no,title,type,priority,assignee_id,creator_id,start_time,due_time,status,progress,result,remark
task_item|任务明细|task_id,item_name,content,assignee_id,status,due_time,finish_time,remark
feedback|意见反馈|title,content,user_id,type,status,reply,reply_by,reply_time,remark
tag|标签管理|name,type,color,sort,status,remark
tag_ref|标签关联|tag_id,biz_type,biz_id,remark
dict|数据字典|type,code,name,value,sort,status,remark
config|系统配置|config_key,name,value,type,group_name,status,remark
audit_trail|审计追踪|biz_type,biz_id,action,actor_id,actor_name,detail,ip,action_time,remark
message|站内消息|title,content,sender_id,receiver_id,type,status,read_time,remark
"""

# ---------- 19 分类专属业务表 ----------
DEEP_TEXT = OrderedDict([
("通用企业", """
attendance|考勤记录|employee_id,attendance_date,check_in,check_out,status,work_hours,overtime_hours,remark
leave_request|请假申请|no,employee_id,type,start,end,days,reason,status,approver_id,remark
overtime|加班申请|no,employee_id,start,end,hours,reason,status,approver_id,remark
salary|薪资记录|no,employee_id,month,base_salary,bonus,deduction,amount,status,pay_time,remark
performance|绩效考核|no,employee_id,period,score,level,reviewer_id,comment,status,remark
recruitment|招聘需求|no,position,dept_id,headcount,requirement,salary_range,status,deadline,remark
candidate|候选人|name,phone,email,position,source,status,resume,interview_time,remark
interview|面试记录|candidate_id,position,interviewer_id,round,score,result,interview_time,remark
training|培训计划|no,title,type, trainer_id,start,end,place,status,remark
training_record|培训记录|training_id,employee_id,score,result,status,finish_time,remark
contract|劳动合同|no,employee_id,type,start,end,sign_time,status,file,remark
asset|固定资产|no,name,type,model,amount,purchase_date,status,custodian_id,dept_id,remark
asset_borrow|资产领用|asset_id,employee_id,borrow_time,return_time,status,remark
expense|费用申请|no,title,type,amount,dept_id,applicant_id,status,reason,remark
reimbursement|报销单|no,employee_id,type,amount,invoice_count,status,pay_time,remark
"""),
("电商零售", """
shop|店铺管理|no,name,type,owner_id,status,level,phone,addr,remark
product_review|商品评价|product_id,order_id,user_id,score,content,images,status,reply,remark
cart|购物车|user_id,product_id,sku_id,qty,price,status,remark
promotion|促销活动|no,title,type,start,end,status,budget,remark
coupon|优惠券|no,title,type,amount,threshold,start,end,status,remark
coupon_use|优惠券使用|coupon_id,user_id,order_id,use_time,status,remark
order_item|订单明细|order_id,product_id,sku_id,qty,price,amount,status,remark
shipment|发货单|no,order_id,express_no,carrier,amount,status,ship_time,remark
after_sale|售后申请|no,order_id,user_id,type,reason,amount,status,remark
after_sale_item|售后明细|after_sale_id,product_id,qty,amount,result,remark
store|门店管理|no,name,type,manager_id,phone,addr,status,remark
customer_level|客户等级|name,min_amount,discount,level,status,remark
"""),
("批发分销", """
supplier|供应商|no,name,type,contact,phone,addr,level,status,remark
purchase_order|采购订单|no,supplier_id,amount,status,order_time,deadline,remark
purchase_item|采购明细|order_id,product_id,qty,price,amount,status,remark
wholesale_order|批发订单|no,customer_id,amount,discount,status,order_time,remark
wholesale_item|批发明细|order_id,product_id,qty,price,amount,remark
delivery|送货单|no,order_id,warehouse_id,driver_id,status,delivery_time,remark
settlement|结算单|no,customer_id,amount,paid_amount,status,settlement_date,remark
rebate|返利记录|no,customer_id,amount,rate,period,status,remark
price_list|价格表|product_id,customer_level,price,start,end,status,remark
credit|信用额度|customer_id,limit_amount,used_amount,status,remark
sales_target|销售目标|no,employee_id,month,target_amount,actual_amount,status,remark
"""),
("制造工业", """
workshop|车间管理|no,name,code,manager_id,status,remark
production_order|生产工单|no,product_id,qty,plan_start,plan_end,status,workshop_id,remark
bom|物料清单|no,product_id,version,status,remark
bom_item|BOM明细|bom_id,material_id,qty,loss_rate,status,remark
process|工序管理|no,name,code,sequence,workshop_id,status,remark
process_plan|工序计划|production_order_id,process_id,plan_qty,assignee_id,status,remark
quality_check|质量检验|no,biz_type,biz_id,inspector_id,result,check_time,remark
defect|不良记录|no,check_id,type,qty,severity,reason,status,remark
equipment|设备台账|no,name,type,model,workshop_id,purchase_date,status,remark
equipment_maintain|设备保养|equipment_id,type,plan_date,finish_date,result,status,remark
material|物料管理|no,name,type,spec,unit,status,remark
material_flow|物料流转|no,material_id,from_warehouse,to_warehouse,qty,status,remark
"""),
("物流供应链", """
carrier|承运商|no,name,type,contact,phone,level,status,remark
waybill|运单|no,order_id,carrier_id,amount,status,ship_time,arrive_time,remark
waybill_item|运单明细|waybill_id,product_id,qty,weight,volume,remark
shipment|出库单|no,warehouse_id,order_id,status,ship_time,remark
tracking|物流轨迹|waybill_id,location,status,track_time,operator_id,remark
warehouse|仓库管理|no,name,type,manager_id,addr,status,remark
location|库位管理|warehouse_id,code,name,type,status,remark
stock|库存台账|warehouse_id,product_id,qty,lock_qty,status,remark
stock_flow|库存流水|no,product_id,warehouse_id,type,qty,before_qty,after_qty,status,remark
inventory_check|盘点单|no,warehouse_id,check_date,status,operator_id,remark
freight|运费结算|no,waybill_id,amount,status,settlement_date,remark
delivery_order|配送单|no,waybill_id,driver_id,status,delivery_time,remark
"""),
("金融财税", """
account|账户管理|no,name,type,balance,status,owner_id,remark
transaction|交易流水|no,account_id,type,amount,before_balance,after_balance,status,trade_time,remark
ledger|会计账簿|no,period,account_id,debit,credit,balance,status,remark
voucher|记账凭证|no,period,type,amount,preparer_id,status,remark
invoice|发票管理|no,type,code,amount,status,invoice_date,remark
invoice_item|发票明细|invoice_id,name,type,qty,price,amount,remark
tax|税费申报|no,period,type,amount,status,declare_date,remark
budget|预算管理|no,period,dept_id,amount,used_amount,status,remark
budget_item|预算明细|budget_id,subject,amount,used_amount,remark
payment|付款单|no,type,amount,payee_id,status,pay_time,remark
reconciliation|对账单|no,account_id,period,amount,status,remark
financial_report|财务报表|no,type,period,content,status,remark
"""),
("医疗健康", """
appointment|预约挂号|no,patient_id,dept_id,doctor_id,visit_date,period,status,remark
registration|挂号记录|no,patient_id,dept_id,doctor_id,type,amount,status,reg_time,remark
visit|就诊记录|no,patient_id,doctor_id,chief_complaint,diagnosis,visit_time,status,remark
medical_record|病历档案|no,patient_id,visit_id,type,content,doctor_id,status,remark
examination|检查申请|no,patient_id,visit_id,type,item,result,status,exam_time,remark
lab_test|检验申请|no,patient_id,visit_id,item,result,reference,status,test_time,remark
imaging|影像检查|no,patient_id,visit_id,type,part,result,status,check_time,remark
prescription_item|处方明细|prescription_id,drug_id,qty,usage,dosage,days,amount,remark
admission|住院登记|no,patient_id,dept_id,doctor_id,admission_date,status,remark
bed|床位管理|no,ward,code,type,status,patient_id,remark
nursing|护理记录|no,patient_id,bed_id,type,content,nurse_id,record_time,remark
surgery|手术记录|no,patient_id,doctor_id,type,name,plan_time,status,remark
fee|收费单|no,patient_id,type,amount,paid_amount,status,fee_time,remark
fee_item|费用明细|fee_id,item,type,qty,price,amount,status,remark
follow_up|随访记录|no,patient_id,type,content,follow_time,status,remark
"""),
("教育培训", """
school|学校管理|no,name,type,addr,phone,status,remark
class_info|班级管理|no,name,grade,teacher_id,school_id,status,remark
course|课程管理|no,name,type,teacher_id,credit,hours,status,remark
teacher|教师档案|no,name,phone,email,subject,level,status,remark
student|学生档案|no,name,gender,birth_date,phone,class_id,status,remark
enrollment|学籍注册|no,student_id,class_id,semester,status,remark
attendance|考勤记录|student_id,course_id,date,status,remark
homework|作业管理|no,course_id,teacher_id,title,content,deadline,status,remark
homework_submit|作业提交|homework_id,student_id,content,file,score,status,remark
exam|考试管理|no,course_id,name,exam_time,total_score,status,remark
exam_score|考试成绩|exam_id,student_id,score,rank,status,remark
grade|成绩单|no,student_id,semester,subject,score,level,remark
textbook|教材管理|no,name,type,publisher,price,status,remark
schedule|课表管理|course_id,teacher_id,class_id,weekday,period,room,remark
"""),
("政务民生", """
citizen|居民档案|no,name,gender,birth_date,id_no,phone,addr,status,remark
business_license|营业执照|no,company_id,code,start,end,status,remark
permit|行政许可|no,name,type,applicant_id,start,end,status,remark
application|办事申请|no,type,applicant_id,title,content,status,submit_time,remark
approval_case|审批案件|no,application_id,type,handler_id,result,status,finish_time,remark
case_material|申报材料|case_id,name,type,file,status,remark
penalty|行政处罚|no,subject,type,amount,reason,status,decision_date,remark
inspection|监督检查|no,object_id,type,inspector_id,result,status,check_time,remark
petition|信访投诉|no,title,content,applicant_id,type,status,reply,remark
subsidy|补贴发放|no,applicant_id,type,amount,status,pay_time,remark
public_notice|公示公告|no,title,content,start,end,status,remark
"""),
("房产建筑", """
property|房产档案|no,name,type,addr,area,status,remark
building|楼栋管理|no,property_id,name,code,floors,status,remark
unit|房屋单元|no,building_id,floor,code,area,type,status,remark
owner|业主档案|no,name,phone,id_no,unit_id,status,remark
lease|租赁合同|no,unit_id,owner_id,start,end,amount,status,remark
lease_payment|租金收缴|no,lease_id,period,amount,status,pay_time,remark
repair|维修工单|no,unit_id,type,content,assignee_id,status,finish_time,remark
decoration|装修管理|no,unit_id,owner_id,start,end,status,remark
property_fee|物业费|no,unit_id,period,amount,status,pay_time,remark
facility|公共设施|no,name,type,location,status,remark
inspection_plan|巡检计划|no,facility_id,period,assignee_id,status,remark
contract|工程合同|no,name,type,amount,start,end,status,remark
"""),
("餐饮酒店", """
table_info|餐桌管理|no,name,area,seats,status,remark
reservation|预订管理|no,guest_name,phone,table_id,reserve_time,persons,status,remark
order_dish|菜品明细|order_id,dish_id,qty,price,amount,status,remark
dish|菜品管理|no,name,type,price,cost,status,remark
menu|菜单管理|no,name,type,status,remark
kitchen|后厨管理|no,name,type,status,remark
stock_ingredient|食材库存|ingredient_id,warehouse_id,qty,unit,status,remark
guest|宾客档案|no,name,phone,id_no,level,status,remark
room|客房管理|no,code,type,floor,price,status,remark
room_booking|客房预订|no,room_id,guest_id,start,end,amount,status,remark
check_in|入住登记|no,guest_id,room_id,check_in_time,check_out_time,status,remark
housekeeping|客房清洁|no,room_id,staff_id,type,status,finish_time,remark
"""),
("文旅传媒", """
attraction|景点管理|no,name,type,level,addr,price,status,remark
ticket|门票管理|no,attraction_id,type,price,stock,status,remark
ticket_order|门票订单|no,ticket_id,visitor_id,qty,amount,status,remark
tour|旅游线路|no,name,type,days,price,status,remark
tour_guide|导游管理|no,name,phone,level,status,remark
hotel|酒店管理|no,name,type,level,addr,phone,status,remark
room_type|房型管理|hotel_id,name,price,area,status,remark
article|文章管理|no,title,type,author_id,content,status,publish_time,remark
media|媒体资源|no,name,type,file,size,status,remark
campaign|营销活动|no,title,type,start,end,budget,status,remark
visitor|游客档案|no,name,phone,id_no,source,status,remark
review|评价反馈|biz_type,biz_id,user_id,score,content,status,reply,remark
"""),
("交通出行", """
vehicle|车辆档案|no,plate_no,type,model,owner_id,status,remark
driver|司机档案|no,name,phone,id_no,license_no,level,status,remark
route|线路管理|no,name,start_addr,end_addr,distance,status,remark
trip|行程记录|no,route_id,vehicle_id,driver_id,start,end,status,remark
ticket|车票管理|no,trip_id,seat_no,price,status,remark
booking|订票记录|no,ticket_id,passenger_id,amount,status,remark
gps_track|定位轨迹|vehicle_id,longitude,latitude,location,track_time,remark
maintenance|车辆维保|no,vehicle_id,type,amount,status,finish_time,remark
fuel|加油记录|no,vehicle_id,amount,volume,fuel_time,remark
violation|违章记录|no,vehicle_id,type,amount,status,violation_date,remark
parking|停车记录|no,vehicle_id,location,start,end,amount,status,remark
toll|通行费|no,vehicle_id,road,amount,status,toll_time,remark
"""),
("农业环保", """
farm|农场管理|no,name,type,area,addr,manager_id,status,remark
plot|地块管理|no,farm_id,code,area,type,status,remark
crop|作物管理|no,name,type,season,cycle,status,remark
planting|种植记录|no,plot_id,crop_id,plant_date,harvest_date,status,remark
pesticide|农药管理|no,name,type,spec,stock,status,remark
fertilizer|肥料管理|no,name,type,spec,stock,status,remark
harvest|采收记录|no,plot_id,crop_id,qty,harvest_date,status,remark
sensor|监测设备|no,code,type,location,status,remark
monitor_record|监测记录|sensor_id,type,value,unit,record_time,status,remark
waste|废弃物管理|no,type,qty,source,status,handle_time,remark
emission|排放记录|no,type,value,standard,status,record_time,remark
treatment|治理记录|no,type,content,amount,status,finish_time,remark
"""),
("人力资源", """
recruitment_plan|招聘计划|no,position,dept_id,headcount,deadline,status,remark
job|岗位管理|no,name,type,dept_id,level,salary_range,status,remark
candidate|候选人|name,phone,email,position,source,status,resume,remark
interview|面试安排|candidate_id,job_id,interviewer_id,round,score,result,interview_time,remark
offer|录用通知|no,candidate_id,job_id,salary,status,send_time,remark
onboarding|入职登记|no,employee_id,start,probation,status,remark
employee|员工档案|no,name,gender,birth_date,phone,dept_id,position,status,remark
contract|劳动合同|no,employee_id,type,start,end,status,remark
attendance|考勤记录|employee_id,date,check_in,check_out,status,remark
leave|请假记录|no,employee_id,type,start,end,days,status,remark
salary|薪资档案|no,employee_id,month,amount,status,pay_time,remark
performance|绩效评价|no,employee_id,period,score,level,status,remark
training|培训记录|no,employee_id,title,content,score,status,remark
resignation|离职登记|no,employee_id,type,reason,status,leave_date,remark
"""),
("法律法务", """
case_info|案件管理|no,name,type,client_id,lawyer_id,amount,status,remark
client|客户管理|no,name,type,contact,phone,level,status,remark
lawyer|律师档案|no,name,phone,specialty,level,status,remark
contract|合同管理|no,name,type,party_a,party_b,amount,start,end,status,remark
contract_item|合同条款|contract_id,title,content,sequence,remark
litigation|诉讼管理|no,case_id,court,amount,status,hearing_date,remark
evidence|证据材料|case_id,name,type,file,source,status,remark
hearing|庭审记录|case_id,type,content,result,hearing_time,remark
judgment|判决结果|case_id,type,content,amount,result,status,remark
legal_document|法律文书|no,case_id,type,title,file,status,remark
bill|账单管理|no,case_id,client_id,amount,status,remark
legal_expense|法务费用|no,case_id,type,amount,status,pay_time,remark
"""),
("科研IT", """
project|科研项目|no,name,type,leader_id,start,end,budget,status,remark
research|研究方向|no,name,type,owner_id,status,remark
experiment|实验管理|no,project_id,name,type,owner_id,status,remark
experiment_record|实验记录|experiment_id,content,result,status,record_time,remark
dataset|数据集|no,name,type,size,owner_id,status,remark
paper|论文管理|no,title,authors,journal,status,publish_time,remark
patent|专利管理|no,name,type,code,status,apply_date,remark
achievement|成果管理|no,name,type,level,owner_id,status,remark
lab|实验室管理|no,name,type,manager_id,status,remark
grant|科研经费|no,project_id,amount,source,status,remark
milestone|项目里程碑|project_id,name,plan_date,finish_date,status,remark
"""),
("生活服务", """
service|服务管理|no,name,type,price,duration,status,remark
provider|服务商|no,name,type,contact,phone,level,status,remark
booking|服务预约|no,service_id,provider_id,user_id,start,end,status,remark
service_order|服务订单|no,service_id,user_id,amount,status,order_time,remark
review|服务评价|service_id,user_id,score,content,status,reply,remark
coupon|优惠券|no,title,type,amount,threshold,start,end,status,remark
member|会员管理|no,name,phone,level,points,status,remark
membership|会员等级|name,min_points,discount,level,status,remark
address|收货地址|user_id,name,phone,province,city,detail,is_default,status,remark
favorite|收藏记录|user_id,biz_type,biz_id,status,remark
complaint|投诉建议|no,title,content,user_id,type,status,reply,remark
refund|退款申请|no,order_id,user_id,amount,reason,status,remark
"""),
("垂直行业", """
industry_project|行业项目|no,name,type,owner_id,start,end,amount,status,remark
industry_task|行业任务|no,project_id,title,assignee_id,deadline,status,remark
industry_report|行业报表|no,name,type,period,content,status,remark
industry_metric|行业指标|no,name,type,value,unit,period,status,remark
industry_standard|行业标准|no,name,code,type,version,status,remark
industry_audit|行业审计|no,type,object_id,result,auditor_id,status,remark
industry_license|行业资质|no,name,code,owner_id,start,end,status,remark
industry_cert|认证证书|no,name,code,owner_id,start,end,status,remark
industry_risk|风险台账|no,name,type,level,owner_id,status,remark
industry_check|检查记录|no,type,object_id,checker_id,result,status,remark
"""),
])

# ---------- 字段二次深化 ----------
# 语义等价组：组内任一列存在就不补（避免 amount/total_amount 这类语义重复）
ENRICH_RULES = (
    (("owner_id", "assignee_id", "manager_id", "responsible_id", "user_id", "employee_id",
      "operator_id", "actor_id", "creator_id", "applicant_id", "handler_id", "reviewer_id",
      "inspector_id", "leader_id", "publisher_id", "sender_id", "trainer_id", "nurse_id",
      "staff_id", "approver_id", "auditor_id", "custodian_id", "driver_id", "author_id",
      "interviewer_id", "checker_id", "preparer_id", "upload_by", "reply_by"),
     "owner_id", "bigint", "责任人ID"),
    (("priority", "priority_level", "importance"),
     "priority", "tinyint", "优先级"),
    (("source", "source_type", "channel", "origin"),
     "source", "varchar(64)", "来源"),
    (("version", "version_no"),
     "version", "varchar(32)", "版本"),
    (("effective_date", "valid_from", "start_date"),
     "effective_date", "date", "生效日期"),
    (("expire_date", "valid_to", "end_date"),
     "expire_date", "date", "失效日期"),
    (("attachment_count", "file_count", "attach_count"),
     "attachment_count", "int", "附件数"),
)


# 辅助/明细表标记：这类表补 priority、attachment_count、生效期没有业务意义
AUX_MARKERS = ("log", "trail", "ref", "dict", "config", "flow", "operation",
               "attachment", "_item", "_detail", "_line", "流水", "日志", "明细", "关联")


def _is_auxiliary(table):
    name = table.get("tableName", "").lower()
    comment = table.get("tableComment", "")
    return any(m in name for m in AUX_MARKERS) or any(m in comment for m in ("流水", "日志", "明细"))


def enrich_table(table):
    """按语义等价组补通用业务列（幂等，绝不碰审计列）。返回新增列数。"""
    cols = table.get("columns")
    if not cols:
        return 0
    names = {c["name"] for c in cols}
    aux = _is_auxiliary(table)
    added = 0
    for group, name, typ, comment in ENRICH_RULES:
        if name in AUDIT or name in names:
            continue
        if aux and name in ("priority", "attachment_count", "effective_date", "expire_date"):
            continue  # 日志/明细/关联表不补这些字段，避免"附件表有附件数"这类假字段
        if any(g in names for g in group):
            continue
        if name in ("effective_date", "expire_date") and "status" not in names:
            continue  # 无状态表不补生效期
        cols.append({"name": name, "type": typ, "comment": comment})
        names.add(name)
        added += 1
    return added


def deepen_vo(vo):
    """给单个模板 VO 追加通用表 + 分类专属表 + 字段二次深化。返回(新增表数, 新增字段数)。"""
    category = vo.get("category") or "通用企业"
    existing = set()
    for module in vo.get("modules", []):
        for table in module.get("tables", []):
            existing.add(table["tableName"])

    result = []
    for title, text in (("平台通用能力", COMMON_TEXT), (category + "业务深化", DEEP_TEXT.get(category, ""))):
        added = []
        for table in _parse_block(text):
            if table["tableName"] in existing:
                continue
            existing.add(table["tableName"])
            added.append(table)
        if added:
            result.append({"name": title, "tables": added})

    # 字段二次深化：对全部表（含原有表）补通用业务列
    field_added = 0
    for module in vo.get("modules", []) + result:
        for table in module.get("tables", []):
            field_added += enrich_table(table)

    vo.setdefault("modules", []).extend(result)
    return sum(len(m["tables"]) for m in result), field_added


def _count(vo):
    return (
        sum(len(m["tables"]) for m in vo.get("modules", [])),
        sum(len(t["columns"]) for m in vo.get("modules", []) for t in m["tables"]),
    )


def main():
    check_only = "--check" in sys.argv
    files = sorted(glob.glob(os.path.join(OUT_DIR, "*.json")))
    total_added_tables = total_added_fields = 0
    before_t = before_c = after_t = after_c = 0
    for path in files:
        with open(path, encoding="utf-8") as f:
            vo = json.load(f, object_pairs_hook=OrderedDict)
        b_t, b_c = _count(vo)
        added_t, added_f = deepen_vo(vo)
        a_t, a_c = _count(vo)
        before_t += b_t; before_c += b_c; after_t += a_t; after_c += a_c
        total_added_tables += added_t; total_added_fields += added_f
        if added_t or added_f:
            if not check_only:
                with open(path, "w", encoding="utf-8", newline="\n") as f:
                    json.dump(vo, f, ensure_ascii=False, indent=2)
                    f.write("\n")

    print("模板套数=%d" % len(files))
    print("表数: %d → %d (+%d)" % (before_t, after_t, total_added_tables))
    print("字段数: %d → %d (+%d)" % (before_c, after_c, total_added_fields))
    print("平均每套表数: %.1f → %.1f" % (before_t / len(files), after_t / len(files)))
    print("平均每表字段: %.2f → %.2f" % (before_c / before_t, after_c / after_t))
    print("模式:", "检查(未写)" if check_only else "已写回")


if __name__ == "__main__":
    main()
