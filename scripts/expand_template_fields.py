# -*- coding: utf-8 -*-
"""
V9.10 行业模板字段展开器
=====================================================================
背景：254 套行业模板共 2191 张表，原平均仅 6.12 个业务字段，823 张表
只有 5 列 —— 基于模板生成的 CRUD 页面偏空壳。审计字段
(id/create_time/update_time/create_by/update_by/deleted) 由 BaseEntity +
buildDdl() 统一管理，模板严禁重复，故本脚本只展开"业务字段"。

设计要点：
1. 幂等：按列名去重，重跑不会重复追加；可作为 CLI 原地展开，也被
   gen_industry_templates.py 在写出前调用（重跑生成脚本也不丢字段）。
2. 语义等价组：组内任一列已存在（如已有 total_amount）就跳过整组，
   避免补出 amount / total_amount 这类语义重复列。
3. 表语义驱动：按表名/列注释关键词补编码、单号、金额、联系电话、时间
   区间、内容摘要等，让字段贴合业务而非千表一面。
4. 类型合法：只用生成脚本既有的 MySQL 类型常量，宽度与项目白名单一致。

用法：
    python scripts/expand_template_fields.py            # 原地展开全部 JSON（含历史4套）
    python scripts/expand_template_fields.py --check    # 只统计不写入
生成脚本会自动 import expand_table() 并在写出每套前展开。
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(
    os.path.join(HERE, "..", "src", "main", "resources", "industry-templates")
)

# 与生成脚本一致的类型常量（保持白名单口径）
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

# BaseEntity / buildDdl 管理的审计列 —— 展开器绝不追加
AUDIT = frozenset(
    {"id", "create_time", "update_time", "create_by", "update_by", "deleted"}
)

LEGAL_TYPES = frozenset(
    {BIG, V64, V128, V255, V512, TXT, TINY, INT, DEC, DT, DATE,
     "varchar(20)", "varchar(32)", "varchar(50)", "decimal(5,2)", "decimal(10,2)"}
)


def _names(table):
    return {c["name"] for c in table.get("columns", [])}


def _append(table, name, typ, comment, names):
    """追加一列；列已存在或命中审计列则跳过。返回是否真的追加。"""
    if name in names or name in AUDIT:
        return False
    table.setdefault("columns", []).append(
        {"name": name, "type": typ, "comment": comment}
    )
    names.add(name)
    return True


def _has_status(names):
    return "status" in names or any(n.endswith("_status") for n in names)


def _has_remark(names):
    return any(k in n for n in names for k in ("remark", "memo", "note", "desc"))


def _has_sort(names):
    return any(n in ("sort", "seq") or n.endswith("_sort") or n.endswith("_order")
               for n in names)


def _has_code(names):
    return "code" in names or any(n.endswith("_code") or n.endswith("_no") for n in names)


def _has_amount(names):
    return any(k in n for n in names
               for k in ("amount", "price", "money", "fee", "cost", "salary", "total"))


def _has_phone(names):
    return any(k in n for n in names for k in ("phone", "mobile", "tel", "contact"))


def _has_range(names, kind):
    if kind == "start":
        return any(k in n for n in names for k in ("start", "begin", "valid_from", "effective"))
    return any(k in n for n in names for k in ("end", "expire", "valid_to", "finish"))


def _hit(table, words):
    """表名或表注释命中任一关键词。"""
    text = (table.get("tableName", "") + " " + table.get("tableComment", "")).lower()
    return any(w in text for w in words)


def _code_label(name_comment, prefix):
    """由 xxx_name 的中文注释派生编码列注释：患者姓名→患者编码；无中文则退回『业务编码』。"""
    if name_comment and any("\u4e00" <= ch <= "\u9fff" for ch in name_comment):
        for suffix in ("名称", "姓名", "名字", "标题"):
            if name_comment.endswith(suffix):
                return name_comment[:-len(suffix)] + "编码"
        if "名称" in name_comment:
            return name_comment.replace("名称", "编码")
        # 「患者」这类无后缀的短中文标签直接加编码
        if len(name_comment) <= 6:
            return name_comment + "编码"
    return "业务编码"


# 单号规则：按业务关键词映射出语义化单号列（命中第一条即止）
NO_RULES = (
    (("contract", "协议"), "contract_no", "合同编号"),
    (("invoice", "发票"), "invoice_no", "发票号"),
    (("voucher", "凭证"), "voucher_no", "凭证号"),
    (("apply", "申请"), "apply_no", "申请单号"),
    (("refund", "退款"), "refund_no", "退款单号"),
    (("payment", "支付"), "payment_no", "支付流水号"),
    (("bill", "账单", "单据"), "bill_no", "单据编号"),
    (("order", "订单"), "order_no", "订单号"),
    (("plan", "计划"), "plan_no", "计划编号"),
    (("task", "任务"), "task_no", "任务编号"),
)

TREE_WORDS = ("category", "dict", "config", "type", "channel", "menu",
              "area", "region", "dept", "组织", "分类", "字典", "菜单")
NO_WORDS = tuple(w for rule in NO_RULES for w in rule[0])
AMOUNT_WORDS = ("order", "payment", "contract", "finance", "refund", "invoice",
                "purchase", "sale", "price", "amount", "fee", "salary", "cost",
                "account", "订单", "支付", "金额", "费用", "工资", "采购", "销售")
PHONE_WORDS = ("customer", "user", "member", "student", "employee", "patient",
               "supplier", "client", "contact", "tenant", "driver", "owner",
               "staff", "teacher", "客户", "用户", "会员", "学生", "员工", "患者", "供应商", "联系人")
RANGE_WORDS = ("task", "plan", "schedule", "meeting", "event", "activity",
               "leave", "course", "exam", "lease", "campaign", "任务", "计划",
               "日程", "会议", "活动", "请假", "课程", "考试")
CONTENT_WORDS = ("article", "news", "content", "post", "notice", "announcement",
                 "document", "blog", "文章", "新闻", "公告", "通知")


def expand_table(table):
    """对单张表按语义补充业务字段（幂等）。返回本次追加的列数。"""
    cols = table.get("columns")
    if not cols:
        return 0
    before = len(cols)

    # 0) 审计列防冲突：业务列若误用了审计列名（如历史蓝图的 create_time=开方时间），
    #    改名为 biz_ 前缀；否则 buildDdl() 追加审计列时会重复列导致建表失败。
    for col in cols:
        if col["name"] in AUDIT and col["name"] != "id":
            col["name"] = "biz_" + col["name"]

    names = _names(table)

    # 1) 编码：存在 xxx_name 但缺对应 xxx_code → 补一个编码列（单表最多一个）
    #    注释优先取 name 列的中文含义（如「患者姓名」→「患者编码」），避免中英混排
    for col in list(cols):
        n = col["name"]
        if n == "name" or not n.endswith("_name"):
            continue
        prefix = n[:-5]
        if not prefix or _has_code(names):
            break
        label = _code_label(col.get("comment", ""), prefix)
        if _append(table, prefix + "_code", V64, label, names):
            break

    # 2) 单号：单据/订单类表补语义化单号
    if not _has_code(names) and _hit(table, NO_WORDS):
        for words, no_name, comment in NO_RULES:
            if _hit(table, words):
                if _append(table, no_name, V64, comment, names):
                    break

    # 3) 金额：资金/交易类表补金额
    if not _has_amount(names) and _hit(table, AMOUNT_WORDS):
        _append(table, "amount", DEC, "金额", names)

    # 4) 联系方式：人员/客户类表补联系电话
    if not _has_phone(names) and _hit(table, PHONE_WORDS):
        _append(table, "phone", V64, "联系电话", names)

    # 5) 时间区间：计划/任务/日程类表补起止时间
    if _hit(table, RANGE_WORDS):
        if not _has_range(names, "start"):
            _append(table, "start_time", DT, "开始时间", names)
        if not _has_range(names, "end"):
            _append(table, "end_time", DT, "结束时间", names)

    # 6) 内容正文：文章/公告类表补内容列
    if _hit(table, CONTENT_WORDS):
        _append(table, "content", TXT, "详细内容", names)

    # 7) 排序：树形/字典/配置类表补 sort
    if ("parent_id" in names or _hit(table, TREE_WORDS)) and not _has_sort(names):
        _append(table, "sort", INT, "排序", names)

    # 8) 状态：几乎所有业务表都需要（含 xxx_status 则跳过）
    if not _has_status(names):
        _append(table, "status", TINY, "状态（1启用 0停用）", names)

    # 9) 备注：通用列
    if not _has_remark(names):
        _append(table, "remark", V512, "备注", names)

    return len(cols) - before


def _dump_preserving_column_style(vo, path):
    """按文件原有列书写风格落盘：历史 4 套的列是单行紧凑格式，重生成 indent=2 会造成整文件噪声 diff。
    检测原文件是否存在单行列对象（形如 {"name": ...}），若有则用自定义 encoder 保持紧凑列。"""
    with open(path, encoding="utf-8") as f:
        original = f.read()
    compact = '{"name": "' in original and original.count('{"name": "') >= 3
    if not compact:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(vo, f, ensure_ascii=False, indent=2)
            f.write("\n")
        return

    # 紧凑模式：列对象单行，其余结构 indent=2。手工拼装以保持与历史文件一致的格式。
    lines = ["{"]
    lines.append('  "key": %s,' % json.dumps(vo["key"], ensure_ascii=False))
    lines.append('  "name": %s,' % json.dumps(vo["name"], ensure_ascii=False))
    if "desc" in vo:
        lines.append('  "desc": %s,' % json.dumps(vo["desc"], ensure_ascii=False))
    if "category" in vo:
        lines.append('  "category": %s,' % json.dumps(vo["category"], ensure_ascii=False))
    lines.append('  "modules": [')
    for mi, module in enumerate(vo.get("modules", [])):
        lines.append("    {")
        lines.append('      "name": %s,' % json.dumps(module["name"], ensure_ascii=False))
        lines.append('      "tables": [')
        for ti, table in enumerate(module.get("tables", [])):
            lines.append("        {")
            lines.append('          "tableName": %s,' % json.dumps(table["tableName"], ensure_ascii=False))
            lines.append('          "tableComment": %s,' % json.dumps(table["tableComment"], ensure_ascii=False))
            lines.append('          "columns": [')
            for ci, col in enumerate(table.get("columns", [])):
                comma = "," if ci < len(table["columns"]) - 1 else ""
                lines.append("            " + json.dumps(col, ensure_ascii=False) + comma)
            lines.append("          ]")
            lines.append("        }" + ("," if ti < len(module["tables"]) - 1 else ""))
        lines.append("      ]")
        lines.append("    }" + ("," if mi < len(vo.get("modules", [])) - 1 else ""))
    lines.append("  ]")
    lines.append("}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def expand_all(directory=OUT_DIR, check_only=False):
    files = sorted(glob.glob(os.path.join(directory, "*.json")))
    total_tables = total_added = 0
    for path in files:
        with open(path, encoding="utf-8") as f:
            vo = json.load(f)
        added_here = 0
        for module in vo.get("modules", []):
            for table in module.get("tables", []):
                total_tables += 1
                added_here += expand_table(table)
        total_added += added_here
        if added_here and not check_only:
            _dump_preserving_column_style(vo, path)
    return len(files), total_tables, total_added


def main():
    check = "--check" in sys.argv
    files, tables, added = expand_all(check_only=check)
    print("%s：%d 套 / %d 表，%s字段 %d 个" %
          ("检查" if check else "展开", files, tables,
           "可补" if check else "已补", added))


if __name__ == "__main__":
    main()
