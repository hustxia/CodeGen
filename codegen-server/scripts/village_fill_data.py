#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智慧村务：给空表补演示数据，让每个菜单的列表页都有内容可查。

设计：
- 只处理"当前 0 行"的表（幂等）；--force 则先清空再补。
- 列值按「列名语义 + 列类型」生成：外键 _id 引用关联表的真实主键、
  状态/标志给合理枚举、金额数量给数字、日期给近一年、编码给规则值、
  名称/标题用中文业务词 + 序号、备注给演示说明，审计列交给默认值。
- 相互关联：优先引用已有真实数据（resident/household/grid/parcel/asset...），
  保证点开详情、翻关联数据时不会出现悬空 id。

用法：
  python scripts/village_fill_data.py --preview          # 预览将补的表与行数
  python scripts/village_fill_data.py                     # 生成并执行 SQL
  python scripts/village_fill_data.py --out x.sql         # 只生成 SQL 不执行
  python scripts/village_fill_data.py --force             # 清空这些表再补
"""
import subprocess
import sys
import re
import random
import argparse
from datetime import date, timedelta
from collections import OrderedDict

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "village_affairs"
USER, PWD = "root", "root"

random.seed(20261117)

# 不补的系统表（登录/会话/日志等由运行时产生）
SKIP_TABLES = {"sys_session", "sys_login_log", "sys_oper_log", "operation_log",
               "audit_trail"}

# 外键列名 → 引用表（引用其真实 id），优先关联已有数据
FK_REF = {
    "resident_id": "village_resident", "household_id": "village_household",
    "village_id": "village_area", "grid_id": "village_grid",
    "parcel_id": "village_land_parcel", "asset_id": "village_asset",
    "contract_id": "village_contract", "approval_id": "approval",
    "apply_id": "village_service_apply", "task_id": "task",
    "member_id": "village_party_member", "org_id": "village_party_org",
    "archive_id": "village_archive", "dispute_id": "village_dispute",
    "disclosure_id": "village_disclosure", "meeting_id": "village_meeting",
    "motion_id": "village_motion", "vote_id": "village_vote",
    "metric_id": "village_dashboard_metric", "plan_id": "village_assess_plan",
    "item_id": "village_service_item", "tag_id": "tag",
    "user_id": "sys_user", "dept_id": "sys_role", "role_id": "sys_role",
    "assignee_id": "sys_user", "operator_id": "sys_user", "actor_id": "sys_user",
    "handler_id": "sys_user", "reviewer_id": "sys_user", "applicant_id": "sys_user",
    "parent_id": None,  # 自引用，特殊处理
}

# 列注释词库（用于给 name/title/content 等生成中文）
CN_WORDS = {
    "no": "编号", "code": "编码", "name": "名称", "title": "标题",
    "content": "内容", "summary": "摘要", "description": "说明", "remark": "备注",
    "result": "结果", "reason": "原因", "comment": "意见", "problem": "问题",
}

STATUS_COLS = {"status", "read_status", "handle_status", "party_status"}


def mysql(sql, silent=True):
    cmd = [MYSQL, "-u" + USER, "-p" + PWD, "-N", "--default-character-set=utf8mb4", "-e", sql]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0 and not silent:
        raise RuntimeError(r.stderr[:300])
    return r.stdout


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v).replace("\\", "\\\\").replace("'", "''")
    return "'" + s + "'"


def get_all_tables():
    out = mysql("SELECT table_name FROM information_schema.tables "
                "WHERE table_schema='%s' ORDER BY table_name" % DB)
    return [t.strip() for t in out.splitlines() if t.strip()]


def get_columns(table):
    out = mysql("SELECT column_name, column_type, column_key, extra, is_nullable "
                "FROM information_schema.columns WHERE table_schema='%s' "
                "AND table_name='%s' ORDER BY ordinal_position" % (DB, table))
    cols = []
    for line in out.splitlines():
        parts = line.split("\t")
        if len(parts) < 5:
            continue
        cols.append({"name": parts[0], "type": parts[1], "key": parts[2],
                     "extra": parts[3], "nullable": parts[4]})
    return cols


def get_count(table):
    out = mysql("SELECT COUNT(*) FROM `%s`.`%s`" % (DB, table))
    return int(out.strip().splitlines()[0]) if out.strip() else 0


def get_ids(table, limit=20):
    """取一张表真实存在的 id 列表，用于外键关联。"""
    if not table:
        return []
    out = mysql("SELECT id FROM `%s`.`%s` ORDER BY id LIMIT %d" % (DB, table, limit))
    return [int(x.strip()) for x in out.splitlines() if x.strip().isdigit()]


def pick_id(table, ids_cache, i, fallback_start=1):
    if table is None:
        return None
    if table not in ids_cache:
        ids_cache[table] = get_ids(table)
    ids = ids_cache[table]
    if ids:
        return ids[i % len(ids)]
    return fallback_start + (i % 5)


def gen_value(col, table_cn, i, row_i, ids_cache, all_cols):
    name = col["name"]
    typ = col["type"].lower()
    # 审计/自增列交给数据库默认，不显式插入
    if "auto_increment" in col["extra"].lower():
        return None, False
    if name in ("create_by", "update_by", "create_time", "update_time", "deleted", "del_flag"):
        return None, False

    # 主键但非自增：显式给一个稳定 id
    if col["key"] == "PRI":
        return 9000 + row_i, True

    # 外键：引用真实数据
    if name in FK_REF:
        ref = FK_REF[name]
        if name == "parent_id":
            return None, False  # 自引用留空，避免环
        return pick_id(ref, ids_cache, row_i), True

    # 状态类
    if name in STATUS_COLS:
        return (1 if row_i % 3 else 2), True
    if name in ("enabled", "is_required", "agree_flag", "is_default", "read_flag"):
        return row_i % 2, True
    if name.endswith("_flag") or name.startswith("is_"):
        return row_i % 2, True

    # 日期 / 时间（必须在 fee_month / count_date 这类"金额/数量+日期"复合名前判断，
    # 否则 fee_month 会被 fee 规则先命中生成小数，导致 Incorrect date value）
    # 仅当列类型确实是日期时间，或列名以 _date 结尾时才生成日期；
    # 像 service_time(int 服务时长) 这类"名字带 time 但类型是数值"的列不能给日期字符串。
    is_date_type = any(k in typ for k in ("date", "datetime", "timestamp"))
    looks_date = ("date" in name or name.endswith("_month") or name == "month"
                  or ("time" in name and is_date_type))
    if looks_date and (is_date_type or "date" in name or name.endswith("_month")):
        d = date.today() - timedelta(days=(row_i % 12) * 30)
        if "datetime" in typ or "timestamp" in typ:
            return d.strftime("%Y-%m-%d 09:30:00"), True
        if (name.endswith("_month") or name == "month") and "date" not in typ:
            return "%04d-%02d" % (d.year, d.month), True
        return d.strftime("%Y-%m-%d"), True

    # 金额 / 数量 / 比例 / 分数
    if any(k in name for k in ("amount", "money", "fund", "budget", "price", "fee", "capital")):
        return round(1000 + row_i * 732.5, 2), True
    if any(k in name for k in ("count", "qty", "quantity", "num", "population", "households",
                               "score", "area", "size", "value", "limit_days")):
        return 10 + row_i * 7, True
    if "phone" in name or "mobile" in name:
        return "138%08d" % (10000000 + row_i), True
    if "id_card" in name:
        return "3301%014d" % (19900101 + row_i), True

    # 编码 / 单号
    if name.endswith("_no") or name.endswith("_code") or name == "no":
        return "%s%04d" % (table_cn[:2] + "NO", row_i), True
    if name in ("ip", "ip_addr"):
        return "192.168.1.%d" % (10 + row_i), True

    # 文本：按列义生成中文
    if "varchar" in typ or "text" in typ:
        label = CN_WORDS.get(name, "")
        base = table_cn or "数据"
        if name in ("name", "title", "metric_name", "target_name", "item_name", "node_name", "group_name"):
            return "%s演示%s-%d" % (base, label or "记录", row_i), True
        if name in ("content", "description", "summary", "detail", "reason", "result", "comment", "remark"):
            return "%s%s：这是用于功能演示的示例数据第%d条，字段 %s。" % (base, label, row_i, name), True
        if name in ("type", "category", "level", "group_type"):
            return ["甲类", "乙类", "丙类"][row_i % 3], True
        if label:
            return "%s-%d" % (label, row_i), True
        return "%s-%d" % (base, row_i), True

    # tinyint/int 杂项
    if "tinyint" in typ or "int" in typ:
        return row_i % 5 + 1, True
    if "decimal" in typ or "double" in typ or "float" in typ:
        return round(100 + row_i * 13.5, 2), True

    return None, False


def build_inserts(table, cols, table_cn, n, ids_cache):
    statements = []
    for i in range(1, n + 1):
        ins_cols, ins_vals = [], []
        for col in cols:
            v, use = gen_value(col, table_cn, i, i, ids_cache, cols)
            if use:
                ins_cols.append(col["name"])
                ins_vals.append(q(v))
        sql = "INSERT INTO `%s`.`%s` (%s) VALUES (%s);" % (
            DB, table, ", ".join("`%s`" % c for c in ins_cols), ", ".join(ins_vals))
        statements.append(sql)
    return statements


def get_table_comment(table):
    out = mysql("SELECT table_comment FROM information_schema.tables "
                "WHERE table_schema='%s' AND table_name='%s'" % (DB, table))
    return out.strip().splitlines()[0] if out.strip() else table


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="只预览，不生成SQL")
    ap.add_argument("--out", default=None, help="输出 SQL 文件路径（不执行）")
    ap.add_argument("--force", action="store_true", help="先清空空表再补（这些表本就空，等价）")
    ap.add_argument("--rows", type=int, default=6, help="每张表补几行，默认6")
    args = ap.parse_args()

    tables = [t for t in get_all_tables() if t not in SKIP_TABLES]
    empty_tables = [t for t in tables if get_count(t) == 0]
    print("空表数：%d / 总业务表 %d" % (len(empty_tables), len(tables)))

    ids_cache = {}
    all_sql = ["SET FOREIGN_KEY_CHECKS=0;"]
    plan = []
    for t in empty_tables:
        cols = get_columns(t)
        comment = get_table_comment(t)
        stmts = build_inserts(t, cols, comment, args.rows, ids_cache)
        plan.append((t, comment, len(cols), len(stmts)))
        all_sql.extend(stmts)
    all_sql.append("SET FOREIGN_KEY_CHECKS=1;")

    print("\n将补数据的表（表 / 中文名 / 列数 / 行数）：")
    for t, c, nc, nr in plan:
        print("  %-34s %-12s %2d列 x%d行" % (t, c[:12], nc, nr))
    print("\n合计插入 %d 行，SQL %d 条" % (args.rows * len(plan), len(all_sql) - 2))

    if args.preview:
        print("（--preview 预览模式，未生成 SQL）")
        return

    sql_text = "\n".join(all_sql) + "\n"
    out_path = args.out
    if out_path:
        open(out_path, "w", encoding="utf-8", newline="\n").write(sql_text)
        print("已写出 SQL：", out_path)
        return

    # 直接执行：走临时文件 + mysql < file，避免命令行长度限制
    tmp = "scripts/_village_fill_tmp.sql"
    open(tmp, "w", encoding="utf-8", newline="\n").write(sql_text)
    cmd = [MYSQL, "-u" + USER, "-p" + PWD, "--default-character-set=utf8mb4", DB]
    with open(tmp, "r", encoding="utf-8") as f:
        r = subprocess.run(cmd, stdin=f, capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
    err = "\n".join(l for l in r.stderr.splitlines() if "Warning" not in l)
    if r.returncode != 0 or err.strip():
        print("执行有错误：", err[:1500])
    else:
        print("执行成功，%d 行演示数据已入库" % (args.rows * len(plan)))


if __name__ == "__main__":
    main()
