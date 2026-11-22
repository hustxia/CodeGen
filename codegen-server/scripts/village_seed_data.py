#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智慧村务系统：数据库初始化种子数据。

为 village_affairs 库生成初始化 SQL：
  1) 系统管理：角色、用户（复用 admin 的 BCrypt 哈希，密码 admin123）、菜单、角色菜单、字典
  2) 基础数据：村组、户籍户、村民、地块、网格、网格员
  3) 三资：资产、合同、资金账户、收支流水
  4) 党建治理：党组织、党员、三务公开、矛盾纠纷
  5) 便民服务：事项、申请（含流程状态）、办理环节
  6) 任务督办、决策：任务、大屏指标、通知、民情日志
  7) 流程引擎：sys_flow_def（节点 JSON）、审批单、审批节点

数据相互关联（户→人、地块→合同、账户→流水、事项→申请→环节），
让每个菜单点开都有真实内容，且能跑通一条完整审批流。

用法：
  python scripts/village_seed_data.py --out <sql文件>
  python scripts/village_seed_data.py --check   # 只统计不写文件
SQL 用 mysql 客户端执行：
  mysql -uroot -proot --default-character-set=utf8mb4 village_affairs < seed.sql
"""
import os
import sys
import argparse

HERE = os.path.dirname(os.path.abspath(__file__))

# admin 现有 BCrypt 哈希（密码 admin123），新用户复用，保证能登录
ADMIN_HASH = "$2a$10$BQz.XdhOV7sJOpH9z/BeheARd01UQKdchBsEgPfa.rYgmwTh75PaO"


# 列名重命名映射：脚本里的“意图列名” -> 实际表列名（按表结构核对后统一在此维护）
COL_RENAME = {
    "dict": {"dict_type": "type", "dict_label": "name", "dict_value": "value", "dict_name": "code"},
    "village_area": {"parent": "parent_id"},
    "village_party_org": {"leader": "secretary"},
    "village_disclosure": {"publish_date": "publish_time", "publisher": "publisher_id",
                          "category": "category", "disclosure_no": "disclosure_no"},
    "village_notice": {"publish_date": "publish_time", "publisher": "publisher_id"},
    "village_mood_log": {"visit_date": "create_time", "handler_id": "reporter_id"},
    "village_dashboard_metric": {"metric_value": "value", "stat_date": "remark"},
    "village_dispute": {"type": "dispute_type", "applicant": "party_a", "respondent": "party_b",
                        "occur_date": "create_time"},
    "village_grid": {"grid_leader": "grid_head"},
    "village_grid_member": {"name": "member_name"},  # member_no 非实际列，自动跳过
}

SCHEMA = {}  # table -> set(columns)


def load_schema(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) == 2:
                SCHEMA.setdefault(parts[0], set()).add(parts[1])


def q(v):
    """SQL 字面量：None->NULL，数字不引号，字符串转义并加引号。"""
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v).replace("\\", "\\\\").replace("'", "''")
    return "'" + s + "'"


def ins(table, cols, rows):
    """生成多行 INSERT。按实际表结构重命名/裁剪列，缺列自动跳过对应值。"""
    if not rows:
        return ""
    rename = COL_RENAME.get(table, {})
    actual = SCHEMA.get(table)
    norm_cols, keep_idx = [], []
    for i, c in enumerate(cols):
        c2 = rename.get(c, c)
        if actual is not None and c2 not in actual:
            continue  # 实际表无此列，跳过
        norm_cols.append(c2)
        keep_idx.append(i)
    if not norm_cols:
        return "-- 跳过 %s：无可用列\n" % table
    filtered = [tuple(r[i] for i in keep_idx) for r in rows]
    col_sql = ", ".join("`" + c + "`" for c in norm_cols)
    out = ["INSERT INTO `%s` (%s) VALUES" % (table, col_sql)]
    chunks = []
    for r in filtered:
        chunks.append("(" + ", ".join(q(v) for v in r) + ")")
    out.append(",\n".join(chunks) + ";")
    return "\n".join(out) + "\n"


# 复用菜单规划脚本的业务域定义，保证 sys_menu 与 gen-routes.js 一致
def load_domains():
    sys.path.insert(0, HERE)
    import village_menu_replan as M
    return M.DOMAINS, M.SYSTEM_PATH, M.SYSTEM_TITLE, M.class_name


def build_sql():
    domains, sys_path, sys_title, class_name = load_domains()
    parts = ["-- 智慧村务管理系统 初始化种子数据",
             "-- 由 scripts/village_seed_data.py 生成，可重复执行前请先清空业务表",
             "SET NAMES utf8mb4;\n"]

    # ============ 1. 系统管理：角色 ============
    parts.append("-- ===== 系统管理 =====")
    parts.append(ins("sys_role",
                     ["id", "role_name", "role_key", "remark", "deleted"],
                     [
                         (2, "村党支部书记", "village_party_secretary", "村务最高业务审批", 0),
                         (3, "村委会主任", "village_director", "村务经办与执行", 0),
                         (4, "网格员", "grid_member", "网格事件上报与处置", 0),
                         (5, "便民代办员", "service_agent", "便民事项受理", 0),
                     ]))

    # 用户（复用 admin 哈希）
    parts.append(ins("sys_user",
                     ["id", "username", "password", "password_algo", "nickname", "deleted"],
                     [
                         (2, "wangshuji", ADMIN_HASH, "bcrypt", "王书记", 0),
                         (3, "licunzhang", ADMIN_HASH, "bcrypt", "李村长", 0),
                         (4, "wanggeyuan", ADMIN_HASH, "bcrypt", "张网格员", 0),
                         (5, "daiban", ADMIN_HASH, "bcrypt", "赵代办员", 0),
                     ]))
    parts.append(ins("sys_user_role", ["user_id", "role_id"],
                     [(2, 2), (3, 3), (4, 4), (5, 5)]))

    # 菜单：一级系统 + 二级业务域 + 三级页面
    menu_rows = []
    mid = 1
    menu_rows.append((mid, 0, sys_title, "/" + sys_path, None, None, 1, 1, 1, 0)); sys_id = mid; mid += 1
    for gidx, (code, label, tables) in enumerate(domains, start=1):
        gid = mid
        menu_rows.append((gid, sys_id, label, "/" + sys_path + "/" + code, None, None, 1, gidx, 1, 0)); mid += 1
        for pidx, t in enumerate(tables, start=1):
            comment = label  # 由脚本渲染时会替换，这里用业务域名占位
            menu_rows.append((mid, gid, t, "/" + sys_path + "/" + code + "/" + t + "/list",
                              None, sys_path + ":" + t + ":list", 2, pidx, 1, 0)); mid += 1
    parts.append(ins("sys_menu",
                     ["id", "parent_id", "name", "path", "component", "perms", "type", "sort", "status", "deleted"],
                     menu_rows))
    # 全部菜单授权给 admin 角色（角色1）和村支书（角色2）
    parts.append("INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) "
                 "SELECT 1,`id` FROM `sys_menu` WHERE `deleted`=0;")
    parts.append("INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) "
                 "SELECT 2,`id` FROM `sys_menu` WHERE `deleted`=0;\n")

    # 字典
    dict_rows = []
    for i, (typ, label, val, txt, sort) in enumerate([
        ("village_status", "村务状态", "0", "草稿", 1),
        ("village_status", "村务状态", "1", "待审批", 2),
        ("village_status", "村务状态", "2", "已通过", 3),
        ("village_status", "村务状态", "3", "已驳回", 4),
        ("village_gender", "性别", "1", "男", 1),
        ("village_gender", "性别", "2", "女", 2),
        ("household_type", "户类型", "1", "普通农户", 1),
        ("household_type", "户类型", "2", "低保户", 2),
        ("household_type", "户类型", "3", "五保户", 3),
        ("land_type", "土地类型", "1", "耕地", 1),
        ("land_type", "土地类型", "2", "林地", 2),
        ("land_type", "土地类型", "3", "宅基地", 3),
    ], start=1):
        dict_rows.append((i, typ, label, val, txt, sort, 1, 0))
    parts.append(ins("dict", ["id", "dict_type", "dict_label", "dict_value", "dict_name", "sort", "status", "deleted"],
                     dict_rows) + "\n")

    # ============ 2. 基础数据 ============
    parts.append("-- ===== 基础数据：村组/户/村民/地块/网格 =====")
    areas = [
        (1, "V001", "莲花村", 0, "村", "行政村", 2860, 812, "王建国", "13800138001", 1, 1),
        (2, "V001-01", "莲花村一组", 1, "组", "村民小组", 720, 205, "李长福", "13800138002", 1, 1),
        (3, "V001-02", "莲花村二组", 1, "组", "村民小组", 680, 193, "张国平", "13800138003", 1, 2),
        (4, "V001-03", "莲花村三组", 1, "组", "村民小组", 790, 226, "陈有田", "13800138004", 1, 3),
        (5, "V001-04", "莲花村四组", 1, "组", "村民小组", 670, 188, "刘桂花", "13800138005", 1, 4),
    ]
    parts.append(ins("village_area",
                     ["id", "area_code", "area_name", "parent_id", "area_level", "area_type",
                      "population", "households", "leader", "leader_phone", "sort", "status"], areas))

    households = [
        (1, "H20240001", "李长福", "110101197001011234", 4, "莲花村一组李家巷12号", 0.85, "普通农户", 1, 2, 1, 1),
        (2, "H20240002", "张国平", "110101196803152345", 3, "莲花村二组张家庄8号", 0.62, "普通农户", 1, 3, 1, 1),
        (3, "H20240003", "陈有田", "110101197505203456", 5, "莲花村三组陈家湾21号", 1.20, "普通农户", 1, 4, 1, 1),
        (4, "H20240004", "刘桂花", "110101195811064567", 2, "莲花村四组刘家院3号", 0.45, "低保户", 1, 5, 1, 1),
        (5, "H20240005", "王建国", "110101196610128901", 4, "莲花村一组村委会旁", 0.95, "普通农户", 1, 2, 1, 1),
    ]
    parts.append(ins("village_household",
                     ["id", "household_no", "head_name", "head_id_card", "member_count", "addr",
                      "area", "household_type", "village_id", "grid_id", "status", "remark"], households))

    residents = [
        (1, "李长福", "110101197001011234", "男", "1970-01-01", "汉族", "13800138002", "初中", "务农", 1, 1, 2, 1, "户主"),
        (2, "李秀英", "110101197203088765", "女", "1972-03-08", "汉族", None, "小学", "务农", 1, 1, 2, 1, "配偶"),
        (3, "李小明", "110101199509126789", "男", "1995-09-12", "汉族", "13800138333", "本科", "外出务工", 1, 1, 2, 1, "子女"),
        (4, "张国平", "110101196803152345", "男", "1968-03-15", "汉族", "13800138003", "高中", "务农", 2, 1, 3, 1, "户主"),
        (5, "张丽", "110101199207134321", "女", "1992-07-13", "汉族", None, "大专", "本地就业", 2, 1, 3, 1, "子女"),
        (6, "陈有田", "110101197505203456", "男", "1975-05-20", "汉族", "13800138004", "初中", "务农", 3, 1, 4, 1, "户主"),
        (7, "刘桂花", "110101195811064567", "女", "1958-11-06", "汉族", "13800138005", "小学", "无", 4, 1, 5, 1, "低保对象"),
        (8, "王建国", "110101196610128901", "男", "1966-10-12", "汉族", "13800138001", "高中", "村支书", 5, 1, 2, 1, "户主/村干部"),
    ]
    parts.append(ins("village_resident",
                     ["id", "resident_name", "id_card", "gender", "birth_date", "ethnicity", "phone",
                      "education", "occupation", "household_id", "village_id", "grid_id", "status", "remark"], residents))

    parcels = [
        (1, "DK2024001", "东湾责任田", 12.5, "耕地", "莲花村东湾", "大路", "水渠", "张家地", "陈家地", 1, 1),
        (2, "DK2024002", "南坡林地", 30.0, "林地", "莲花村南坡", "山脊", "村道", "李某地", "荒坡", 1, 1),
        (3, "DK2024003", "西塘水田", 8.2, "耕地", "莲花村西塘", "塘埂", "机耕路", "水渠", "王某地", 1, 1),
        (4, "DK2024004", "村委会宅基地", 0.6, "宅基地", "莲花村村委会", "村道", "李某房", "空地", "水渠", 1, 1),
    ]
    parts.append(ins("village_land_parcel",
                     ["id", "parcel_no", "name", "area", "land_type", "location", "east_to", "west_to",
                      "south_to", "north_to", "owner_id", "status"], parcels))

    grids = [
        (1, "WG001", "第一网格", 1, "王建国", "13800138001", 812, 1, 1),
        (2, "WG002", "第二网格", 1, "张网格员", "13800138888", 680, 1, 2),
        (3, "WG003", "第三网格", 1, "陈有田", "13800138004", 790, 1, 3),
    ]
    parts.append(ins("village_grid",
                     ["id", "grid_code", "grid_name", "village_id", "grid_leader", "leader_phone",
                      "population", "status", "sort"], grids))

    parts.append(ins("village_grid_member",
                     ["id", "member_no", "name", "phone", "grid_id", "role", "status"],
                     [(1, "GM001", "王建国", "13800138001", 1, "网格长", 1),
                      (2, "GM002", "张网格员", "13800138888", 2, "网格员", 1),
                      (3, "GM003", "陈有田", "13800138004", 3, "网格员", 1)]) + "\n")

    # ============ 3. 三资 ============
    parts.append("-- ===== 三资管理：资产/合同/资金/流水 =====")
    assets = [
        (1, "ZC2024001", "村委会办公楼", "房屋建筑", "固定资产", "砖混三层", 850000, 1, "2015-06-01", 1, "莲花村村委会", 1, "在用"),
        (2, "ZC2024002", "村集体拖拉机等农机", "机械设备", "固定资产", "东方红", 120000, 3, "2019-03-15", 2, "农机库", 1, "在用"),
        (3, "ZC2024003", "村集体鱼塘", "资源性资产", "农业设施", "30亩", 200000, 1, "2018-09-10", 3, "莲花村南塘", 1, "发包中"),
        (4, "ZC2024004", "文化活动广场", "公共设施", "固定资产", "水泥硬化", 320000, 1, "2020-10-01", 1, "莲花村中心", 1, "在用"),
    ]
    parts.append(ins("village_asset",
                     ["id", "asset_no", "asset_name", "category", "type", "model", "amount", "quantity",
                      "purchase_date", "custodian_id", "location", "status", "remark"], assets))

    contracts = [
        (1, "HT2024001", "东湾责任田承包合同", "土地承包", "莲花村集体经济合作社", "李长福",
         "2024-01-01 00:00:00", "2028-12-31 23:59:59", 12500, "年付", "2024-01-01", 1, "履行中"),
        (2, "HT2024002", "南塘鱼塘发包合同", "鱼塘发包", "莲花村集体经济合作社", "张国平",
         "2024-03-01 00:00:00", "2027-02-28 23:59:59", 60000, "年付", "2024-03-01", 1, "履行中"),
        (3, "HT2024003", "村集体门面房租赁合同", "房屋租赁", "莲花村集体经济合作社", "王建国",
         "2025-01-01 00:00:00", "2027-12-31 23:59:59", 36000, "半年付", "2025-01-01", 1, "履行中"),
    ]
    parts.append(ins("village_contract",
                     ["id", "contract_no", "name", "type", "party_a", "party_b", "start", "end",
                      "amount", "pay_type", "sign_date", "status", "remark"], contracts))

    accounts = [
        (1, "ZH2024001", "莲花村集体经济基本户", "中国农业银行莲花支行", "基本户", 1256800.00, 1, "2018-01-01", 1, "村集体资金主账户"),
        (2, "ZH2024002", "莲花村专户（惠农补贴）", "农村商业银行莲花支行", "专户", 286500.00, 1, "2020-05-01", 1, "专项资金"),
    ]
    parts.append(ins("village_fund_account",
                     ["id", "account_no", "account_name", "bank", "type", "balance", "owner_id",
                      "open_date", "status", "remark"], accounts))

    transactions = [
        (1, "JY20240101", 1, "收入", "承包款", 12500.00, "1244300.00", "1256800.00", "东湾责任田承包款", "2024-01-10", 1),
        (2, "JY20240102", 1, "支出", "办公费", 3500.00, "1256800.00", "1253300.00", "村委会办公用品采购", "2024-01-15", 2),
        (3, "JY20240103", 1, "收入", "租赁收入", 18000.00, "1253300.00", "1271300.00", "门面房半年租金", "2024-01-20", 2),
        (4, "JY20240201", 2, "收入", "财政拨款", 50000.00, "236500.00", "286500.00", "惠农专项资金拨款", "2024-02-01", 2),
        (5, "JY20240202", 1, "支出", "公益支出", 12000.00, "1271300.00", "1259300.00", "文化活动广场维护", "2024-02-05", 2),
    ]
    parts.append(ins("village_fund_transaction",
                     ["id", "transaction_no", "account_id", "type", "direction", "amount",
                      "before_balance", "after_balance", "summary", "transaction_date", "status"], transactions) + "\n")

    # ============ 4. 党建治理 ============
    parts.append("-- ===== 党建治理：党组织/党员/公开/纠纷 =====")
    parts.append(ins("village_party_org",
                     ["id", "org_name", "org_type", "leader", "member_count", "parent_id", "status"],
                     [(1, "中共莲花村支部委员会", "党支部", "王建国", 28, 0, 1),
                      (2, "莲花村第一党小组", "党小组", "李长福", 14, 1, 1),
                      (3, "莲花村第二党小组", "党小组", "张国平", 14, 1, 1)]))
    party_members = [
        (1, "D001", "王建国", "男", "1966-10-12", "110101196610128901", "13800138001", "高中", "1998-07-01", "正式党员", 1, 1, "支部书记"),
        (2, "D002", "李长福", "男", "1970-01-01", "110101197001011234", "13800138002", "初中", "2005-06-15", "正式党员", 1, 1, "党小组长"),
        (3, "D003", "张国平", "男", "1968-03-15", "110101196803152345", "13800138003", "高中", "2008-11-20", "正式党员", 1, 1, "党小组长"),
        (4, "D004", "陈有田", "男", "1975-05-20", "110101197505203456", "13800138004", "初中", "2012-05-04", "正式党员", 1, 1, "党员"),
        (5, "D005", "刘桂花", "女", "1958-11-06", "110101195811064567", "13800138005", "小学", "1985-09-10", "正式党员", 1, 1, "老党员"),
    ]
    parts.append(ins("village_party_member",
                     ["id", "member_no", "name", "gender", "birth_date", "id_card", "phone",
                      "education", "join_date", "party_status", "org_id", "status", "remark"], party_members))

    disclosures = [
        (1, "GK2024001", "2024年度村集体经济收益分配方案", "财务公开", "收益分配", "经村民代表大会表决通过，年度收益按人口与贡献分配", 1, "2025-01-15 00:00:00", "全村", 2, "已公开"),
        (2, "GK2024002", "莲花村2024年度财务收支决算", "财务公开", "财务决算", "全年收支决算情况公示", 1, "2025-01-20 00:00:00", "全村", 2, "已公开"),
        (3, "GK2024003", "村内道路硬化工程招标公示", "村务公开", "工程招标", "村内主干道硬化工程公开招标", 2, "2025-03-01 00:00:00", "全村", 1, "待审核"),
    ]
    parts.append(ins("village_disclosure",
                     ["id", "disclosure_no", "title", "category", "type", "content", "publisher_id",
                      "publish_time", "scope", "status", "remark"], disclosures))

    disputes = [
        (1, "JF2024001", "李某与张某宅基地边界纠纷", "邻里纠纷", "李长福", "张国平", "莲花村一组", "2024-05-10", 1, 1, "已调解"),
        (2, "JF2024002", "鱼塘承包款支付纠纷", "经济纠纷", "张国平", "村委会", "莲花村南塘", "2024-08-20", 1, 2, "调解中"),
    ]
    parts.append(ins("village_dispute",
                     ["id", "case_no", "title", "type", "applicant", "respondent", "location",
                      "occur_date", "mediator_id", "status", "remark"], disputes) + "\n")

    # ============ 5. 便民服务 ============
    parts.append("-- ===== 便民服务：事项/申请/环节 =====")
    items = [
        (1, "FW001", "生育登记服务", "社会保障", "个人", "卫健办", 3, 0, "身份证、结婚证", "窗口办理", 1, "即办"),
        (2, "FW002", "农村低保申请", "社会救助", "个人", "民政办", 15, 0, "申请表、收入证明", "窗口办理", 1, "需审核"),
        (3, "FW003", "宅基地使用权申请", "国土规划", "个人", "国土所", 20, 0, "申请表、户籍证明", "窗口办理", 1, "需审批"),
        (4, "FW004", "新型农村合作医疗参保", "医疗卫生", "个人", "医保办", 5, 0, "身份证、户口本", "网上办理", 1, "即办"),
        (5, "FW005", "证明类事项代办", "便民代办", "个人", "村委会", 2, 0, "身份证", "帮办代办", 1, "即办"),
    ]
    parts.append(ins("village_service_item",
                     ["id", "item_code", "item_name", "category", "type", "department", "limit_days",
                      "fee", "material", "channel", "status", "remark"], items))

    applies = [
        (1, "SQ20240001", 2, 1, "李长福", "13800138002", "2024-06-01", "申请表、收入证明", 1, "已办理完成", 2, "低保申请"),
        (2, "SQ20240002", 3, 4, "张国平", "13800138003", "2024-07-15", "申请表、户籍证明", 2, "待村委会审核", 1, "宅基地申请"),
        (3, "SQ20240003", 1, 3, "陈有田", "13800138004", "2024-09-20", "身份证、结婚证", 2, "待受理", 1, "生育登记"),
        (4, "SQ20240004", 5, 5, "刘桂花", "13800138005", "2024-10-08", "身份证", 5, "已办结", 2, "代办证明"),
    ]
    parts.append(ins("village_service_apply",
                     ["id", "apply_no", "item_id", "applicant_id", "applicant_name", "phone",
                      "apply_date", "material", "handler_id", "result", "status", "remark"], applies))

    parts.append(ins("village_service_flow",
                     ["id", "apply_id", "node_name", "node_order", "handler_id", "action", "result",
                      "comment", "status"],
                     [(1, 1, "受理登记", "1", 1, "受理", "材料齐全，予以受理", "已核对材料", 2),
                      (2, 1, "村委会初审", "2", 2, "初审通过", "符合低保条件", "入户调查属实", 2),
                      (3, 1, "办结出件", "3", 1, "办结", "低保申请已批准", "通知申请人", 2),
                      (4, 2, "受理登记", "1", 5, "受理", "已受理", "", 2),
                      (5, 2, "现场勘查", "2", 2, "勘查中", "", "待国土资源所联合勘查", 1)]) + "\n")

    # ============ 6. 任务督办 + 决策 ============
    parts.append("-- ===== 任务督办/决策：任务/指标/通知/民情 =====")
    tasks = [
        (1, "RW2024001", "村内主干道硬化工程推进", "重点工程", "乡镇督办", "完成村内2公里主干道硬化", 2,
         "2024-03-01 00:00:00", "2024-10-31 23:59:59", 2, 85, "施工中", 1, "已完工85%"),
        (2, "RW2024002", "人居环境整治专项行动", "环境整治", "乡镇督办", "清理垃圾死角、整治村容村貌", 3,
         "2024-05-01 00:00:00", "2024-09-30 23:59:59", 3, 100, "已完成", 2, "通过验收"),
        (3, "RW2024003", "低保户入户走访排查", "民生保障", "村委自定", "对全村低保户逐户走访", 4,
         "2024-08-01 00:00:00", "2024-09-15 23:59:59", 4, 100, "已完成", 2, "走访12户"),
    ]
    parts.append(ins("village_task",
                     ["id", "task_no", "title", "type", "source", "content", "assignee_id", "start",
                      "due_time", "priority", "progress", "result", "status", "remark"], tasks))

    parts.append(ins("village_dashboard_metric",
                     ["id", "metric_code", "metric_name", "metric_value", "unit", "stat_date", "status"],
                     [(1, "POPULATION", "常住人口", 2860, "人", "2025-01-01", 1),
                      (2, "HOUSEHOLDS", "户籍户数", 812, "户", "2025-01-01", 1),
                      (3, "LAND_AREA", "耕地面积", 1280.5, "亩", "2025-01-01", 1),
                      (4, "COLLECTIVE_INCOME", "集体经济收入", 1256800.00, "元", "2025-01-01", 1),
                      (5, "PARTY_MEMBERS", "党员人数", 28, "人", "2025-01-01", 1),
                      (6, "GRID_COUNT", "治理网格数", 3, "个", "2025-01-01", 1)]))
    parts.append(ins("village_notice",
                     ["id", "notice_no", "title", "type", "content", "publisher_id", "publish_time", "status"],
                     [(1, "TZ2024001", "关于2025年春节放假安排的通知", "通知", "春节期间村委会放假7天，如有急事请联系值班人员", 1, "2025-01-20 00:00:00", 2),
                      (2, "TZ2024002", "关于开展人居环境整治的通知", "公告", "全村开展人居环境整治，请各户积极配合", 3, "2025-03-01 00:00:00", 2)]))
    parts.append(ins("village_mood_log",
                     ["id", "log_no", "resident_id", "resident_name", "content", "visit_date", "handler_id", "status"],
                     [(1, "MQ2024001", 1, "李长福", "反映东湾灌溉水渠年久失修，希望村委协调修缮", "2024-06-15", 2, 2),
                      (2, "MQ2024002", 7, "刘桂花", "低保金发放及时，对生活保障满意", "2024-08-20", 1, 2),
                      (3, "MQ2024003", 4, "张国平", "建议村内增设路灯，夜间出行不便", "2024-09-10", 3, 1)]) + "\n")

    # ============ 7. 流程引擎定义 ============
    parts.append("-- ===== 流程引擎：sys_flow_def + 审批单 + 节点 =====")
    # 节点 JSON：from=0 草稿 -> 1 待审批(提交) -> 2 通过 / 3 驳回
    nodes_service = ('[{"from":"0","to":"1","name":"提交申请"},'
                     '{"from":"1","to":"2","name":"村委审批","roleCode":"village_director","rejectTo":"3"},'
                     '{"from":"1","to":"2","name":"代办办结","roleCode":"service_agent"}]')
    nodes_approval = ('[{"from":"0","to":"1","name":"提交审批"},'
                      '{"from":"1","to":"2","name":"书记审批","roleCode":"village_party_secretary","rejectTo":"3"}]')
    flows = [
        (1, "village_service_apply", "便民办事审批流", "village_service_apply", "status", nodes_service, 1, "便民事项从提交到办结"),
        (2, "village_approval", "村务通用审批流", "approval", "status", nodes_approval, 1, "村务事项审批"),
    ]
    parts.append(ins("sys_flow_def",
                     ["id", "flow_code", "flow_name", "table_name", "status_col", "nodes", "enabled", "remark"], flows))

    approvals = [
        (1, "SP20240001", "村委会办公楼维修申请", "费用报销", 35000, 1, "书记审批", 2, "同意维修，从办公经费列支", "2024-05-10", "2024-05-15"),
        (2, "SP20240002", "文化活动广场设备采购", "采购申请", 18000, 1, "书记审批", 1, "", "2024-08-20", None),
        (3, "SP20240003", "困难户临时救助申请", "救助审批", 5000, 4, "书记审批", 3, "不符合条件，驳回", "2024-09-01", "2024-09-03"),
    ]
    parts.append(ins("approval",
                     ["id", "no", "title", "type", "amount", "applicant_id", "current_node", "status",
                      "result", "submit_time", "finish_time"], approvals))
    parts.append(ins("approval_node",
                     ["id", "approval_id", "node_name", "node_order", "assignee_id", "status", "result", "comment"],
                     [(1, 1, "提交申请", 1, 1, 2, "已提交", ""),
                      (2, 1, "书记审批", 2, 2, 2, "同意", "从办公经费列支"),
                      (3, 2, "提交申请", 1, 3, 2, "已提交", ""),
                      (4, 2, "书记审批", 2, 2, 1, "待审批", ""),
                      (5, 3, "提交申请", 1, 4, 2, "已提交", ""),
                      (6, 3, "书记审批", 2, 2, 3, "驳回", "不符合救助条件")]) + "\n")

    # 流程流转历史（供 /flow/records 展示）
    parts.append(ins("sys_flow_task",
                     ["id", "flow_code", "biz_table", "biz_id", "from_status", "to_status", "action",
                      "comment", "oper_user_id", "oper_name"],
                     [(1, "village_service_apply", "village_service_apply", 1, "0", "1", "SUBMIT", "提交低保申请", 1, "李长福"),
                      (2, "village_service_apply", "village_service_apply", 1, "1", "2", "APPROVE", "低保申请已批准", 2, "王书记"),
                      (3, "village_service_apply", "village_service_apply", 2, "0", "1", "SUBMIT", "提交宅基地申请", 4, "张国平"),
                      (4, "village_approval", "approval", 1, "0", "1", "SUBMIT", "提交维修申请", 1, "李长福"),
                      (5, "village_approval", "approval", 1, "1", "2", "APPROVE", "同意维修", 2, "王书记")]))

    return "\n".join(parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "village_seed.sql"))
    ap.add_argument("--schema", default=os.path.join(HERE, "village_schema.txt"))
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    load_schema(args.schema)
    sql = build_sql()
    n_insert = sql.count("INSERT INTO")
    print("生成 SQL: %d 字符, %d 条 INSERT" % (len(sql), n_insert))
    if args.check:
        return 0
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(sql)
    print("已写入:", args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
