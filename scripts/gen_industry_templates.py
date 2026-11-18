#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
行业模板库生成器（V9.9）——把「可复用领域蓝图」编排成 200+ 套行业模板 JSON。

设计要点
--------
1. 真源是本脚本：领域蓝图(BP) + 行业目录(INDUSTRIES)。生成物是
   src/main/resources/industry-templates/*.json（自包含、可被 Registry 直接加载）。
2. 现有 4 套（ecommerce/erp/crm/cms）是历史基线，保持原样不覆盖（SKIP_KEYS）。
3. 每个行业 = 若干通用蓝图 + 若干行业专属蓝图；同一模板内按 tableName 去重，
   每张表首列固定 id(bigint,pk)，审计列由建库链路自动补齐（与 AI 建模产物同形）。
4. key 唯一、小写 ascii；category 用于前端分组；desc 由模块名自动生成。

重跑： python scripts/gen_industry_templates.py
校验： 脚本末尾会断言 key 唯一 / 表内唯一 / 规模落在 200~300。
"""
from __future__ import annotations

import json
import os
from collections import OrderedDict

# V9.10：生成时同步展开业务字段（幂等，历史4套由 expand_template_fields.py CLI 原地展开）
# V9.11：生成时同步追加深化表（通用平台域 + 分类专属域 + 字段二次深化）
from expand_template_fields import expand_table
from deepen_templates import deepen_vo

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "industry-templates"))
SKIP_KEYS = {"ecommerce", "erp", "crm", "cms"}  # 历史 4 套原样保留


# ---------- 蓝图 DSL ----------
def C(n, t, cm):
    return (n, t, cm)


def T(name, comment, *cols):
    columns = [{"name": "id", "type": "bigint", "comment": "主键", "pk": True}]
    for col in cols:
        if col[0] == "id":
            continue
        columns.append({"name": col[0], "type": col[1], "comment": col[2]})
    return {"tableName": name, "tableComment": comment, "columns": columns}


def BP(name, *tables):
    return {"name": name, "tables": list(tables)}


BIG = "bigint"
V64 = "varchar(64)"
V128 = "varchar(128)"
V255 = "varchar(255)"
V512 = "varchar(512)"
TXT = "text"
TINY = "tinyint"
INT = "int"
DEC = "decimal(12,2)"
DT = "datetime"
DATE = "date"

# ---------- 通用领域蓝图（跨行业复用） ----------
BP_LIB = OrderedDict()

BP_LIB["customer"] = BP("客户管理",
    T("customer", "客户档案", C("customer_name", V128, "客户名称"), C("customer_code", V64, "客户编码"),
      C("customer_type", TINY, "客户类型"), C("level_id", BIG, "客户等级"), C("contact_name", V64, "联系人"),
      C("contact_phone", V64, "联系电话"), C("email", V128, "邮箱"), C("address", V255, "地址"),
      C("status", TINY, "状态"), C("remark", V512, "备注")),
    T("customer_contact", "客户联系人", C("customer_id", BIG, "客户"), C("contact_name", V64, "联系人"),
      C("contact_phone", V64, "电话"), C("position", V64, "职务"), C("is_primary", TINY, "是否主联系人")))

BP_LIB["supplier"] = BP("供应商管理",
    T("supplier", "供应商档案", C("supplier_name", V128, "供应商名称"), C("supplier_code", V64, "供应商编码"),
      C("supplier_type", TINY, "供应商类型"), C("contact_name", V64, "联系人"), C("contact_phone", V64, "联系电话"),
      C("bank_account", V128, "银行账号"), C("status", TINY, "状态"), C("remark", V512, "备注")))

BP_LIB["product"] = BP("商品管理",
    T("product_category", "商品分类", C("parent_id", BIG, "上级分类"), C("category_name", V64, "分类名称"),
      C("category_code", V64, "分类编码"), C("sort", INT, "排序")),
    T("product", "商品档案", C("product_name", V128, "商品名称"), C("product_code", V64, "商品编码"),
      C("category_id", BIG, "商品分类"), C("spec", V128, "规格型号"), C("unit", V64, "单位"),
      C("sale_price", DEC, "销售价"), C("cost_price", DEC, "成本价"), C("status", TINY, "状态"),
      C("remark", V512, "备注")),
    T("product_sku", "商品SKU", C("product_id", BIG, "商品"), C("sku_code", V64, "SKU编码"),
      C("sku_name", V128, "SKU名称"), C("price", DEC, "价格"), C("stock_num", INT, "库存数")))

BP_LIB["order"] = BP("订单管理",
    T("sales_order", "销售订单", C("order_no", V64, "订单号"), C("customer_id", BIG, "客户"),
      C("order_amount", DEC, "订单金额"), C("order_status", TINY, "订单状态"), C("pay_status", TINY, "支付状态"),
      C("order_time", DT, "下单时间"), C("remark", V512, "备注")),
    T("sales_order_item", "订单明细", C("order_id", BIG, "订单"), C("product_id", BIG, "商品"),
      C("quantity", INT, "数量"), C("unit_price", DEC, "单价"), C("amount", DEC, "金额")))

BP_LIB["purchase"] = BP("采购管理",
    T("purchase_order", "采购订单", C("order_no", V64, "采购单号"), C("supplier_id", BIG, "供应商"),
      C("total_amount", DEC, "采购金额"), C("order_status", TINY, "状态"), C("order_time", DT, "下单时间")),
    T("purchase_order_item", "采购明细", C("order_id", BIG, "采购订单"), C("product_id", BIG, "商品"),
      C("quantity", INT, "数量"), C("unit_price", DEC, "单价"), C("amount", DEC, "金额")))

BP_LIB["inventory"] = BP("库存管理",
    T("warehouse", "仓库档案", C("warehouse_name", V64, "仓库名称"), C("warehouse_code", V64, "仓库编码"),
      C("address", V255, "地址"), C("manager_id", BIG, "负责人"), C("status", TINY, "状态")),
    T("stock", "库存台账", C("warehouse_id", BIG, "仓库"), C("product_id", BIG, "商品"),
      C("quantity", INT, "库存数"), C("safe_quantity", INT, "安全库存"), C("lock_quantity", INT, "锁定数")),
    T("stock_flow", "出入库流水", C("warehouse_id", BIG, "仓库"), C("product_id", BIG, "商品"),
      C("flow_type", TINY, "出入库类型"), C("quantity", INT, "数量"), C("related_no", V64, "关联单号"),
      C("flow_time", DT, "发生时间")))

BP_LIB["cart"] = BP("购物车",
    T("cart_item", "购物车明细", C("user_id", BIG, "用户"), C("product_id", BIG, "商品"),
      C("sku_id", BIG, "SKU"), C("quantity", INT, "数量"), C("selected", TINY, "是否选中")))

BP_LIB["payment"] = BP("支付管理",
    T("payment", "支付记录", C("pay_no", V64, "支付单号"), C("biz_no", V64, "业务单号"),
      C("pay_type", TINY, "支付方式"), C("pay_amount", DEC, "支付金额"), C("pay_status", TINY, "支付状态"),
      C("pay_time", DT, "支付时间")),
    T("payment_refund", "退款记录", C("payment_id", BIG, "支付记录"), C("refund_no", V64, "退款单号"),
      C("refund_amount", DEC, "退款金额"), C("refund_status", TINY, "退款状态"), C("refund_time", DT, "退款时间")))

BP_LIB["invoice"] = BP("发票管理",
    T("invoice", "发票档案", C("invoice_no", V64, "发票号码"), C("invoice_code", V64, "发票代码"),
      C("invoice_type", TINY, "发票类型"), C("amount", DEC, "金额"), C("tax_amount", DEC, "税额"),
      C("status", TINY, "状态"), C("invoice_time", DT, "开票时间")))

BP_LIB["contract"] = BP("合同管理",
    T("contract", "合同档案", C("contract_no", V64, "合同编号"), C("contract_name", V128, "合同名称"),
      C("partner_id", BIG, "相对方"), C("contract_amount", DEC, "合同金额"), C("sign_date", DATE, "签订日期"),
      C("expire_date", DATE, "到期日期"), C("status", TINY, "状态"), C("content", TXT, "合同内容")),
    T("contract_item", "合同条款", C("contract_id", BIG, "合同"), C("item_title", V128, "条款标题"),
      C("item_content", TXT, "条款内容"), C("sort", INT, "排序")))

BP_LIB["finance"] = BP("财务管理",
    T("finance_account", "财务科目", C("parent_id", BIG, "上级科目"), C("account_code", V64, "科目编码"),
      C("account_name", V64, "科目名称"), C("account_type", TINY, "科目类型"), C("balance", DEC, "余额")),
    T("finance_voucher", "记账凭证", C("voucher_no", V64, "凭证号"), C("voucher_date", DATE, "凭证日期"),
      C("summary", V255, "摘要"), C("total_amount", DEC, "金额"), C("status", TINY, "状态")),
    T("finance_voucher_item", "凭证明细", C("voucher_id", BIG, "凭证"), C("account_id", BIG, "科目"),
      C("debit_amount", DEC, "借方金额"), C("credit_amount", DEC, "贷方金额")))

BP_LIB["approval"] = BP("审批流程",
    T("approval_flow", "审批流程定义", C("flow_name", V64, "流程名称"), C("flow_code", V64, "流程编码"),
      C("biz_type", V64, "业务类型"), C("status", TINY, "状态")),
    T("approval_task", "审批任务", C("flow_id", BIG, "流程"), C("biz_id", BIG, "业务ID"),
      C("task_name", V64, "任务名称"), C("assignee_id", BIG, "审批人"), C("task_status", TINY, "状态"),
      C("approve_time", DT, "审批时间"), C("comment", V512, "审批意见")))

BP_LIB["notice"] = BP("公告通知",
    T("notice", "公告通知", C("title", V128, "标题"), C("notice_type", TINY, "类型"),
      C("content", TXT, "内容"), C("publish_time", DT, "发布时间"), C("status", TINY, "状态")),
    T("notice_read", "公告阅读记录", C("notice_id", BIG, "公告"), C("user_id", BIG, "用户"),
      C("read_time", DT, "阅读时间")))

BP_LIB["file"] = BP("文件附件",
    T("file_info", "文件资源", C("file_name", V128, "文件名"), C("file_path", V512, "存储路径"),
      C("file_size", BIG, "文件大小"), C("file_type", V64, "文件类型"), C("uploader_id", BIG, "上传人"),
      C("upload_time", DT, "上传时间")))

BP_LIB["schedule"] = BP("日程日历",
    T("schedule", "日程安排", C("title", V128, "日程标题"), C("user_id", BIG, "所属人"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("location", V128, "地点"),
      C("remind_flag", TINY, "是否提醒"), C("description", V512, "说明")))

BP_LIB["task"] = BP("任务协作",
    T("task", "协作任务", C("title", V128, "任务标题"), C("project_id", BIG, "所属项目"),
      C("assignee_id", BIG, "负责人"), C("priority", TINY, "优先级"), C("status", TINY, "状态"),
      C("due_date", DATE, "截止日期"), C("description", TXT, "任务描述")),
    T("task_comment", "任务评论", C("task_id", BIG, "任务"), C("user_id", BIG, "评论人"),
      C("content", TXT, "评论内容"), C("comment_time", DT, "评论时间")))

BP_LIB["project"] = BP("项目管理",
    T("project", "项目档案", C("project_name", V128, "项目名称"), C("project_code", V64, "项目编码"),
      C("manager_id", BIG, "项目负责人"), C("start_date", DATE, "开始日期"), C("end_date", DATE, "结束日期"),
      C("status", TINY, "状态"), C("description", TXT, "项目描述")),
    T("project_member", "项目成员", C("project_id", BIG, "项目"), C("user_id", BIG, "成员"),
      C("role", V64, "项目角色"), C("join_date", DATE, "加入日期")))

BP_LIB["employee"] = BP("员工管理",
    T("employee", "员工档案", C("employee_no", V64, "工号"), C("employee_name", V64, "姓名"),
      C("department_id", BIG, "部门"), C("position_id", BIG, "岗位"), C("phone", V64, "手机"),
      C("email", V128, "邮箱"), C("entry_date", DATE, "入职日期"), C("status", TINY, "状态")),
    T("employee_contract", "员工合同", C("employee_id", BIG, "员工"), C("contract_no", V64, "合同编号"),
      C("start_date", DATE, "起始日期"), C("end_date", DATE, "终止日期"), C("status", TINY, "状态")))

BP_LIB["org"] = BP("组织岗位",
    T("department", "部门档案", C("parent_id", BIG, "上级部门"), C("dept_name", V64, "部门名称"),
      C("dept_code", V64, "部门编码"), C("leader_id", BIG, "部门负责人"), C("sort", INT, "排序")),
    T("position", "岗位档案", C("dept_id", BIG, "部门"), C("position_name", V64, "岗位名称"),
      C("position_code", V64, "岗位编码"), C("level", V64, "职级")))

BP_LIB["attendance"] = BP("考勤管理",
    T("attendance", "考勤记录", C("employee_id", BIG, "员工"), C("attendance_date", DATE, "考勤日期"),
      C("check_in", DT, "上班打卡"), C("check_out", DT, "下班打卡"), C("status", TINY, "考勤状态")),
    T("leave_request", "请假申请", C("employee_id", BIG, "员工"), C("leave_type", TINY, "请假类型"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("reason", V255, "事由"),
      C("status", TINY, "审批状态")))

BP_LIB["salary"] = BP("薪资管理",
    T("salary", "薪资记录", C("employee_id", BIG, "员工"), C("salary_month", V64, "薪资月份"),
      C("base_salary", DEC, "基本工资"), C("bonus", DEC, "奖金"), C("deduct", DEC, "扣款"),
      C("actual_salary", DEC, "实发工资"), C("status", TINY, "状态")))

BP_LIB["recruit"] = BP("招聘管理",
    T("recruit_plan", "招聘计划", C("position_name", V64, "招聘岗位"), C("dept_id", BIG, "用人部门"),
      C("headcount", INT, "招聘人数"), C("status", TINY, "状态")),
    T("candidate", "候选人", C("name", V64, "姓名"), C("phone", V64, "手机"), C("email", V128, "邮箱"),
      C("position", V64, "应聘岗位"), C("status", TINY, "状态"), C("resume", TXT, "简历")))

BP_LIB["performance"] = BP("绩效考核",
    T("performance", "绩效考核", C("employee_id", BIG, "员工"), C("period", V64, "考核周期"),
      C("score", DEC, "得分"), C("grade", V64, "等级"), C("status", TINY, "状态")),
    T("performance_item", "考核指标", C("performance_id", BIG, "考核记录"), C("item_name", V128, "指标名称"),
      C("weight", DEC, "权重"), C("score", DEC, "得分")))

BP_LIB["member"] = BP("会员管理",
    T("member", "会员档案", C("member_no", V64, "会员编号"), C("member_name", V64, "会员姓名"),
      C("phone", V64, "手机号"), C("level_id", BIG, "会员等级"), C("points", INT, "积分"),
      C("balance", DEC, "余额"), C("status", TINY, "状态")),
    T("member_level", "会员等级", C("level_name", V64, "等级名称"), C("min_points", INT, "所需积分"),
      C("discount", DEC, "折扣"), C("sort", INT, "排序")))

BP_LIB["coupon"] = BP("优惠券",
    T("coupon", "优惠券定义", C("coupon_name", V64, "券名称"), C("coupon_type", TINY, "券类型"),
      C("discount_amount", DEC, "优惠金额"), C("min_amount", DEC, "使用门槛"), C("valid_days", INT, "有效天数"),
      C("status", TINY, "状态")),
    T("coupon_grant", "优惠券发放", C("coupon_id", BIG, "优惠券"), C("user_id", BIG, "领取人"),
      C("grant_time", DT, "领取时间"), C("use_time", DT, "使用时间"), C("status", TINY, "状态")))

BP_LIB["marketing"] = BP("营销活动",
    T("marketing_campaign", "营销活动", C("campaign_name", V128, "活动名称"), C("campaign_type", TINY, "活动类型"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("status", TINY, "状态"),
      C("description", TXT, "活动说明")))

BP_LIB["aftersale"] = BP("售后服务",
    T("after_sale", "售后申请", C("apply_no", V64, "申请单号"), C("order_id", BIG, "订单"),
      C("user_id", BIG, "用户"), C("apply_type", TINY, "售后类型"), C("reason", V255, "申请原因"),
      C("status", TINY, "状态")),
    T("after_sale_item", "售后明细", C("after_sale_id", BIG, "售后申请"), C("product_id", BIG, "商品"),
      C("quantity", INT, "数量"), C("amount", DEC, "金额")))

BP_LIB["workorder"] = BP("工单管理",
    T("work_order", "工单", C("order_no", V64, "工单号"), C("title", V128, "工单标题"),
      C("customer_id", BIG, "客户"), C("assignee_id", BIG, "处理人"), C("priority", TINY, "优先级"),
      C("status", TINY, "状态"), C("description", TXT, "问题描述")),
    T("work_order_log", "工单日志", C("work_order_id", BIG, "工单"), C("operator_id", BIG, "操作人"),
      C("action", V64, "操作"), C("content", V512, "内容"), C("log_time", DT, "操作时间")))

BP_LIB["complaint"] = BP("投诉建议",
    T("complaint", "投诉建议", C("title", V128, "标题"), C("user_id", BIG, "提交人"),
      C("complaint_type", TINY, "类型"), C("content", TXT, "内容"), C("status", TINY, "状态")),
    T("complaint_reply", "处理回复", C("complaint_id", BIG, "投诉"), C("reply_user_id", BIG, "回复人"),
      C("content", TXT, "回复内容"), C("reply_time", DT, "回复时间")))

BP_LIB["survey"] = BP("问卷调查",
    T("questionnaire", "问卷定义", C("title", V128, "问卷标题"), C("status", TINY, "状态"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间")),
    T("question", "题目", C("questionnaire_id", BIG, "问卷"), C("question_title", V255, "题目标题"),
      C("question_type", TINY, "题型"), C("options", TXT, "选项(JSON)"), C("sort", INT, "排序")),
    T("questionnaire_answer", "问卷答卷", C("questionnaire_id", BIG, "问卷"), C("user_id", BIG, "答卷人"),
      C("answers", TXT, "答案(JSON)"), C("submit_time", DT, "提交时间")))

BP_LIB["article"] = BP("内容文章",
    T("article_category", "文章分类", C("parent_id", BIG, "上级分类"), C("category_name", V64, "分类名称"),
      C("sort", INT, "排序")),
    T("article", "文章", C("title", V128, "标题"), C("category_id", BIG, "分类"),
      C("cover_image", V512, "封面图"), C("summary", V512, "摘要"), C("content", TXT, "正文"),
      C("view_num", INT, "阅读量"), C("publish_time", DT, "发布时间"), C("status", TINY, "状态")))

BP_LIB["media"] = BP("媒体资源",
    T("media_asset", "媒体资源", C("media_name", V128, "资源名称"), C("media_url", V512, "资源地址"),
      C("media_type", TINY, "资源类型"), C("file_size", BIG, "文件大小"), C("uploader_id", BIG, "上传人"),
      C("upload_time", DT, "上传时间")))

BP_LIB["social"] = BP("评论互动",
    T("comment", "评论", C("biz_type", V64, "业务类型"), C("biz_id", BIG, "业务ID"),
      C("parent_id", BIG, "父级评论"), C("user_id", BIG, "评论人"), C("content", TXT, "评论内容"),
      C("like_num", INT, "点赞数"), C("status", TINY, "状态")),
    T("favorite_record", "收藏记录", C("user_id", BIG, "用户"), C("biz_type", V64, "业务类型"),
      C("biz_id", BIG, "业务ID"), C("favorite_time", DT, "收藏时间")))

BP_LIB["ad"] = BP("广告管理",
    T("ad_position", "广告位", C("position_name", V64, "广告位名称"), C("position_code", V64, "广告位编码"),
      C("size", V64, "尺寸"), C("status", TINY, "状态")),
    T("ad_material", "广告素材", C("position_id", BIG, "广告位"), C("material_name", V128, "素材名称"),
      C("material_url", V512, "素材地址"), C("link_url", V512, "跳转链接"), C("status", TINY, "状态")))

BP_LIB["report"] = BP("数据统计",
    T("report", "报表定义", C("report_name", V128, "报表名称"), C("report_code", V64, "报表编码"),
      C("report_type", TINY, "报表类型"), C("status", TINY, "状态")),
    T("report_metric", "报表指标", C("report_id", BIG, "报表"), C("metric_name", V64, "指标名称"),
      C("metric_code", V64, "指标编码"), C("sort", INT, "排序")))

BP_LIB["vehicle"] = BP("车辆管理",
    T("vehicle", "车辆档案", C("plate_no", V64, "车牌号"), C("vehicle_type", V64, "车辆类型"),
      C("brand", V64, "品牌型号"), C("driver_id", BIG, "驾驶员"), C("status", TINY, "状态")),
    T("vehicle_use", "用车记录", C("vehicle_id", BIG, "车辆"), C("user_id", BIG, "使用人"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("purpose", V255, "用途")))

BP_LIB["equipment"] = BP("设备管理",
    T("equipment", "设备档案", C("equipment_no", V64, "设备编号"), C("equipment_name", V128, "设备名称"),
      C("equipment_type", TINY, "设备类型"), C("model", V64, "型号"), C("status", TINY, "状态")),
    T("equipment_maintenance", "设备维保", C("equipment_id", BIG, "设备"), C("maintain_type", TINY, "维保类型"),
      C("maintain_date", DATE, "维保日期"), C("content", V512, "维保内容"), C("operator_id", BIG, "处理人")))

BP_LIB["asset"] = BP("固定资产",
    T("asset_category", "资产分类", C("parent_id", BIG, "上级分类"), C("category_name", V64, "分类名称"),
      C("sort", INT, "排序")),
    T("asset", "固定资产", C("asset_no", V64, "资产编号"), C("asset_name", V128, "资产名称"),
      C("category_id", BIG, "资产分类"), C("purchase_date", DATE, "购置日期"), C("original_value", DEC, "原值"),
      C("department_id", BIG, "使用部门"), C("status", TINY, "状态")))

BP_LIB["office"] = BP("办公用品",
    T("office_supply", "办公用品", C("supply_name", V64, "用品名称"), C("supply_code", V64, "用品编码"),
      C("unit", V64, "单位"), C("stock_num", INT, "库存数"), C("price", DEC, "单价")))

BP_LIB["meeting"] = BP("会议管理",
    T("meeting_room", "会议室", C("room_name", V64, "会议室名称"), C("capacity", INT, "容纳人数"),
      C("location", V128, "位置"), C("status", TINY, "状态")),
    T("meeting_booking", "会议预订", C("room_id", BIG, "会议室"), C("title", V128, "会议主题"),
      C("book_user_id", BIG, "预订人"), C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"),
      C("status", TINY, "状态")))

BP_LIB["dict"] = BP("数据字典",
    T("dict_type", "字典类型", C("dict_name", V64, "字典名称"), C("dict_code", V64, "字典编码"),
      C("status", TINY, "状态")),
    T("dict_item", "字典项", C("dict_type_id", BIG, "字典类型"), C("item_label", V64, "显示名"),
      C("item_value", V64, "值"), C("sort", INT, "排序")))

BP_LIB["message"] = BP("消息通知",
    T("message", "站内消息", C("title", V128, "消息标题"), C("receiver_id", BIG, "接收人"),
      C("message_type", TINY, "消息类型"), C("content", TXT, "内容"), C("read_flag", TINY, "是否已读"),
      C("send_time", DT, "发送时间")))

BP_LIB["contact"] = BP("通讯录",
    T("contact", "联系人", C("name", V64, "姓名"), C("phone", V64, "手机"), C("email", V128, "邮箱"),
      C("company", V128, "公司"), C("group_id", BIG, "分组")),
    T("contact_group", "联系人分组", C("group_name", V64, "分组名称"), C("sort", INT, "排序")))

BP_LIB["audit"] = BP("日志审计",
    T("audit_log", "操作审计日志", C("module", V64, "模块"), C("operation", V64, "操作"),
      C("operator_id", BIG, "操作人"), C("biz_id", V64, "业务ID"), C("content", TXT, "内容"),
      C("operate_time", DT, "操作时间")))

BP_LIB["dev"] = BP("软件研发",
    T("dev_project", "研发项目", C("project_name", V128, "项目名称"), C("project_code", V64, "项目编码"),
      C("status", TINY, "状态"), C("description", TXT, "描述")),
    T("dev_issue", "研发问题", C("project_id", BIG, "项目"), C("title", V128, "问题标题"),
      C("issue_type", TINY, "问题类型"), C("assignee_id", BIG, "处理人"), C("priority", TINY, "优先级"),
      C("status", TINY, "状态")))

BP_LIB["testcase"] = BP("测试管理",
    T("test_suite", "测试套件", C("suite_name", V128, "套件名称"), C("project_id", BIG, "项目"),
      C("status", TINY, "状态")),
    T("test_case", "测试用例", C("suite_id", BIG, "套件"), C("case_title", V128, "用例标题"),
      C("case_type", TINY, "用例类型"), C("precondition", TXT, "前置条件"), C("steps", TXT, "步骤"),
      C("expected", TXT, "预期结果"), C("status", TINY, "状态")))

BP_LIB["monitor"] = BP("运维监控",
    T("monitor_target", "监控对象", C("target_name", V128, "对象名称"), C("target_type", TINY, "对象类型"),
      C("endpoint", V255, "地址"), C("status", TINY, "状态")),
    T("monitor_alert", "告警记录", C("target_id", BIG, "监控对象"), C("alert_level", TINY, "告警级别"),
      C("alert_content", V512, "告警内容"), C("alert_time", DT, "告警时间"), C("status", TINY, "状态")))

# ---------- 行业专属蓝图 ----------
def VBP(key, name, *tables):
    BP_LIB[key] = BP(name, *tables)


VBP("patient", "患者管理", T("patient", "患者档案", C("patient_name", V64, "姓名"), C("id_card", V64, "身份证号"),
    C("gender", TINY, "性别"), C("birth_date", DATE, "出生日期"), C("phone", V64, "联系电话"),
    C("address", V255, "地址"), C("blood_type", V64, "血型")))
VBP("appointment", "预约挂号", T("appointment", "预约挂号", C("patient_id", BIG, "患者"), C("dept_id", BIG, "科室"),
    C("doctor_id", BIG, "医生"), C("appointment_date", DATE, "预约日期"), C("time_slot", V64, "时段"),
    C("status", TINY, "状态")))
VBP("medicalrecord", "病历管理", T("medical_record", "就诊记录", C("patient_id", BIG, "患者"),
    C("visit_date", DT, "就诊时间"), C("dept_id", BIG, "科室"), C("doctor_id", BIG, "医生"),
    C("chief_complaint", TXT, "主诉"), C("diagnosis", TXT, "诊断"), C("status", TINY, "状态")))
VBP("prescription", "处方药品", T("drug", "药品目录", C("drug_name", V128, "药品名称"), C("drug_code", V64, "药品编码"),
    C("spec", V64, "规格"), C("unit", V64, "单位"), C("price", DEC, "价格"), C("stock_num", INT, "库存")),
    T("prescription", "处方", C("record_id", BIG, "就诊记录"), C("patient_id", BIG, "患者"),
      C("prescription_no", V64, "处方号"), C("status", TINY, "状态"), C("prescription_time", DT, "开方时间")),
    T("prescription_item", "处方明细", C("prescription_id", BIG, "处方"), C("drug_id", BIG, "药品"),
      C("quantity", INT, "数量"), C("usage", V128, "用法用量")))
VBP("labtest", "检验检查", T("lab_test", "检验项目", C("test_name", V128, "项目名称"), C("test_code", V64, "项目编码"),
    C("test_type", TINY, "项目类型"), C("price", DEC, "价格")),
    T("lab_result", "检验结果", C("patient_id", BIG, "患者"), C("test_id", BIG, "检验项目"),
      C("result_value", V255, "结果值"), C("reference_range", V128, "参考范围"), C("test_time", DT, "检验时间")))
VBP("nursing", "护理管理", T("nursing_record", "护理记录", C("patient_id", BIG, "患者"), C("nurse_id", BIG, "护士"),
    C("record_time", DT, "记录时间"), C("content", TXT, "护理内容"), C("vital_signs", V255, "生命体征")))
VBP("student", "学生管理", T("student", "学生档案", C("student_no", V64, "学号"), C("student_name", V64, "姓名"),
    C("gender", TINY, "性别"), C("class_id", BIG, "班级"), C("phone", V64, "联系电话"), C("status", TINY, "状态")),
    T("student_class", "班级档案", C("class_name", V64, "班级名称"), C("grade", V64, "年级"),
      C("head_teacher_id", BIG, "班主任")))
VBP("course", "课程管理", T("course", "课程档案", C("course_name", V128, "课程名称"), C("course_code", V64, "课程编码"),
    C("credit", DEC, "学分"), C("teacher_id", BIG, "授课教师"), C("status", TINY, "状态")),
    T("course_schedule", "课程表", C("course_id", BIG, "课程"), C("class_id", BIG, "班级"),
      C("weekday", TINY, "星期"), C("period", V64, "节次"), C("classroom", V64, "教室")))
VBP("exam", "考试管理", T("exam", "考试", C("exam_name", V128, "考试名称"), C("course_id", BIG, "课程"),
    C("exam_time", DT, "考试时间"), C("status", TINY, "状态")),
    T("exam_score", "考试成绩", C("exam_id", BIG, "考试"), C("student_id", BIG, "学生"),
      C("score", DEC, "分数"), C("grade", V64, "等级")))
VBP("tuition", "缴费管理", T("tuition_bill", "缴费账单", C("bill_no", V64, "账单号"), C("student_id", BIG, "学生"),
    C("fee_type", V64, "费用类型"), C("amount", DEC, "应收金额"), C("paid_amount", DEC, "已收金额"),
    C("status", TINY, "状态")), T("tuition_payment", "缴费记录", C("bill_id", BIG, "账单"),
    C("pay_amount", DEC, "缴费金额"), C("pay_time", DT, "缴费时间"), C("pay_type", TINY, "支付方式")))
VBP("book", "图书管理", T("book", "图书档案", C("book_name", V128, "书名"), C("isbn", V64, "ISBN"),
    C("author", V64, "作者"), C("publisher", V128, "出版社"), C("stock_num", INT, "馆藏数")),
    T("book_borrow", "借阅记录", C("book_id", BIG, "图书"), C("borrower_id", BIG, "借阅人"),
      C("borrow_date", DATE, "借出日期"), C("return_date", DATE, "归还日期"), C("status", TINY, "状态")))
VBP("dorm", "宿舍管理", T("dormitory", "宿舍档案", C("dorm_no", V64, "宿舍号"), C("building", V64, "楼栋"),
    C("capacity", INT, "容纳人数"), C("status", TINY, "状态")),
    T("dorm_checkin", "住宿登记", C("dorm_id", BIG, "宿舍"), C("student_id", BIG, "学生"),
      C("checkin_date", DATE, "入住日期"), C("status", TINY, "状态")))
VBP("research", "科研课题", T("research_project", "科研课题", C("project_name", V128, "课题名称"),
    C("project_code", V64, "课题编号"), C("leader_id", BIG, "负责人"), C("fund_amount", DEC, "经费"),
    C("start_date", DATE, "开始日期"), C("end_date", DATE, "结束日期"), C("status", TINY, "状态")),
    T("research_member", "课题成员", C("project_id", BIG, "课题"), C("member_id", BIG, "成员"), C("role", V64, "角色")))
VBP("paper", "论文成果", T("paper", "论文", C("paper_title", V255, "论文标题"), C("author_id", BIG, "作者"),
    C("journal", V128, "期刊"), C("publish_date", DATE, "发表日期"), C("status", TINY, "状态")),
    T("patent", "专利", C("patent_name", V128, "专利名称"), C("patent_no", V64, "专利号"),
      C("applicant_id", BIG, "申请人"), C("apply_date", DATE, "申请日期"), C("status", TINY, "状态")))
VBP("lab", "实验室管理", T("lab", "实验室", C("lab_name", V64, "实验室名称"), C("lab_code", V64, "实验室编码"),
    C("location", V128, "位置"), C("manager_id", BIG, "负责人")),
    T("lab_appointment", "实验室预约", C("lab_id", BIG, "实验室"), C("user_id", BIG, "预约人"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("status", TINY, "状态")))
VBP("legalcase", "案件管理", T("legal_case", "案件档案", C("case_no", V64, "案号"), C("case_name", V128, "案件名称"),
    C("case_type", TINY, "案件类型"), C("client_id", BIG, "委托人"), C("lawyer_id", BIG, "承办律师"),
    C("status", TINY, "状态"), C("description", TXT, "案情")),
    T("case_participant", "案件参与人", C("case_id", BIG, "案件"), C("name", V64, "姓名"),
      C("role", V64, "诉讼地位"), C("phone", V64, "联系电话")))
VBP("tax", "税务管理", T("tax_declaration", "纳税申报", C("declaration_no", V64, "申报号"), C("tax_type", V64, "税种"),
    C("tax_period", V64, "税款所属期"), C("tax_amount", DEC, "税额"), C("status", TINY, "状态")),
    T("tax_item", "申报明细", C("declaration_id", BIG, "申报"), C("item_name", V64, "项目"),
      C("amount", DEC, "金额"), C("tax_rate", DEC, "税率")))
VBP("expense", "报销管理", T("expense_claim", "费用报销", C("claim_no", V64, "报销单号"), C("applicant_id", BIG, "申请人"),
    C("claim_type", TINY, "报销类型"), C("total_amount", DEC, "报销金额"), C("status", TINY, "状态")),
    T("expense_item", "报销明细", C("claim_id", BIG, "报销单"), C("expense_date", DATE, "发生日期"),
      C("expense_type", V64, "费用类型"), C("amount", DEC, "金额"), C("remark", V255, "说明")))
VBP("budget", "预算管理", T("budget", "预算", C("budget_name", V128, "预算名称"), C("budget_year", V64, "预算年度"),
    C("dept_id", BIG, "部门"), C("total_amount", DEC, "预算金额"), C("status", TINY, "状态")),
    T("budget_item", "预算明细", C("budget_id", BIG, "预算"), C("item_name", V64, "项目"),
      C("amount", DEC, "金额"), C("used_amount", DEC, "已使用")))
VBP("investment", "投资理财", T("investment", "投资项目", C("invest_name", V128, "项目名称"), C("invest_type", TINY, "投资类型"),
    C("invest_amount", DEC, "投资金额"), C("start_date", DATE, "起始日期"), C("status", TINY, "状态")),
    T("investment_record", "投资流水", C("investment_id", BIG, "投资项目"), C("record_type", TINY, "类型"),
      C("amount", DEC, "金额"), C("record_time", DT, "时间")))
VBP("insurance", "保险保单", T("insurance_policy", "保单", C("policy_no", V64, "保单号"), C("customer_id", BIG, "客户"),
    C("product_name", V128, "险种"), C("premium", DEC, "保费"), C("start_date", DATE, "起保日期"),
    C("end_date", DATE, "终保日期"), C("status", TINY, "状态")),
    T("insurance_claim", "理赔案件", C("policy_id", BIG, "保单"), C("claim_no", V64, "报案号"),
      C("claim_amount", DEC, "理赔金额"), C("status", TINY, "状态")))
VBP("loan", "信贷管理", T("loan", "贷款档案", C("loan_no", V64, "贷款账号"), C("customer_id", BIG, "客户"),
    C("loan_amount", DEC, "贷款金额"), C("term_months", INT, "期限(月)"), C("interest_rate", DEC, "利率"),
    C("status", TINY, "状态")), T("loan_repayment", "还款记录", C("loan_id", BIG, "贷款"),
    C("repay_amount", DEC, "还款金额"), C("repay_date", DATE, "还款日期"), C("status", TINY, "状态")))
VBP("security", "证券交易", T("trade_account", "交易账户", C("account_no", V64, "资金账号"), C("customer_id", BIG, "客户"),
    C("balance", DEC, "资金余额"), C("status", TINY, "状态")),
    T("trade_order", "交易委托", C("account_id", BIG, "账户"), C("stock_code", V64, "证券代码"),
      C("trade_type", TINY, "买卖方向"), C("quantity", INT, "数量"), C("price", DEC, "价格"),
      C("status", TINY, "状态"), C("order_time", DT, "委托时间")))
VBP("production", "生产计划", T("production_plan", "生产计划", C("plan_no", V64, "计划号"), C("product_id", BIG, "产品"),
    C("quantity", INT, "计划数量"), C("plan_date", DATE, "计划日期"), C("status", TINY, "状态")),
    T("production_order", "生产工单", C("plan_id", BIG, "生产计划"), C("order_no", V64, "工单号"),
      C("quantity", INT, "数量"), C("workshop_id", BIG, "车间"), C("status", TINY, "状态")))
VBP("process", "工艺管理", T("process_route", "工艺路线", C("product_id", BIG, "产品"), C("route_name", V64, "路线名称"),
    C("version", V64, "版本"), C("status", TINY, "状态")),
    T("process_step", "工序", C("route_id", BIG, "工艺路线"), C("step_name", V64, "工序名称"),
      C("step_no", INT, "工序号"), C("standard_time", INT, "标准工时")))
VBP("quality", "质量管理", T("quality_check", "质量检验", C("check_no", V64, "检验单号"), C("biz_type", V64, "业务类型"),
    C("biz_id", BIG, "业务ID"), C("check_result", TINY, "检验结果"), C("checker_id", BIG, "检验员"),
    C("check_time", DT, "检验时间")), T("quality_issue", "质量问题", C("check_id", BIG, "检验单"),
    C("issue_desc", V255, "问题描述"), C("severity", TINY, "严重程度"), C("status", TINY, "状态")))
VBP("bom", "BOM管理", T("bom", "物料清单", C("product_id", BIG, "成品"), C("bom_no", V64, "BOM编号"),
    C("version", V64, "版本"), C("status", TINY, "状态")),
    T("bom_item", "BOM明细", C("bom_id", BIG, "BOM"), C("material_id", BIG, "物料"),
      C("quantity", DEC, "用量"), C("loss_rate", DEC, "损耗率")))
VBP("dish", "菜品管理", T("dish_category", "菜品分类", C("category_name", V64, "分类名称"), C("sort", INT, "排序")),
    T("dish", "菜品档案", C("dish_name", V64, "菜品名称"), C("category_id", BIG, "菜品分类"),
      C("price", DEC, "价格"), C("image", V512, "图片"), C("description", V255, "描述"), C("status", TINY, "状态")))
VBP("dining", "点餐管理", T("dining_table", "餐桌", C("table_no", V64, "桌号"), C("capacity", INT, "容纳人数"),
    C("area", V64, "区域"), C("status", TINY, "状态")),
    T("dining_order", "点餐单", C("order_no", V64, "单号"), C("table_id", BIG, "餐桌"),
      C("total_amount", DEC, "金额"), C("status", TINY, "状态"), C("order_time", DT, "下单时间")),
    T("dining_order_item", "点餐明细", C("order_id", BIG, "点餐单"), C("dish_id", BIG, "菜品"),
      C("quantity", INT, "数量"), C("amount", DEC, "金额")))
VBP("hotel", "客房管理", T("room_type", "房型", C("type_name", V64, "房型名称"), C("price", DEC, "价格"),
    C("bed_type", V64, "床型"), C("image", V512, "图片")),
    T("room", "客房档案", C("room_no", V64, "房号"), C("type_id", BIG, "房型"),
      C("floor", V64, "楼层"), C("status", TINY, "房态")),
    T("hotel_booking", "客房预订", C("booking_no", V64, "预订号"), C("room_id", BIG, "客房"),
      C("guest_name", V64, "客人姓名"), C("check_in", DATE, "入住日期"), C("check_out", DATE, "离店日期"),
      C("status", TINY, "状态")))
VBP("scenic", "景区票务", T("scenic_spot", "景点档案", C("spot_name", V128, "景点名称"), C("spot_code", V64, "景点编码"),
    C("ticket_price", DEC, "票价"), C("status", TINY, "状态")),
    T("ticket_order", "门票订单", C("order_no", V64, "订单号"), C("spot_id", BIG, "景点"),
      C("quantity", INT, "数量"), C("amount", DEC, "金额"), C("visit_date", DATE, "游玩日期"),
      C("status", TINY, "状态")))
VBP("travel", "旅游线路", T("travel_route", "旅游线路", C("route_name", V128, "线路名称"), C("route_code", V64, "线路编码"),
    C("days", INT, "行程天数"), C("price", DEC, "价格"), C("status", TINY, "状态")),
    T("travel_order", "旅游订单", C("order_no", V64, "订单号"), C("route_id", BIG, "线路"),
      C("customer_id", BIG, "客户"), C("quantity", INT, "人数"), C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("waybill", "物流运单", T("waybill", "运单", C("waybill_no", V64, "运单号"), C("sender_id", BIG, "寄件人"),
    C("receiver_id", BIG, "收件人"), C("goods_name", V128, "货物名称"), C("weight", DEC, "重量"),
    C("status", TINY, "状态")), T("waybill_track", "物流轨迹", C("waybill_id", BIG, "运单"),
    C("track_time", DT, "轨迹时间"), C("location", V128, "位置"), C("description", V255, "描述")))
VBP("delivery", "配送管理", T("delivery", "配送单", C("delivery_no", V64, "配送单号"), C("order_id", BIG, "订单"),
    C("driver_id", BIG, "配送员"), C("delivery_time", DT, "配送时间"), C("status", TINY, "状态")),
    T("delivery_item", "配送明细", C("delivery_id", BIG, "配送单"), C("product_id", BIG, "商品"),
      C("quantity", INT, "数量")))
VBP("coldchain", "冷链温控", T("cold_chain", "冷链设备", C("device_no", V64, "设备编号"), C("device_name", V128, "设备名称"),
    C("location", V128, "位置"), C("status", TINY, "状态")),
    T("temperature_record", "温度记录", C("device_id", BIG, "设备"), C("temperature", DEC, "温度"),
      C("humidity", DEC, "湿度"), C("record_time", DT, "采集时间")))
VBP("land", "地块管理", T("land_parcel", "地块档案", C("parcel_no", V64, "地块编号"), C("parcel_name", V64, "地块名称"),
    C("area", DEC, "面积(亩)"), C("owner_id", BIG, "承包人"), C("status", TINY, "状态")),
    T("crop_plan", "种植计划", C("parcel_id", BIG, "地块"), C("crop_name", V64, "作物名称"),
      C("plant_date", DATE, "种植日期"), C("status", TINY, "状态")))
VBP("agri", "农资管理", T("agricultural_material", "农资档案", C("material_name", V128, "农资名称"),
    C("material_type", TINY, "农资类型"), C("spec", V64, "规格"), C("stock_num", INT, "库存")),
    T("material_stock", "农资出入库", C("material_id", BIG, "农资"), C("flow_type", TINY, "出入库类型"),
      C("quantity", DEC, "数量"), C("flow_time", DT, "时间")))
VBP("livestock", "养殖管理", T("livestock", "畜禽档案", C("batch_no", V64, "批次号"), C("species", V64, "品种"),
    C("quantity", INT, "数量"), C("birth_date", DATE, "出生日期"), C("status", TINY, "状态")),
    T("vaccine_record", "免疫记录", C("livestock_id", BIG, "批次"), C("vaccine_name", V64, "疫苗名称"),
      C("vaccine_date", DATE, "免疫日期"), C("operator_id", BIG, "操作人")))
VBP("trace", "农产品溯源", T("trace_batch", "溯源批次", C("batch_no", V64, "批次号"), C("product_id", BIG, "产品"),
    C("produce_date", DATE, "生产日期"), C("origin", V128, "产地")),
    T("trace_record", "溯源环节", C("batch_id", BIG, "批次"), C("link_type", V64, "环节类型"),
      C("link_content", V512, "环节内容"), C("link_time", DT, "环节时间")))
VBP("envmon", "环境监测", T("env_monitor_point", "监测点位", C("point_name", V64, "点位名称"), C("point_code", V64, "点位编码"),
    C("location", V128, "位置"), C("status", TINY, "状态")),
    T("env_monitor_record", "监测数据", C("point_id", BIG, "监测点位"), C("pm25", DEC, "PM2.5"),
      C("pm10", DEC, "PM10"), C("noise", DEC, "噪声"), C("record_time", DT, "采集时间")))
VBP("energy", "能源管理", T("energy_meter", "能源表计", C("meter_no", V64, "表计编号"), C("meter_type", TINY, "能源类型"),
    C("location", V128, "位置"), C("status", TINY, "状态")),
    T("energy_reading", "能耗读数", C("meter_id", BIG, "表计"), C("reading", DEC, "读数"),
      C("reading_time", DT, "采集时间")))
VBP("waste", "垃圾分类", T("waste_station", "垃圾站点", C("station_name", V64, "站点名称"), C("location", V128, "位置"),
    C("status", TINY, "状态")), T("waste_record", "收运记录", C("station_id", BIG, "站点"),
    C("waste_type", TINY, "垃圾类型"), C("weight", DEC, "重量"), C("record_time", DT, "时间")))
VBP("engineering", "工程项目", T("engineering_project", "工程项目", C("project_name", V128, "项目名称"),
    C("project_code", V64, "项目编码"), C("manager_id", BIG, "项目负责人"), C("budget", DEC, "预算"),
    C("status", TINY, "状态")), T("engineering_task", "工程任务", C("project_id", BIG, "项目"),
    C("task_name", V128, "任务名称"), C("status", TINY, "状态"), C("due_date", DATE, "截止日期")))
VBP("construction", "施工管理", T("construction_site", "施工现场", C("site_name", V128, "工地名称"),
    C("project_id", BIG, "所属项目"), C("location", V128, "位置"), C("status", TINY, "状态")),
    T("construction_log", "施工日志", C("site_id", BIG, "工地"), C("log_date", DATE, "日志日期"),
      C("content", TXT, "施工内容"), C("recorder_id", BIG, "记录人")))
VBP("safety", "安全管理", T("safety_check", "安全检查", C("check_no", V64, "检查单号"), C("location", V128, "检查位置"),
    C("checker_id", BIG, "检查人"), C("result", TINY, "结果"), C("check_date", DATE, "检查日期")),
    T("safety_issue", "安全隐患", C("check_id", BIG, "检查单"), C("issue_desc", V255, "隐患描述"),
      C("severity", TINY, "严重程度"), C("status", TINY, "整改状态")))
VBP("bid", "招投标管理", T("bid_project", "招标项目", C("project_name", V128, "项目名称"), C("project_no", V64, "项目编号"),
    C("budget", DEC, "预算"), C("status", TINY, "状态")),
    T("bidder", "投标单位", C("project_id", BIG, "招标项目"), C("bidder_name", V128, "单位名称"),
      C("contact_phone", V64, "联系电话"), C("status", TINY, "状态")),
    T("bid_record", "投标记录", C("project_id", BIG, "招标项目"), C("bidder_id", BIG, "投标单位"),
      C("bid_amount", DEC, "投标报价"), C("status", TINY, "状态")))
VBP("grid", "网格管理", T("grid", "网格划分", C("grid_name", V64, "网格名称"), C("parent_id", BIG, "上级网格"),
    C("manager_id", BIG, "网格员"), C("status", TINY, "状态")),
    T("grid_member", "网格居民", C("grid_id", BIG, "网格"), C("name", V64, "姓名"), C("phone", V64, "电话"),
      C("address", V255, "地址")))
VBP("visitor", "门禁访客", T("visitor", "访客登记", C("visitor_name", V64, "访客姓名"), C("phone", V64, "联系电话"),
      C("visit_company", V128, "来访单位"), C("interviewee_id", BIG, "被访人"), C("visit_time", DT, "来访时间"),
      C("status", TINY, "状态")), T("access_record", "通行记录", C("person_id", BIG, "人员"),
    C("gate", V64, "门禁点"), C("access_time", DT, "通行时间"), C("access_type", TINY, "通行类型")))
VBP("patrol", "安保巡逻", T("patrol", "巡逻任务", C("patrol_name", V64, "任务名称"), C("route", V255, "巡逻路线"),
    C("assignee_id", BIG, "巡逻人"), C("start_time", DT, "开始时间"), C("status", TINY, "状态")),
    T("patrol_record", "巡逻记录", C("patrol_id", BIG, "巡逻任务"), C("location", V128, "位置"),
      C("record_time", DT, "时间"), C("description", V255, "情况")))
VBP("gym", "健身会员", T("gym_member", "健身会员", C("member_no", V64, "会员编号"), C("member_name", V64, "姓名"),
    C("phone", V64, "手机"), C("member_type", TINY, "会员类型"), C("expire_date", DATE, "到期日期"), C("status", TINY, "状态")),
    T("gym_course", "健身课程", C("course_name", V64, "课程名称"), C("coach_id", BIG, "教练"),
      C("start_time", DT, "开始时间"), C("capacity", INT, "名额")))
VBP("event", "赛事管理", T("event", "赛事活动", C("event_name", V128, "赛事名称"), C("event_type", TINY, "赛事类型"),
    C("start_time", DT, "开始时间"), C("location", V128, "地点"), C("status", TINY, "状态")),
    T("event_registration", "赛事报名", C("event_id", BIG, "赛事"), C("name", V64, "姓名"),
      C("phone", V64, "电话"), C("register_time", DT, "报名时间"), C("status", TINY, "状态")))
VBP("beauty", "美容服务", T("beauty_service", "美容项目", C("service_name", V64, "项目名称"), C("service_type", TINY, "项目类型"),
    C("price", DEC, "价格"), C("duration", INT, "时长(分钟)"), C("status", TINY, "状态")),
    T("beautician", "技师档案", C("name", V64, "姓名"), C("phone", V64, "电话"),
      C("skill", V128, "擅长项目"), C("status", TINY, "状态")))
VBP("shopappointment", "预约到店", T("shop_appointment", "到店预约", C("appointment_no", V64, "预约号"),
    C("customer_id", BIG, "客户"), C("service_id", BIG, "服务项目"), C("staff_id", BIG, "服务人员"),
    C("appointment_time", DT, "预约时间"), C("status", TINY, "状态")))
VBP("pet", "宠物管理", T("pet", "宠物档案", C("pet_name", V64, "宠物名"), C("owner_id", BIG, "主人"),
    C("species", V64, "种类"), C("breed", V64, "品种"), C("birth_date", DATE, "出生日期")),
    T("pet_service", "宠物服务", C("pet_id", BIG, "宠物"), C("service_type", V64, "服务类型"),
      C("service_time", DT, "服务时间"), C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("housekeep", "家政服务", T("housekeeper", "家政人员", C("name", V64, "姓名"), C("phone", V64, "电话"),
    C("skill", V128, "技能"), C("status", TINY, "状态")),
    T("housekeep_order", "家政订单", C("order_no", V64, "订单号"), C("customer_id", BIG, "客户"),
      C("housekeeper_id", BIG, "家政人员"), C("service_type", V64, "服务类型"), C("amount", DEC, "金额"),
      C("status", TINY, "状态")))
VBP("repair", "维修服务", T("repairman", "维修师傅", C("name", V64, "姓名"), C("phone", V64, "电话"),
    C("skill", V128, "擅长"), C("status", TINY, "状态")),
    T("repair_order", "维修工单", C("order_no", V64, "工单号"), C("customer_id", BIG, "客户"),
      C("repairman_id", BIG, "维修师傅"), C("fault_desc", TXT, "故障描述"), C("amount", DEC, "费用"),
      C("status", TINY, "状态")))
VBP("photo", "摄影服务", T("photo_order", "摄影订单", C("order_no", V64, "订单号"), C("customer_id", BIG, "客户"),
    C("shoot_type", V64, "拍摄类型"), C("shoot_date", DATE, "拍摄日期"), C("amount", DEC, "金额"),
    C("status", TINY, "状态")), T("photo_asset", "摄影作品", C("order_id", BIG, "订单"),
    C("image_url", V512, "图片地址"), C("sort", INT, "排序")))
VBP("translation", "翻译服务", T("translator", "译员档案", C("name", V64, "姓名"), C("phone", V64, "电话"),
    C("language_pair", V64, "语言对"), C("status", TINY, "状态")),
    T("translation_order", "翻译订单", C("order_no", V64, "订单号"), C("customer_id", BIG, "客户"),
      C("translator_id", BIG, "译员"), C("source_lang", V64, "源语言"), C("target_lang", V64, "目标语言"),
      C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("print", "印刷制作", T("print_order", "印刷订单", C("order_no", V64, "订单号"), C("customer_id", BIG, "客户"),
    C("product_name", V128, "印刷品名称"), C("quantity", INT, "数量"), C("amount", DEC, "金额"),
    C("status", TINY, "状态")), T("print_item", "印刷明细", C("order_id", BIG, "订单"),
    C("item_name", V64, "明细项"), C("spec", V128, "规格"), C("quantity", INT, "数量")))
VBP("exhibition", "会展管理", T("exhibition", "展会", C("exhibition_name", V128, "展会名称"),
    C("start_date", DATE, "开始日期"), C("end_date", DATE, "结束日期"), C("location", V128, "地点"),
    C("status", TINY, "状态")), T("exhibitor", "参展商", C("exhibition_id", BIG, "展会"),
    C("company_name", V128, "公司名称"), C("booth_no", V64, "展位号"), C("status", TINY, "状态")))
VBP("publication", "出版物管理", T("publication", "出版物", C("title", V128, "书名"), C("isbn", V64, "ISBN"),
    C("author", V64, "作者"), C("publisher", V128, "出版社"), C("price", DEC, "定价"), C("status", TINY, "状态")),
    T("publication_stock", "出版物库存", C("publication_id", BIG, "出版物"), C("warehouse_id", BIG, "仓库"),
      C("quantity", INT, "库存数")))
VBP("customs", "报关管理", T("customs_declaration", "报关单", C("declaration_no", V64, "报关单号"),
    C("order_no", V64, "订单号"), C("goods_name", V128, "货物名称"), C("amount", DEC, "申报金额"),
    C("status", TINY, "状态")), T("trade_document", "外贸单证", C("declaration_id", BIG, "报关单"),
    C("doc_type", V64, "单证类型"), C("doc_no", V64, "单证编号"), C("file_url", V512, "文件地址")))
VBP("port", "港口管理", T("port", "港口档案", C("port_name", V64, "港口名称"), C("port_code", V64, "港口编码"),
    C("location", V128, "位置"), C("status", TINY, "状态")),
    T("berth", "泊位", C("port_id", BIG, "港口"), C("berth_no", V64, "泊位号"), C("capacity", DEC, "吞吐能力"),
      C("status", TINY, "状态")))
VBP("vessel", "航运管理", T("vessel", "船舶档案", C("vessel_name", V64, "船名"), C("imo_no", V64, "IMO号"),
    C("vessel_type", V64, "船型"), C("status", TINY, "状态")),
    T("voyage", "航次", C("vessel_id", BIG, "船舶"), C("voyage_no", V64, "航次号"),
      C("departure", V128, "出发港"), C("destination", V128, "目的港"), C("sail_date", DATE, "开航日期")))
VBP("flight", "航班管理", T("flight", "航班", C("flight_no", V64, "航班号"), C("airline", V64, "航空公司"),
    C("departure", V128, "出发地"), C("destination", V128, "目的地"), C("depart_time", DT, "起飞时间"),
    C("status", TINY, "状态")), T("flight_booking", "机票订单", C("flight_id", BIG, "航班"),
    C("passenger_name", V64, "乘机人"), C("id_card", V64, "证件号"), C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("metro", "地铁运营", T("metro_line", "地铁线路", C("line_name", V64, "线路名称"), C("line_code", V64, "线路编码"),
    C("status", TINY, "状态")), T("metro_station", "地铁站", C("line_id", BIG, "线路"),
    C("station_name", V64, "站名"), C("station_no", INT, "站序"), C("status", TINY, "状态")))
VBP("bus", "公交管理", T("bus_line", "公交线路", C("line_name", V64, "线路名称"), C("line_no", V64, "线路编号"),
    C("start_station", V64, "起点站"), C("end_station", V64, "终点站"), C("status", TINY, "状态")),
    T("bus", "公交车辆", C("plate_no", V64, "车牌号"), C("line_id", BIG, "所属线路"), C("status", TINY, "状态")))
VBP("parking", "停车管理", T("parking_lot", "停车场", C("lot_name", V64, "停车场名称"), C("location", V128, "位置"),
    C("total_spaces", INT, "总车位"), C("status", TINY, "状态")),
    T("parking_record", "停车记录", C("lot_id", BIG, "停车场"), C("plate_no", V64, "车牌号"),
      C("entry_time", DT, "入场时间"), C("exit_time", DT, "出场时间"), C("fee", DEC, "费用")))
VBP("charging", "充电站", T("charging_station", "充电站", C("station_name", V64, "站点名称"), C("location", V128, "位置"),
    C("pile_count", INT, "充电桩数"), C("status", TINY, "状态")),
    T("charging_order", "充电订单", C("station_id", BIG, "充电站"), C("plate_no", V64, "车牌号"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("amount", DEC, "金额")))
VBP("gasstation", "加油站", T("gas_station", "加油站", C("station_name", V64, "站点名称"), C("location", V128, "位置"),
    C("status", TINY, "状态")), T("fuel_order", "加油订单", C("station_id", BIG, "加油站"),
    C("plate_no", V64, "车牌号"), C("fuel_type", V64, "油品"), C("quantity", DEC, "加油量"),
    C("amount", DEC, "金额"), C("order_time", DT, "时间")))
VBP("toll", "高速公路", T("toll_station", "收费站", C("station_name", V64, "站点名称"), C("location", V128, "位置"),
    C("status", TINY, "状态")), T("toll_record", "通行记录", C("station_id", BIG, "收费站"),
    C("plate_no", V64, "车牌号"), C("entry_time", DT, "入口时间"), C("exit_time", DT, "出口时间"),
    C("fee", DEC, "通行费")))
VBP("bike", "共享单车", T("bike", "单车档案", C("bike_no", V64, "车辆编号"), C("bike_type", TINY, "车辆类型"),
    C("location", V128, "当前位置"), C("status", TINY, "状态")),
    T("bike_rental", "骑行记录", C("bike_id", BIG, "单车"), C("user_id", BIG, "用户"),
      C("start_time", DT, "开始时间"), C("end_time", DT, "结束时间"), C("fee", DEC, "费用")))
VBP("ride", "网约车", T("ride_driver", "司机档案", C("name", V64, "姓名"), C("phone", V64, "电话"),
    C("plate_no", V64, "车牌号"), C("status", TINY, "状态")),
    T("ride_order", "行程订单", C("order_no", V64, "订单号"), C("passenger_id", BIG, "乘客"),
      C("driver_id", BIG, "司机"), C("start_location", V255, "起点"), C("end_location", V255, "终点"),
      C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("autorepair", "汽车维修", T("auto_repair", "维修工单", C("order_no", V64, "工单号"), C("plate_no", V64, "车牌号"),
    C("customer_id", BIG, "客户"), C("fault_desc", TXT, "故障描述"), C("amount", DEC, "金额"), C("status", TINY, "状态")),
    T("auto_repair_item", "维修项目", C("repair_id", BIG, "工单"), C("item_name", V64, "项目名称"),
      C("quantity", INT, "数量"), C("amount", DEC, "金额")))
VBP("carrental", "汽车租赁", T("car", "租赁车辆", C("plate_no", V64, "车牌号"), C("car_type", V64, "车型"),
      C("daily_price", DEC, "日租金"), C("status", TINY, "状态")),
    T("car_rental", "租车订单", C("order_no", V64, "订单号"), C("car_id", BIG, "车辆"),
      C("customer_id", BIG, "客户"), C("start_date", DATE, "起租日期"), C("end_date", DATE, "还车日期"),
      C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("violation", "交通违法", T("violation", "违法记录", C("plate_no", V64, "车牌号"), C("violation_type", V64, "违法类型"),
    C("location", V128, "违法地点"), C("violation_time", DT, "违法时间"), C("fine_amount", DEC, "罚款金额"),
    C("status", TINY, "状态")))
VBP("elderly", "养老护理", T("elderly", "老人档案", C("name", V64, "姓名"), C("gender", TINY, "性别"),
    C("id_card", V64, "身份证号"), C("health_status", V64, "健康状况"), C("emergency_contact", V64, "紧急联系人")),
    T("care_record", "护理记录", C("elderly_id", BIG, "老人"), C("nurse_id", BIG, "护理员"),
      C("care_time", DT, "护理时间"), C("content", TXT, "护理内容")))
VBP("socialsec", "社保管理", T("social_security", "社保参保", C("person_id", BIG, "人员"), C("insurance_type", V64, "险种"),
    C("base_amount", DEC, "缴费基数"), C("start_date", DATE, "起始日期"), C("status", TINY, "状态")),
    T("ss_payment", "社保缴费", C("ss_id", BIG, "参保记录"), C("pay_month", V64, "缴费月份"),
      C("amount", DEC, "缴费金额"), C("status", TINY, "状态")))
VBP("housingfund", "公积金", T("housing_fund", "公积金账户", C("person_id", BIG, "人员"), C("account_no", V64, "账号"),
    C("balance", DEC, "余额"), C("status", TINY, "状态")),
    T("hf_payment", "公积金缴存", C("fund_id", BIG, "账户"), C("pay_month", V64, "缴存月份"),
      C("amount", DEC, "缴存金额"), C("status", TINY, "状态")))
VBP("employment", "就业服务", T("job_seeker", "求职人员", C("name", V64, "姓名"), C("phone", V64, "电话"),
    C("skill", V128, "技能特长"), C("status", TINY, "状态")),
    T("job_match", "岗位匹配", C("seeker_id", BIG, "求职人员"), C("job_id", BIG, "岗位"),
      C("match_score", DEC, "匹配度"), C("status", TINY, "状态")))
VBP("legalaid", "法律援助", T("legal_aid", "援助申请", C("apply_no", V64, "申请编号"), C("applicant_id", BIG, "申请人"),
    C("case_type", V64, "案件类型"), C("status", TINY, "状态"), C("description", TXT, "情况说明")))
VBP("notary", "公证服务", T("notarization", "公证事项", C("case_no", V64, "公证编号"), C("applicant_id", BIG, "申请人"),
    C("notary_type", V64, "公证类型"), C("status", TINY, "状态")))
VBP("arbitration", "仲裁管理", T("arbitration", "仲裁案件", C("case_no", V64, "案号"), C("case_name", V128, "案件名称"),
    C("applicant_id", BIG, "申请人"), C("respondent_id", BIG, "被申请人"), C("status", TINY, "状态")))
VBP("ip", "知识产权", T("ip_asset", "知识产权", C("asset_name", V128, "名称"), C("asset_type", TINY, "类型"),
    C("reg_no", V64, "登记号"), C("owner_id", BIG, "权利人"), C("status", TINY, "状态")),
    T("ip_application", "申请记录", C("asset_id", BIG, "知识产权"), C("apply_no", V64, "申请号"),
      C("apply_date", DATE, "申请日期"), C("status", TINY, "状态")))
VBP("certification", "认证管理", T("certification", "认证项目", C("cert_name", V128, "认证名称"), C("cert_type", V64, "认证类型"),
    C("company_id", BIG, "企业"), C("status", TINY, "状态")),
    T("cert_audit", "认证审核", C("cert_id", BIG, "认证项目"), C("audit_date", DATE, "审核日期"),
      C("result", TINY, "审核结果"), C("auditor_id", BIG, "审核员")))
VBP("inspection", "检验检测", T("inspection", "检测委托", C("delegate_no", V64, "委托号"), C("client_id", BIG, "客户"),
    C("sample_name", V128, "样品名称"), C("status", TINY, "状态")),
    T("inspection_item", "检测项目", C("inspection_id", BIG, "检测委托"), C("item_name", V128, "项目"),
      C("result", V255, "结果"), C("standard", V128, "标准")))
VBP("auction", "拍卖管理", T("auction", "拍卖会", C("auction_name", V128, "拍卖会名称"), C("start_time", DT, "开始时间"),
    C("location", V128, "地点"), C("status", TINY, "状态")),
    T("auction_item", "拍卖标的", C("auction_id", BIG, "拍卖会"), C("item_name", V128, "标的名称"),
      C("start_price", DEC, "起拍价"), C("status", TINY, "状态")))
VBP("pawn", "典当管理", T("pawn", "典当业务", C("pawn_no", V64, "当票号"), C("customer_id", BIG, "客户"),
    C("item_name", V128, "当物名称"), C("loan_amount", DEC, "当金"), C("status", TINY, "状态")),
    T("pawn_item", "当物明细", C("pawn_id", BIG, "典当业务"), C("item_desc", V255, "物品描述"),
      C("estimated_value", DEC, "评估价")))
VBP("recycle", "再生资源", T("recycle_order", "回收订单", C("order_no", V64, "订单号"), C("customer_id", BIG, "客户"),
    C("material_type", V64, "回收品类"), C("weight", DEC, "重量"), C("amount", DEC, "金额"), C("status", TINY, "状态")),
    T("recycle_item", "回收明细", C("order_id", BIG, "回收订单"), C("item_name", V64, "品名"),
      C("quantity", INT, "数量"), C("amount", DEC, "金额")))
VBP("bonded", "保税仓", T("bonded_warehouse", "保税仓库", C("warehouse_name", V64, "仓库名称"), C("warehouse_code", V64, "仓库编码"),
    C("location", V128, "位置"), C("status", TINY, "状态")),
    T("bonded_stock", "保税库存", C("warehouse_id", BIG, "仓库"), C("goods_name", V128, "货物名称"),
      C("quantity", INT, "数量"), C("amount", DEC, "金额")))
VBP("driving", "驾校管理", T("driving_school", "驾校档案", C("school_name", V64, "驾校名称"), C("school_code", V64, "编码"),
    C("address", V255, "地址"), C("status", TINY, "状态")),
    T("driving_appointment", "练车预约", C("student_id", BIG, "学员"), C("coach_id", BIG, "教练"),
      C("appointment_date", DATE, "预约日期"), C("status", TINY, "状态")))
VBP("enrollment", "招生管理", T("enrollment", "招生报名", C("apply_no", V64, "报名号"), C("student_name", V64, "姓名"),
    C("phone", V64, "电话"), C("intended_major", V64, "意向专业"), C("status", TINY, "状态")),
    T("enrollment_source", "生源渠道", C("source_name", V64, "渠道名称"), C("count", INT, "报名数")))
VBP("textbook", "教材管理", T("textbook", "教材档案", C("book_name", V128, "教材名称"), C("isbn", V64, "ISBN"),
    C("publisher", V128, "出版社"), C("price", DEC, "定价"), C("stock_num", INT, "库存")),
    T("textbook_stock", "教材出入库", C("textbook_id", BIG, "教材"), C("flow_type", TINY, "出入库类型"),
      C("quantity", INT, "数量"), C("flow_time", DT, "时间")))
VBP("campuscard", "校园卡", T("campus_card", "校园卡", C("card_no", V64, "卡号"), C("holder_id", BIG, "持卡人"),
    C("balance", DEC, "余额"), C("status", TINY, "状态")),
    T("campus_transaction", "校园消费", C("card_id", BIG, "校园卡"), C("amount", DEC, "金额"),
      C("trade_type", TINY, "交易类型"), C("trade_time", DT, "交易时间")))
VBP("canteen", "食堂管理", T("canteen", "食堂档案", C("canteen_name", V64, "食堂名称"), C("location", V128, "位置"),
    C("manager_id", BIG, "负责人"), C("status", TINY, "状态")),
    T("canteen_stock", "食堂库存", C("canteen_id", BIG, "食堂"), C("material_name", V64, "食材名称"),
      C("quantity", DEC, "数量"), C("unit", V64, "单位")))
VBP("pharmacy", "药房管理", T("pharmacy", "药房", C("pharmacy_name", V64, "药房名称"), C("location", V128, "位置"),
    C("manager_id", BIG, "负责人"), C("status", TINY, "状态")),
    T("drug_stock", "药品库存", C("pharmacy_id", BIG, "药房"), C("drug_id", BIG, "药品"),
      C("quantity", INT, "库存数"), C("expire_date", DATE, "有效期")))
VBP("medicalins", "医保结算", T("insurance_settlement", "医保结算", C("settlement_no", V64, "结算单号"),
    C("patient_id", BIG, "患者"), C("medical_amount", DEC, "医疗金额"), C("insurance_amount", DEC, "医保支付"),
    C("self_amount", DEC, "自费金额"), C("status", TINY, "状态")))
VBP("physical", "体检中心", T("physical_exam", "体检套餐", C("package_name", V128, "套餐名称"), C("price", DEC, "价格"),
    C("status", TINY, "状态")), T("exam_item", "体检项目", C("package_id", BIG, "套餐"),
    C("item_name", V128, "项目名称"), C("sort", INT, "排序")))
VBP("vaccine", "疫苗管理", T("vaccine", "疫苗目录", C("vaccine_name", V128, "疫苗名称"), C("vaccine_code", V64, "疫苗编码"),
    C("batch_no", V64, "批号"), C("stock_num", INT, "库存"), C("expire_date", DATE, "有效期")),
    T("vaccination", "接种记录", C("person_id", BIG, "接种人"), C("vaccine_id", BIG, "疫苗"),
      C("vaccinate_date", DATE, "接种日期"), C("status", TINY, "状态")))
VBP("dental", "口腔诊所", T("dental_chart", "牙位图记录", C("patient_id", BIG, "患者"), C("tooth_no", V64, "牙位"),
    C("diagnosis", V255, "诊断"), C("record_date", DATE, "记录日期")),
    T("dental_treatment", "诊疗项目", C("patient_id", BIG, "患者"), C("item_name", V128, "项目名称"),
      C("amount", DEC, "金额"), C("status", TINY, "状态")))
VBP("rehab", "康复管理", T("rehab_plan", "康复方案", C("patient_id", BIG, "患者"), C("plan_name", V128, "方案名称"),
    C("start_date", DATE, "开始日期"), C("status", TINY, "状态")),
    T("rehab_record", "康复记录", C("plan_id", BIG, "康复方案"), C("record_time", DT, "记录时间"),
      C("content", TXT, "康复内容"), C("therapist_id", BIG, "治疗师")))
VBP("household", "户籍管理", T("household", "户籍档案", C("household_no", V64, "户号"), C("head_name", V64, "户主"),
    C("address", V255, "住址"), C("status", TINY, "状态")),
    T("resident", "常住人口", C("household_id", BIG, "户籍"), C("name", V64, "姓名"),
      C("id_card", V64, "身份证号"), C("relation", V64, "与户主关系")))
VBP("petition", "信访管理", T("petition", "信访事项", C("petition_no", V64, "信访编号"), C("petitioner_id", BIG, "信访人"),
    C("content", TXT, "诉求内容"), C("status", TINY, "状态")),
    T("petition_reply", "办理答复", C("petition_id", BIG, "信访事项"), C("reply_user_id", BIG, "答复人"),
      C("content", TXT, "答复内容"), C("reply_time", DT, "答复时间")))
VBP("emergency", "应急管理", T("emergency", "应急预案", C("plan_name", V128, "预案名称"), C("plan_type", V64, "预案类型"),
    C("status", TINY, "状态"), C("content", TXT, "预案内容")),
    T("emergency_event", "应急事件", C("plan_id", BIG, "预案"), C("event_name", V128, "事件名称"),
      C("level", TINY, "事件级别"), C("occur_time", DT, "发生时间"), C("status", TINY, "状态")))
VBP("fire", "消防管理", T("fire_facility", "消防设施", C("facility_no", V64, "设施编号"), C("facility_name", V64, "设施名称"),
    C("location", V128, "位置"), C("status", TINY, "状态")),
    T("fire_check", "消防检查", C("facility_id", BIG, "设施"), C("check_date", DATE, "检查日期"),
      C("result", TINY, "检查结果"), C("checker_id", BIG, "检查人")))
VBP("chemical", "危化品管理", T("chemical", "危险化学品", C("chemical_name", V128, "化学品名称"), C("chemical_no", V64, "编号"),
    C("category", V64, "危化类别"), C("stock_num", DEC, "库存量")),
    T("chemical_stock", "危化品出入库", C("chemical_id", BIG, "危化品"), C("flow_type", TINY, "出入库类型"),
      C("quantity", DEC, "数量"), C("operator_id", BIG, "操作人"), C("flow_time", DT, "时间")))
VBP("food", "食品监管", T("food_business", "食品经营主体", C("business_name", V128, "主体名称"), C("license_no", V64, "许可证号"),
    C("address", V255, "地址"), C("status", TINY, "状态")),
    T("food_check", "食品检查", C("business_id", BIG, "经营主体"), C("check_date", DATE, "检查日期"),
      C("result", TINY, "检查结果"), C("checker_id", BIG, "检查人")))
VBP("drugreg", "药品监管", T("drug_business", "药品经营企业", C("company_name", V128, "企业名称"), C("license_no", V64, "许可证号"),
    C("address", V255, "地址"), C("status", TINY, "状态")),
    T("drug_check", "药品检查", C("business_id", BIG, "企业"), C("check_date", DATE, "检查日期"),
      C("result", TINY, "检查结果"), C("checker_id", BIG, "检查人")))
VBP("device", "医疗器械", T("medical_device", "医疗器械", C("device_name", V128, "器械名称"), C("reg_no", V64, "注册证号"),
    C("device_class", TINY, "管理类别"), C("manufacturer", V128, "生产企业"), C("status", TINY, "状态")),
    T("device_reg", "器械备案", C("device_id", BIG, "器械"), C("reg_no", V64, "备案号"),
      C("reg_date", DATE, "备案日期"), C("status", TINY, "状态")))
VBP("cosmetic", "化妆品管理", T("cosmetic", "化妆品", C("product_name", V128, "产品名称"), C("reg_no", V64, "备案编号"),
    C("manufacturer", V128, "生产企业"), C("status", TINY, "状态")),
    T("cosmetic_record", "备案记录", C("cosmetic_id", BIG, "化妆品"), C("record_no", V64, "记录编号"),
      C("record_date", DATE, "备案日期"), C("status", TINY, "状态")))

# ---------- 行业目录：key|名称|分类|蓝图列表 ----------
# 分类：通用企业/电商零售/批发分销/制造工业/物流供应链/金融财税/医疗健康/教育培训/
#      政务民生/房产建筑/餐饮酒店/文旅传媒/交通出行/农业环保/人力资源/法律法务/科研IT/生活服务/垂直行业
CATALOG = """
general_oa|通用OA办公|通用企业|notice meeting file schedule task approval audit dict
general_crm|客户资源管理|通用企业|customer contract order aftersale marketing report
general_erp|通用ERP管理|通用企业|product purchase inventory order finance report
general_project|项目型企业|通用企业|project task approval finance report file
general_doc|文档管理|通用企业|file article notice dict audit
general_meeting|会议管理|通用企业|meeting room notice schedule audit
general_asset|资产管理|通用企业|asset supplier inventory finance report
general_approval|审批中心|通用企业|approval notice task audit org
general_finance|财务核算|通用企业|finance invoice budget expense report
general_hr|人事管理|通用企业|employee org attendance salary performance recruit
general_report|报表中心|通用企业|report finance order inventory dict
general_knowledge|知识库|通用企业|article file dict notice audit
general_workflow|流程中心|通用企业|approval task notice audit org

retail_b2c|B2C商城|电商零售|product cart order payment aftersale member coupon
retail_b2b|B2B订货平台|电商零售|customer product order payment invoice contract
retail_o2o|O2O门店|电商零售|store product order payment member coupon aftersale
retail_fresh|生鲜电商|电商零售|product inventory cart order payment aftersale delivery
retail_crossborder|跨境电商|电商零售|product order payment customs invoice logistics
retail_social|社交电商|电商零售|member product order payment social marketing coupon
retail_live|直播带货|电商零售|product media order payment marketing aftersale social
retail_groupbuy|团购平台|电商零售|marketing coupon order payment product member
retail_seckill|秒杀平台|电商零售|marketing product order payment inventory report
retail_pos|零售POS|电商零售|store product inventory order payment member
retail_chain|连锁门店|电商零售|store product inventory order payment member report
retail_memberstore|会员商店|电商零售|member points coupon product order payment
retail_discount|折扣特卖|电商零售|product marketing coupon order payment aftersale inventory

wholesale_general|批发贸易|批发分销|supplier product inventory order payment invoice
wholesale_food|食品批发|批发分销|supplier product inventory order invoice trace
wholesale_apparel|服装批发|批发分销|supplier product product_sku inventory order
wholesale_electronics|电子批发|批发分销|supplier product product_sku inventory order aftersale
wholesale_pharma|医药批发|批发分销|supplier drug prescription inventory order invoice
wholesale_building|建材批发|批发分销|supplier product inventory order contract invoice
wholesale_auto|汽配批发|批发分销|supplier product product_sku inventory order aftersale
wholesale_agri|农资批发|批发分销|supplier agri agricultural_material inventory order
wholesale_beauty|美妆批发|批发分销|supplier product inventory order member marketing
wholesale_dealer|经销商管理|批发分销|customer supplier product order payment invoice report

mfg_discrete|离散制造|制造工业|product bom production process quality inventory
mfg_process|流程制造|制造工业|product production process quality equipment inventory
mfg_auto|汽车制造|制造工业|product bom production quality equipment supplier
mfg_electronics|电子制造|制造工业|product bom production quality equipment inventory
mfg_food|食品生产|制造工业|product production quality inventory trace supplier
mfg_pharma|制药生产|制造工业|drug production quality inventory prescription equipment
mfg_textile|纺织印染|制造工业|product production process quality inventory supplier
mfg_furniture|家具制造|制造工业|product bom production quality inventory supplier
mfg_machinery|机械制造|制造工业|product bom production process quality equipment
mfg_chemical|化工生产|制造工业|chemical production quality equipment inventory safety
mfg_steel|钢铁冶金|制造工业|production process quality equipment inventory energy
mfg_cement|水泥建材|制造工业|production quality equipment inventory energy supplier
mfg_paper|造纸印刷|制造工业|production process quality inventory equipment supplier
mfg_toy|玩具制造|制造工业|product bom production quality inventory supplier
mfg_shoe|鞋服制造|制造工业|product product_sku bom production quality inventory

logistics_express|快递物流|物流供应链|waybill delivery vehicle customer report
logistics_warehouse|仓储管理|物流供应链|warehouse inventory stock stock_flow delivery
logistics_coldchain|冷链物流|物流供应链|coldchain temperature_record warehouse inventory delivery
logistics_freight|货运配载|物流供应链|waybill delivery vehicle supplier report
logistics_port|港口物流|物流供应链|port berth vessel waybill delivery
logistics_air|航空货运|物流供应链|flight waybill delivery customer report
logistics_rail|铁路货运|物流供应链|waybill delivery warehouse vehicle report
logistics_ship|船运管理|物流供应链|vessel voyage waybill delivery port
logistics_park|物流园区|物流供应链|warehouse supplier vehicle waybill report
logistics_3pl|第三方物流|物流供应链|customer waybill delivery inventory report
logistics_crossborder|国际物流|物流供应链|customs waybill trade_document bonded port
logistics_lastmile|末端配送|物流供应链|delivery waybill vehicle customer report
logistics_supply|供应链协同|物流供应链|supplier purchase inventory order delivery report

fin_bank|银行核心|金融财税|customer loan finance invoice audit report
fin_credit|信贷管理|金融财税|loan customer finance approval report
fin_insurance|保险业务|金融财税|customer insurance insurance_claim payment report
fin_securities|证券交易|金融财税|customer trade_account trade_order finance report
fin_fund|基金管理|金融财税|investment investment_record customer finance report
fin_payment|支付平台|金融财税|payment payment_refund customer finance report
fin_settlement|清结算|金融财税|payment finance invoice expense report
fin_tax|税务管理|金融财税|tax tax_declaration tax_item invoice finance
fin_audit|审计管理|金融财税|audit finance expense budget report
fin_budget|预算管理|金融财税|budget budget_item expense finance report
fin_cost|成本管理|金融财税|finance expense budget purchase report
fin_invoice|电子发票|金融财税|invoice order payment finance report
fin_wealth|财富管理|金融财税|customer investment investment_record finance report
fin_p2p|互联网金融|金融财税|customer loan investment payment finance report
fin_leasing|融资租赁|金融财税|customer asset contract finance invoice report

med_hospital|综合医院|医疗健康|patient appointment medicalrecord prescription labtest nursing
med_clinic|诊所管理|医疗健康|patient appointment medicalrecord prescription pharmacy
med_dental|口腔诊所|医疗健康|patient appointment dental dental_treatment medicalrecord
med_tcm|中医馆|医疗健康|patient appointment medicalrecord prescription nursing
med_pharmacy|药房管理|医疗健康|pharmacy drug drug_stock prescription supplier
med_lab|检验中心|医疗健康|patient labtest lab_result medicalrecord report
med_imaging|影像中心|医疗健康|patient medicalrecord media lab_result appointment
med_physical|体检中心|医疗健康|patient physical exam_item appointment report
med_maternal|妇幼保健|医疗健康|patient appointment medicalrecord vaccine nursing
med_elderly|养老机构|医疗健康|elderly care_record nursing patient
med_rehab|康复中心|医疗健康|patient rehab rehab_plan medicalrecord nursing
med_mental|心理咨询|医疗健康|patient medicalrecord nursing survey appointment
med_vaccine|疫苗接种|医疗健康|vaccine vaccination patient appointment report
med_blood|血站管理|医疗健康|donor blood donation blood stock patient report
med_insurance|医保管理|医疗健康|patient medicalins medicalrecord invoice report

edu_k12|K12教育|教育培训|student student_class course exam tuition
edu_university|高校管理|教育培训|student course exam research paper tuition
edu_vocational|职业教育|教育培训|student course exam training tuition
edu_training|培训机构|教育培训|student course exam tuition employee
edu_online|在线教育|教育培训|course media exam student report
edu_preschool|幼儿园|教育培训|student student_class course nursing tuition
edu_driving|驾校管理|教育培训|driving driving_appointment student exam
edu_music|音乐培训|教育培训|student course exam tuition event
edu_art|美术培训|教育培训|student course exam tuition media
edu_sports|体育培训|教育培训|student course exam tuition event
edu_language|语言培训|教育培训|student course exam tuition translation
edu_exam|考试中心|教育培训|exam exam_score student course report
edu_library|图书馆|教育培训|book book_borrow report dict
edu_dorm|宿舍管理|教育培训|dorm dorm_checkin student report
edu_canteen|校园食堂|教育培训|canteen canteen_stock supplier report

gov_service|政务服务大厅|政务民生|approval notice visitor grid report
gov_household|户籍管理|政务民生|household resident approval report dict
gov_social|社保管理|政务民生|socialsec ss_payment employee report
gov_fund|公积金管理|政务民生|housingfund hf_payment employee report
gov_employment|就业服务|政务民生|employment job_seeker job_match report
gov_petition|信访管理|政务民生|petition petition_reply approval report
gov_legal|法律援助|政务民生|legalaid legalcase approval report
gov_notary|公证服务|政务民生|notary approval file report
gov_emergency|应急管理|政务民生|emergency emergency_event approval report
gov_safety|安全生产|政务民生|safety safety_issue inspection report
gov_fire|消防管理|政务民生|fire fire_facility safety report
gov_env|环保监管|政务民生|envmon env_monitor_point env_monitor_record report
gov_food|食品监管|政务民生|food food_business food_check report
gov_drug|药品监管|政务民生|drugreg drug_business drug_check report
gov_quality|质量监督|政务民生|inspection inspection_item certification report

re_dev|房地产开发|房产建筑|engineering project customer contract finance
re_sales|房产销售|房产建筑|customer project contract payment report
re_lease|房屋租赁|房产建筑|lease customer contract payment invoice
re_property|物业管理|房产建筑|property propfee repair visitor report
re_community|社区管理|房产建筑|grid grid_member resident propfee notice
con_project|工程项目管理|房产建筑|engineering engineering_task project budget report
con_build|施工管理|房产建筑|construction construction_log engineering safety
con_supervision|工程监理|房产建筑|inspection safety engineering report file
con_bid|招投标管理|房产建筑|bid bidder bid_record contract report
con_quality|工程质量|房产建筑|quality quality_issue inspection engineering
con_safety|施工安全|房产建筑|safety safety_issue construction report
con_cost|工程造价|房产建筑|budget budget_item engineering contract report

food_restaurant|中餐厅|餐饮酒店|dish dining store purchase inventory report
food_chain|连锁餐饮|餐饮酒店|dish dining store purchase inventory member report
food_fastfood|快餐管理|餐饮酒店|dish dining store order payment report
food_cafe|咖啡馆|餐饮酒店|dish dining store member coupon report
food_central|中央厨房|餐饮酒店|dish purchase inventory production delivery quality
food_delivery|外卖平台|餐饮酒店|dish dining delivery order payment customer
hotel_star|星级酒店|餐饮酒店|hotel room_type room hotel_booking customer report
hotel_chain|连锁酒店|餐饮酒店|hotel room hotel_booking member payment report
hotel_booking|酒店预订|餐饮酒店|hotel room hotel_booking payment customer
hotel_homestay|民宿管理|餐饮酒店|hotel room hotel_booking media customer
hotel_resort|度假村|餐饮酒店|hotel room hotel_booking scenic dining report
hotel_hostel|青年旅舍|餐饮酒店|hotel room hotel_booking customer payment

travel_agency|旅行社|文旅传媒|travel travel_order customer contract report
travel_scenic|景区管理|文旅传媒|scenic ticket_order visitor media report
travel_ticket|票务系统|文旅传媒|scenic ticket_order payment customer report
travel_guide|导游管理|文旅传媒|employee travel travel_order schedule report
travel_route|旅游线路|文旅传媒|travel travel_order customer media report
media_news|新闻门户|文旅传媒|article article_category media comment report
media_video|视频平台|文旅传媒|media article comment member report
media_live|直播平台|文旅传媒|media marketing order payment member report
media_ad|广告传媒|文旅传媒|ad ad_position ad_material customer report
media_publish|出版发行|文旅传媒|publication publication_stock customer order report
media_music|音乐平台|文旅传媒|media member comment report ad
media_event|会展演艺|文旅传媒|exhibition exhibitor event media report

tr_transit|公交管理|交通出行|bus bus_line vehicle report
tr_metro|地铁运营|交通出行|metro metro_line vehicle report
tr_taxi|网约车|交通出行|ride ride_driver vehicle payment report
tr_bike|共享单车|交通出行|bike bike_rental payment report
tr_parking|停车管理|交通出行|parking parking_lot payment vehicle report
tr_charging|充电站|交通出行|charging charging_station payment report
tr_gas|加油站|交通出行|gasstation fuel_order payment inventory report
tr_toll|高速公路|交通出行|toll toll_station vehicle payment report
tr_airport|机场管理|交通出行|flight flight_booking visitor vehicle report
tr_airline|航空公司|交通出行|flight flight_booking customer employee report
tr_railway|铁路客运|交通出行|train train_schedule ticket_order customer report
tr_ferry|轮渡管理|交通出行|vessel voyage ticket_order customer report
tr_vehicle|车辆管理|交通出行|vehicle vehicle_use employee report

agr_farm|农场管理|农业环保|land crop_plan agri livestock report
agr_crop|种植管理|农业环保|land crop_plan agri trace report
agr_livestock|养殖管理|农业环保|livestock vaccine_record agri trace report
agr_agri|农资管理|农业环保|agri agricultural_material material_stock supplier
agr_trace|农产品溯源|农业环保|trace trace_batch trace_record product report
agr_cooperative|合作社|农业环保|land customer agri finance report
agr_forestry|林业管理|农业环保|land crop_plan envmon report
agr_irrigation|灌溉管理|农业环保|land energy_meter energy_reading report
env_monitor|环境监测|农业环保|envmon env_monitor_point env_monitor_record report
env_waste|垃圾处理|农业环保|waste waste_station waste_record report
env_sewage|污水处理|农业环保|envmon energy_meter report inspection
env_energy|能源管理|农业环保|energy energy_meter energy_reading report

hr_recruit|招聘管理|人力资源|recruit candidate employee interview performance
hr_employee|员工管理|人力资源|employee org contract attendance report
hr_attendance|考勤管理|人力资源|attendance employee leave_request report
hr_salary|薪资管理|人力资源|salary employee attendance performance report
hr_performance|绩效考核|人力资源|performance performance_item employee report
hr_training|培训管理|人力资源|training employee course exam report
hr_outsourcing|劳务外包|人力资源|employee supplier contract salary report
hr_dispatch|劳务派遣|人力资源|employee supplier contract attendance report
hr_talent|人才管理|人力资源|employee talent candidate performance report
hr_labor|劳动关系|人力资源|employee contract attendance approval report

legal_lawfirm|律所管理|法律法务|legalcase case_participant contract employee report
legal_case|案件管理|法律法务|legalcase case_participant legalaid report
legal_contract|合同管理|法律法务|contract customer approval invoice report
legal_ip|知识产权|法律法务|ip ip_application patent report
legal_trademark|商标管理|法律法务|ip ip_application report dict
legal_copyright|版权管理|法律法务|ip ip_application publication report
legal_arbitration|仲裁管理|法律法务|arbitration legalcase contract report
legal_appraisal|司法鉴定|法律法务|inspection inspection_item legalcase report
legal_aid|法律援助|法律法务|legalaid legalcase approval report
legal_compliance|合规管理|法律法务|approval audit contract inspection report

it_dev|软件研发|科研IT|dev dev_project dev_issue testcase report
it_test|测试中心|科研IT|testcase test_suite test_case dev_issue report
it_devops|DevOps|科研IT|monitor monitor_target monitor_alert dev_issue report
it_monitor|运维监控|科研IT|monitor monitor_target monitor_alert audit report
it_data|数据平台|科研IT|report report_metric dev_project dict
it_ai|AI平台|科研IT|dev dev_project media report dict
it_iot|物联网|科研IT|device monitor energy_meter monitor_alert report
it_cloud|云资源管理|科研IT|device monitor budget report asset
it_security|信息安全|科研IT|audit visitor access_record monitor report
sci_project|科研项目管理|科研IT|research research_member paper budget report
sci_paper|论文成果|科研IT|paper patent research report
sci_lab|实验室管理|科研IT|lab lab_appointment device research report

life_fitness|健身俱乐部|生活服务|gym gym_course member payment report
life_beauty|美容美发|生活服务|beauty beautician shopappointment member payment
life_pet|宠物服务|生活服务|pet pet_service customer payment report
life_housekeep|家政服务|生活服务|housekeeper housekeep_order customer payment
life_repair|维修服务|生活服务|repairman repair_order customer payment
life_photo|摄影服务|生活服务|photo photo_order photo_asset customer payment
life_wedding|婚庆服务|生活服务|event photo customer contract payment
life_funeral|殡葬服务|生活服务|customer contract payment file report
life_moving|搬家服务|生活服务|housekeeper repair_order customer payment report
life_laundry|洗衣服务|生活服务|customer order payment member report
life_flowers|鲜花配送|生活服务|product delivery order payment member
life_cake|蛋糕烘焙|生活服务|dish dining order payment member
life_education|亲子教育|生活服务|student course exam event tuition

v_auto|汽车服务|垂直行业|vehicle autorepair customer payment member
v_autorepair|汽车维修|垂直行业|autorepair auto_repair_item customer payment
v_carrental|汽车租赁|垂直行业|car carrental customer payment contract
v_usedcar|二手车交易|垂直行业|car customer contract payment inspection
v_auction|拍卖行|垂直行业|auction auction_item customer payment report
v_pawn|典当行|垂直行业|pawn pawn_item customer payment report
v_recycle|再生资源|垂直行业|recycle recycle_order recycle_item supplier payment
v_bonded|保税仓|垂直行业|bonded bonded_warehouse bonded_stock customs
v_customs|报关服务|垂直行业|customs trade_document customer report
v_trade|外贸综合|垂直行业|customs trade_document customer contract payment
v_exhibition|会展服务|垂直行业|exhibition exhibitor event customer payment
v_translation|翻译服务|垂直行业|translation translator customer payment report
v_print|印刷服务|垂直行业|print print_order print_item customer payment
v_publication|出版物发行|垂直行业|publication publication_stock customer order report
v_certification|认证咨询|垂直行业|certification cert_audit customer inspection report
v_inspection|检验检测|垂直行业|inspection inspection_item customer report
v_metrology|计量检定|垂直行业|inspection inspection_item device report
v_standard|标准管理|垂直行业|dict article file approval report
v_archives|档案管理|垂直行业|file article dict approval audit
v_evidence|电子存证|垂直行业|file audit message report dict
"""


# 目录里两种 token 都允许：① 蓝图 key（整模块）② 单张表 tableName（精确取该表）。
# 少量语义别名映射到已有蓝图，保证目录可读且零孤儿。
ALIAS = {
    "logistics": "waybill", "points": "member", "store": "product",
    "lease": "contract", "property": "workorder", "propfee": "finance",
    "train": "flight", "train_schedule": "flight", "training": "course",
    "talent": "candidate", "interview": "candidate",
    "blood": "labtest", "donor": "patient", "donation": "labtest",
}


def build_resolver():
    """token -> (模块名, [表定义])；蓝图 key 展开整模块，子表名精确取单表。"""
    resolver = {}
    for bp_key, bp in BP_LIB.items():
        resolver[bp_key] = (bp["name"], bp["tables"])
    for bp_key, bp in BP_LIB.items():
        for table in bp["tables"]:
            resolver.setdefault(table["tableName"], (table["tableComment"], [table]))
    return resolver


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    resolver = build_resolver()
    seen_keys = set()
    categories = set()
    total_modules = 0
    total_tables = 0
    lines = [ln.strip() for ln in CATALOG.strip().splitlines() if ln.strip()]
    for line in lines:
        parts = line.split("|")
        if len(parts) != 4:
            raise ValueError("目录行格式错误: " + line)
        key, name, category, bp_keys = [p.strip() for p in parts]
        if key in seen_keys:
            raise ValueError("重复 key: " + key)
        seen_keys.add(key)
        categories.add(category)

        modules = []
        used_tables = set()
        for raw in bp_keys.split():
            token = ALIAS.get(raw, raw)
            if token not in resolver:
                raise ValueError("未知蓝图/表 %s（原 token=%s，行业=%s）" % (token, raw, key))
            mod_name, tables = resolver[token]
            kept = []
            for table in tables:
                if table["tableName"] in used_tables:
                    continue
                used_tables.add(table["tableName"])
                kept.append(json.loads(json.dumps(table, ensure_ascii=False)))
            if kept:
                modules.append({"name": mod_name, "tables": kept})
        if len(used_tables) < 3:
            raise ValueError("行业 %s 表数不足（%d）" % (key, len(used_tables)))

        module_names = "、".join(m["name"] for m in modules[:4])
        vo = OrderedDict()
        vo["key"] = key
        vo["name"] = name
        vo["category"] = category
        vo["desc"] = "%s场景：%s等核心业务域" % (name, module_names)
        # V9.10：写出前展开业务字段（幂等；不碰 BaseEntity 管理的审计列）
        for module in modules:
            for table in module["tables"]:
                expand_table(table)

        vo["modules"] = modules

        # V9.11：追加通用平台域 + 分类专属业务域，并做字段二次深化（幂等）
        deepen_vo(vo)

        if key in SKIP_KEYS:
            continue

        path = os.path.join(OUT_DIR, key + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(vo, f, ensure_ascii=False, indent=2)
            f.write("\n")

        total_modules += len(modules)
        total_tables += len(used_tables)

    generated = len(lines) - len(SKIP_KEYS & seen_keys)
    print("目录行业数=%d，跳过基线=%d，生成模板=%d" % (len(lines), len(SKIP_KEYS & seen_keys), generated))
    print("分类数=%d，蓝图数=%d，模块总数=%d，表总数=%d" % (len(categories), len(BP_LIB), total_modules, total_tables))
    assert 200 <= generated + len(SKIP_KEYS) <= 300, "行业总数应落在 200~300"
    assert len(seen_keys) == len(lines), "key 必须唯一"


if __name__ == "__main__":
    main()
