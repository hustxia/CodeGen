#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智慧村务系统：一级/二级菜单重新规划。

现状：生成器的 GenMenuGroup 是仓储场景关键词规则，village_ 表全落「其他」，
菜单二级分组变成 其他(66页)/计费/库存/流程/调度 等与村务无关的分组。

本脚本按村务业务域重新规划二级分组（12 个业务域，替代原 6 个混乱分组），
直接重建生成产物的 src/router/gen-routes.js（前端 9040 会热更新立即生效）。

同时 --rules 输出可沉淀到 GenMenuGroup.java 的村务规则清单（下次生成自动正确）。

用法：
  python scripts/village_menu_replan.py                 # 重建产物 gen-routes.js
  python scripts/village_menu_replan.py --target <web目录>
  python scripts/village_menu_replan.py --rules         # 输出 GenMenuGroup 规则
  python scripts/village_menu_replan.py --check         # 只校验，不写文件
"""
import json
import os
import re
import sys
import argparse

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, ".."))
TEMPLATE = os.path.join(REPO, "src", "main", "resources", "industry-templates",
                        "village_affairs.json")
DEFAULT_WEB = os.path.normpath(os.path.join(REPO, "..", "codegen-demo-target",
                                            "village_affairs-web"))
ROUTES_REL = os.path.join("src", "router", "gen-routes.js")

SYSTEM_PATH = "village_affairs"
SYSTEM_TITLE = "智慧村务管理系统"

# 二级业务域图标（Element Plus 图标组件名，须在 main.js 全局注册或按需导入）
# Menu美观：二级面板每个业务域配专属图标，三级页面用统一色点，视觉上一眼可辨。
DOMAIN_ICONS = {
    "base": "OfficeBuilding",      # 基础数据
    "land": "MapLocation",         # 房屋土地
    "grid": "Location",            # 网格档案
    "party": "Flag",               # 党建管理
    "openness": "Document",        # 村务公开
    "dispute": "ChatDotRound",     # 矛盾调解
    "assets": "Wallet",            # 三资管理
    "service": "Service",          # 便民服务
    "env": "Sunrise",              # 人居环境
    "supervise": "AlarmClock",     # 任务督办
    "decision": "DataAnalysis",    # 决策分析
    "system": "Setting",           # 系统管理
}

# 二级业务域：(code, 显示名, 表名前缀/精确表名列表)
# 顺序即菜单显示顺序；每张 village_ 表恰好归入一个业务域。
DOMAINS = [
    ("base",     "基础数据",   ["village_area", "village_household", "village_resident",
                              "village_resident_relation", "village_population_change",
                              "village_special_group", "village_enterprise"]),
    ("land",     "房屋土地",   ["village_house", "village_land_parcel",
                              "village_land_contract", "village_land_transfer"]),
    ("grid",     "网格档案",   ["village_grid", "village_grid_member", "village_grid_event",
                              "village_archive", "village_archive_file", "village_archive_borrow"]),
    ("party",    "党建管理",   ["village_party_org", "village_party_member",
                              "village_party_member_change", "village_party_meeting",
                              "village_party_activity", "village_party_learning",
                              "village_party_fee", "village_party_assess",
                              "village_party_volunteer"]),
    ("openness", "村务公开",   ["village_disclosure", "village_disclosure_file",
                              "village_disclosure_feedback", "village_meeting",
                              "village_motion", "village_vote", "village_vote_record"]),
    ("dispute",  "矛盾调解",   ["village_dispute", "village_dispute_mediator",
                              "village_dispute_record", "village_dispute_visit"]),
    ("assets",   "三资管理",   ["village_asset", "village_asset_change", "village_asset_inventory",
                              "village_resource", "village_contract", "village_contract_payment",
                              "village_fund_account", "village_fund_transaction",
                              "village_fund_budget"]),
    ("service",  "便民服务",   ["village_service_item", "village_service_apply",
                              "village_service_material", "village_service_flow",
                              "village_service_evaluate", "village_service_agent"]),
    ("env",      "人居环境",   ["village_env_inspect", "village_env_issue",
                              "village_env_clean_task", "village_env_facility"]),
    ("supervise","任务督办",   ["village_task", "village_task_assign",
                              "village_task_feedback", "village_task_score"]),
    ("decision", "决策分析",   ["village_dashboard_metric", "village_dashboard_snapshot",
                              "village_mood_log", "village_mood_visit", "village_mood_tag",
                              "village_assess_plan", "village_assess_score",
                              "village_assess_result", "village_notice", "village_stat_report"]),
    ("system",   "系统管理",   ["approval", "approval_node", "attachment", "operation_log",
                              "notice", "document", "task", "task_item", "feedback",
                              "tag", "tag_ref", "dict", "config", "audit_trail", "message"]),
]


def load_tables():
    """从模板读出 tableName -> (comment, moduleName)，保持顺序。"""
    with open(TEMPLATE, encoding="utf-8") as f:
        vo = json.load(f)
    tables = {}
    for m in vo["modules"]:
        for t in m["tables"]:
            tables[t["tableName"]] = (t.get("tableComment", ""), m["name"])
    return tables


def class_name(table):
    return "".join(w[:1].upper() + w[1:] for w in table.split("_"))


def domain_of(table, table_to_domain):
    return table_to_domain.get(table)


def build_table_to_domain():
    m = {}
    for code, label, tables in DOMAINS:
        for t in tables:
            if t in m:
                raise ValueError("表 %s 同时归入 %s 和 %s" % (t, m[t], code))
            m[t] = (code, label)
    return m


def render(tables, table_to_domain):
    """生成新的 gen-routes.js 文本。用元素收集 + join，避免手动管理逗号/闭合。"""
    indent = lambda n: " " * n

    def page_block(t, code):
        comment, _module = tables[t]
        return "\n".join([
            indent(10) + "{",
            indent(12) + "path: '%s/list'," % t,
            indent(12) + "name: '%s'," % class_name(t),
            indent(12) + "component: () => import('@/views/%s/index.vue')," % t,
            indent(12) + "meta: { title: '%s', i18nKey: 'menu.%s.%s.%s' }" % (comment, SYSTEM_PATH, code, t),
            indent(10) + "}",
        ])

    def domain_block(code, label, tnames):
        pages = ",\n".join(page_block(t, code) for t in tnames)
        return "\n".join([
            indent(6) + "{",
            indent(8) + "path: '%s'," % code,
            indent(8) + "component: GroupView,",
            indent(8) + "meta: { title: '%s', i18nKey: 'menu.%s.%s', icon: '%s' }," % (label, SYSTEM_PATH, code, DOMAIN_ICONS.get(code, 'Menu')),
            indent(8) + "children: [",
            pages,
            indent(8) + "]",
            indent(6) + "}",
        ])

    assigned = set()
    domains = []
    for code, label, tnames in DOMAINS:
        for t in tnames:
            if t not in tables:
                raise ValueError("模板中不存在表：" + t)
            assigned.add(t)
        domains.append(domain_block(code, label, tnames))

    missing = set(tables) - assigned
    if missing:
        raise ValueError("以下表未分配业务域：" + ", ".join(sorted(missing)))

    domains_text = ",\n".join(domains)
    text = "\n".join([
        "/* eslint-disable */",
        "// 该文件由代码生成器自动维护，请勿手工编辑。",
        "// 注册/删除菜单后会自动重建；要移除某个生成页面，请先删除表配置。",
        "// 菜单分组由 scripts/village_menu_replan.py 按村务业务域规划（一级=系统，二级=业务域，三级=页面）。",
        "import Layout from '@/layout/index.vue'",
        "import { h } from 'vue'",
        "import { RouterView } from 'vue-router'",
        "const GroupView = { name: 'GroupView', render: () => h(RouterView) }",
        "",
        "export default [",
        indent(2) + "{",
        indent(4) + "path: '/%s'," % SYSTEM_PATH,
        indent(4) + "component: Layout,",
        indent(4) + "meta: { title: '%s', i18nKey: 'menu.%s' }," % (SYSTEM_TITLE, SYSTEM_PATH),
        indent(4) + "children: [",
        domains_text,
        indent(4) + "]",
        indent(2) + "}",
        "]",
        "",
    ])
    return text, len(assigned)


def gen_rules():
    """输出可沉淀到 GenMenuGroup.java 的村务规则（按表注释中文关键词）。"""
    out = []
    for code, label, tnames in DOMAINS:
        if not tnames:
            continue
        keywords = []
        for t in tnames:
            keywords.append(t)
        out.append('            new Rule(new Group("%s", "%s"), List.of(%s)),'
                   % (code, label, ", ".join('"%s"' % k for k in keywords)))
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default=DEFAULT_WEB, help="生成产物前端目录")
    ap.add_argument("--check", action="store_true", help="只校验不写文件")
    ap.add_argument("--rules", action="store_true", help="输出 GenMenuGroup 规则后退出")
    args = ap.parse_args()

    if args.rules:
        print(gen_rules())
        return 0

    tables = load_tables()
    t2d = build_table_to_domain()
    content, n = render(tables, t2d)
    target = os.path.join(args.target, ROUTES_REL)
    print("模板表数: %d, 已分配: %d, 业务域: %d" % (len(tables), n, len(DOMAINS)))
    print("输出目标:", target)

    if args.check:
        print("[check] 生成成功，未写文件。分组预览：")
        for code, label, tnames in DOMAINS:
            print("  %-10s %-6s %2d 页" % (code, label, len(tnames)))
        return 0

    if not os.path.isfile(target):
        print("目标文件不存在：" + target, file=sys.stderr)
        return 2
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("已重建 gen-routes.js（%d 字节）" % len(content.encode("utf-8")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
