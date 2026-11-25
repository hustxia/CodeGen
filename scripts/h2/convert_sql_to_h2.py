# -*- coding: utf-8 -*-
"""
把 docs/sql/*.sql（MySQL 8 语法）转换为 H2 MODE=MySQL 可执行版本，
输出到 src/main/resources/db/h2/，供 application-h2.yml 的 spring.sql.init 加载。

设计要点（与 docs/sql 语法侦查结论一一对应）：
1. 版本排序按「数字版本号」而非文件名字典序：V9.15 > V9.2（字典序陷阱），
   同版本两个文件（V6.9 / V9.2）按文件名稳定排序，脚本本身幂等可任意顺序。
2. 直接丢弃（demo 空库场景无意义或有 H2 不支持的语义）：
   - SET NAMES / SET FOREIGN_KEY_CHECKS / USE / CREATE DATABASE / /*!...*/ 条件注释
   - information_schema 条件 DDL 全套（SET @x := ... / PREPARE / EXECUTE / DEALLOCATE /
     CALL / DROP PROCEDURE / CREATE PROCEDURE）——改写为 H2 原生 IF NOT EXISTS DDL
   - ALTER TABLE ... CONVERT TO CHARACTER SET（字符集统一，H2 无此语义）
   - UPDATE ... INNER JOIN（MySQL 多表 UPDATE，H2 不支持；demo 空库无数据可迁移）
   - 纯 SELECT 诊断语句（部分用到 information_schema，H2 视图结构不同）
3. 改写：
   - 引号内的 ALTER TABLE t ADD COLUMN ...  → ALTER TABLE t ADD COLUMN IF NOT EXISTS ...
     （去 AFTER 列、去 COLLATE，'' 还原为 '）
   - 引号内的 ALTER TABLE t ADD INDEX i (cols) → CREATE INDEX IF NOT EXISTS i ON t (cols)
   - CREATE TABLE：去 ENGINE/CHARSET/COLLATE 表选项；UNIQUE KEY → CONSTRAINT ... UNIQUE；
     KEY idx (cols) → CREATE INDEX IF NOT EXISTS；列级 COMMENT 与表注释 → COMMENT ON 语句；
     表选项 AUTO_INCREMENT=n → ALTER TABLE t ALTER COLUMN id RESTART WITH n
     （H2 2.x 显式插入主键后也会自动推进序列，此处为双保险）
   - 存储过程 V4.1：游标 + UNION ALL 列名白名单 + CONCAT 动态 DDL → 按白名单展开为
     逐列 ADD COLUMN IF NOT EXISTS
   - 存储过程 V4.2：body 里的普通 ALTER TABLE ADD COLUMN 直接提取改写
4. 保留不动（H2 MODE=MySQL 官方文档明确支持）：
   - INSERT ... ON DUPLICATE KEY UPDATE（含 VALUES(col) 引用新值）
   - INSERT IGNORE（未与 ON DUPLICATE 同用时用于跳过重复键）
   - 列级 AUTO_INCREMENT、backtick 标识符、mediumtext/longtext、'' 引号转义

幂等：每次运行重建输出目录（先清空再生成），可反复执行。

用法：
    python scripts/h2/convert_sql_to_h2.py
"""

import re
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SRC_DIR = REPO_ROOT / "docs" / "sql"
DST_DIR = REPO_ROOT / "src" / "main" / "resources" / "db" / "h2"

SEED_FILE = REPO_ROOT / "scripts" / "h2" / "999_demo_seed.sql"


# ------------------------------------------------------------
# 语句切分：感知单引号字符串与 -- 行注释
# ------------------------------------------------------------
def split_statements(sql: str):
    """按分号切分语句，跳过字符串字面量内的分号；去掉 -- 行注释与 /*!...*/ 条件注释。"""
    # 先去掉 /*!NNNNN ... */ 形式的 MySQL 条件注释（含其中分号）
    sql = re.sub(r"/\*!.*?\*/", " ", sql, flags=re.S)
    stmts, buf, i, n = [], [], 0, len(sql)
    in_str = False
    while i < n:
        ch = sql[i]
        if in_str:
            buf.append(ch)
            if ch == "'":
                # '' 转义
                if i + 1 < n and sql[i + 1] == "'":
                    buf.append("'")
                    i += 1
                else:
                    in_str = False
            i += 1
            continue
        if ch == "'":
            in_str = True
            buf.append(ch)
            i += 1
            continue
        if ch == "-" and sql[i:i + 2] == "--":
            # 行注释：跳到行尾
            j = sql.find("\n", i)
            i = n if j < 0 else j
            continue
        if ch == "#":
            j = sql.find("\n", i)
            i = n if j < 0 else j
            continue
        if ch == ";":
            stmt = "".join(buf).strip()
            if stmt:
                stmts.append(stmt)
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        stmts.append(tail)
    return stmts


# ------------------------------------------------------------
# 文本清洗工具
# ------------------------------------------------------------
def unquote_mysql_string(s: str) -> str:
    """把 MySQL 字符串字面量还原为原始文本（'' -> '）。s 不含外层引号。"""
    return s.replace("''", "'")


def extract_quoted_strings(stmt: str):
    """提取语句里的所有单引号字符串内容（已还原转义）。"""
    out = []
    i, n = 0, len(stmt)
    while i < n:
        if stmt[i] == "'":
            j = i + 1
            buf = []
            while j < n:
                if stmt[j] == "'":
                    if j + 1 < n and stmt[j + 1] == "'":
                        buf.append("'")
                        j += 2
                        continue
                    break
                buf.append(stmt[j])
                j += 1
            out.append("".join(buf))
            i = j + 1
        else:
            i += 1
    return out


def clean_column_def(defn: str):
    """清洗一段列定义：去 COMMENT / COLLATE / AFTER / CHARACTER SET。
    返回 (清洗后的定义, 列注释 or None)。"""
    comment = None
    m = re.search(r"\bCOMMENT\s+'((?:[^']|'')*)'", defn, flags=re.I)
    if m:
        comment = unquote_mysql_string(m.group(1))
        defn = defn[: m.start()] + defn[m.end():]
    defn = re.sub(r"\bCOLLATE\s+[A-Za-z0-9_]+", "", defn, flags=re.I)
    defn = re.sub(r"\bCHARACTER\s+SET\s+[A-Za-z0-9_]+", "", defn, flags=re.I)
    defn = re.sub(r"\bAFTER\s+`?[A-Za-z0-9_]+`?", "", defn, flags=re.I)
    return re.sub(r"\s+", " ", defn).strip().strip(",").strip(), comment


def quote_ident(name: str) -> str:
    name = name.strip("` ")
    return '`%s`' % name


# ------------------------------------------------------------
# CREATE TABLE 改写
# ------------------------------------------------------------
def transform_create_table(stmt: str, notes: list):
    """返回 (改写后的语句列表)。处理列定义、约束、表选项、注释与序列重置。"""
    m = re.match(
        r"CREATE\s+TABLE\s+(IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?\s*\(", stmt, flags=re.I | re.S)
    if not m:
        notes.append("  [跳过改写] 无法解析的 CREATE TABLE")
        return [stmt]
    table = m.group(2)
    # 找最外层括号 body
    open_idx = stmt.index("(", m.end() - 1)
    # 从后往前找表选项：匹配最后一个 ")\s*ENGINE" 或裸 ")" 后的选项串
    opt_match = re.search(r"\)\s*(ENGINE\b.*|DEFAULT\s+CHARSET.*|COMMENT\s*=.*)?$", stmt, flags=re.I | re.S)
    close_idx = stmt.rindex(")")
    body = stmt[open_idx + 1: close_idx]
    tail = stmt[close_idx + 1:] if opt_match else ""
    tail = tail.rstrip("; \t\r\n")

    # 拆列/约束（顶层逗号）
    items, buf, depth, in_str = [], [], 0, False
    i = 0
    while i < len(body):
        ch = body[i]
        if in_str:
            buf.append(ch)
            if ch == "'":
                if i + 1 < len(body) and body[i + 1] == "'":
                    buf.append("'")
                    i += 1
                else:
                    in_str = False
        elif ch == "'":
            in_str = True
            buf.append(ch)
        elif ch == "(":
            depth += 1
            buf.append(ch)
        elif ch == ")":
            depth -= 1
            buf.append(ch)
        elif ch == "," and depth == 0:
            items.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
        i += 1
    if buf and "".join(buf).strip():
        items.append("".join(buf).strip())

    out_cols, out_cons, indexes, col_comments = [], [], [], []
    auto_col = None
    for it in items:
        head = re.match(r"^`?([A-Za-z0-9_]+)`?", it)
        m_first = re.match(r"^[A-Za-z ]+", it, flags=re.I)
        first = m_first.group(0).strip().upper() if m_first else ""
        kw = first.split()[0] if first else ""
        if kw in ("PRIMARY",):
            out_cons.append(re.sub(r"\s+", " ", it).strip())
            continue
        if kw == "CONSTRAINT":
            # 保留（UNIQUE/PRIMARY 约束）；清洗内嵌 COLLATE
            it2 = re.sub(r"\bCOLLATE\s+[A-Za-z0-9_]+", "", it, flags=re.I)
            out_cons.append(re.sub(r"\s+", " ", it2).strip())
            continue
        if kw == "UNIQUE":
            # UNIQUE KEY uk_x (a,b) -> CONSTRAINT uk_x UNIQUE (a,b)
            m2 = re.match(r"UNIQUE\s+(?:KEY|INDEX)\s+`?([A-Za-z0-9_]+)`?\s*\((.*)\)\s*$", it, flags=re.I | re.S)
            if m2:
                cols = re.sub(r"\s+", " ", m2.group(2)).strip()
                out_cons.append("CONSTRAINT %s UNIQUE (%s)" % (quote_ident(m2.group(1)), cols))
            else:
                out_cons.append(re.sub(r"\s+", " ", it).strip())
            continue
        if kw == "KEY" or kw == "INDEX":
            m2 = re.match(r"(?:KEY|INDEX)\s+`?([A-Za-z0-9_]+)`?\s*\((.*)\)\s*$", it, flags=re.I | re.S)
            if m2:
                indexes.append((m2.group(1), re.sub(r"\s+", " ", m2.group(2)).strip()))
            continue
        # 普通列定义
        if head is None:
            out_cols.append(re.sub(r"\s+", " ", it).strip())
            continue
        col_name = head.group(1)
        rest = it[head.end():]
        if re.search(r"\bAUTO_INCREMENT\b", rest, flags=re.I):
            auto_col = col_name
            rest = re.sub(r"\bCOMMENT\s+'(?:[^']|'')*'", "", rest, flags=re.I)
            out_cols.append((quote_ident(col_name) + " " + re.sub(r"\s+", " ", rest).strip()).strip())
            continue
        cleaned, cmt = clean_column_def(it)
        out_cols.append(cleaned)
        if cmt:
            col_comments.append((col_name, cmt.replace("'", "''")))

    # 表选项
    restart, table_comment = None, None
    if tail:
        m3 = re.search(r"AUTO_INCREMENT\s*=\s*(\d+)", tail, flags=re.I)
        if m3:
            restart = int(m3.group(1))
        m4 = re.search(r"COMMENT\s*=\s*'((?:[^']|'')*)'", tail, flags=re.I)
        if m4:
            table_comment = unquote_mysql_string(m4.group(1))

    parts = ["CREATE TABLE IF NOT EXISTS %s (" % quote_ident(table)]
    all_lines = out_cols + out_cons
    for k, line in enumerate(all_lines):
        sep = "," if k < len(all_lines) - 1 else ""
        parts.append("  %s%s" % (line, sep))
    parts.append(");")
    stmts = ["\n".join(parts)]

    for idx_name, cols in indexes:
        stmts.append("CREATE INDEX IF NOT EXISTS %s ON %s (%s);" % (quote_ident(idx_name), quote_ident(table), cols))
    if table_comment:
        stmts.append("COMMENT ON TABLE %s IS '%s';" % (quote_ident(table), table_comment))
    for col, cmt in col_comments:
        stmts.append("COMMENT ON COLUMN %s.%s IS '%s';" % (quote_ident(table), quote_ident(col), cmt))
    if restart and auto_col:
        stmts.append("ALTER TABLE %s ALTER COLUMN %s RESTART WITH %d;"
                     % (quote_ident(table), quote_ident(auto_col), restart))
        notes.append("  [序列] %s.%s RESTART WITH %d" % (table, auto_col, restart))
    return stmts


# ------------------------------------------------------------
# PREPARE 条件 DDL 改写：从 SET @x := ... / IF(...) 的字符串里提取 DDL
# ------------------------------------------------------------
def transform_conditional_ddl(stmt: str, notes: list):
    """从 MySQL information_schema + PREPARE 模式里提取真正的 DDL。
    返回改写语句列表；无法识别时返回空列表（丢弃并记录）。"""
    strings = extract_quoted_strings(stmt)
    add_col, add_idx = [], []
    for s in strings:
        s2 = s.strip()
        m = re.match(r"ALTER\s+TABLE\s+`?([A-Za-z0-9_]+)`?\s+ADD\s+COLUMN\s+(.*)$", s2, flags=re.I | re.S)
        if m:
            defn, cmt = clean_column_def(m.group(2))
            add_col.append((m.group(1), defn, cmt))
            continue
        m = re.match(r"ALTER\s+TABLE\s+`?([A-Za-z0-9_]+)`?\s+ADD\s+(?:UNIQUE\s+)?(?:INDEX|KEY)\s+`?([A-Za-z0-9_]+)`?\s*\((.*)\)\s*$",
                     s2, flags=re.I | re.S)
        if m:
            add_idx.append((m.group(1), m.group(2), re.sub(r"\s+", " ", m.group(3)).strip()))
    out = []
    for table, defn, cmt in add_col:
        # 列定义若带 AUTO_INCREMENT 关键字则直接透传
        out.append("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s;" % (quote_ident(table), defn))
        if cmt:
            col_name = re.match(r"`?([A-Za-z0-9_]+)`?", defn).group(1)
            out.append("COMMENT ON COLUMN %s.%s IS '%s';" % (quote_ident(table), quote_ident(col_name), cmt.replace("'", "''")))
        notes.append("  [条件DDL] ADD COLUMN IF NOT EXISTS %s %s" % (table, defn.split()[0]))
    for table, idx, cols in add_idx:
        out.append("CREATE INDEX IF NOT EXISTS %s ON %s (%s);" % (quote_ident(idx), quote_ident(table), cols))
        notes.append("  [条件DDL] CREATE INDEX IF NOT EXISTS %s" % idx)
    return out


# ------------------------------------------------------------
# 存储过程改写（V4.1 游标 / V4.2 IF NOT EXISTS 块）
# ------------------------------------------------------------
def transform_procedure(body: str, notes: list):
    out = []
    strings = extract_quoted_strings(body)
    # V4.1 模式：UNION ALL SELECT '列名' 白名单 + CONCAT('ALTER TABLE x ADD COLUMN `', col_name, '` <def>')
    literals = re.findall(
        r"SELECT\s+'((?:[^']|'')+?)'\s*(?:AS\s+\w+)?(?=\s+UNION\s+ALL|\s*\))", body, flags=re.I)
    if literals:
        for idx, s in enumerate(strings):
            m = re.match(r"ALTER\s+TABLE\s+`?([A-Za-z0-9_]+)`?\s+ADD\s+COLUMN\s+`?\s*$",
                         s.strip(), flags=re.I | re.S)
            if m:
                table = m.group(1)
                def_tpl = strings[idx + 1].strip() if idx + 1 < len(strings) else ""
                defn, cmt = clean_column_def(def_tpl)
                defn = defn.lstrip("` ").strip()  # CONCAT 模板里列名后的分隔反引号
                if defn:
                    for lit in literals:
                        col = unquote_mysql_string(lit)
                        out.append("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s;"
                                   % (quote_ident(table), quote_ident(col), defn))
                        if cmt:
                            out.append("COMMENT ON COLUMN %s.%s IS '%s';"
                                       % (quote_ident(table), quote_ident(col), cmt.replace("'", "''")))
                    notes.append("  [存储过程] 按 %d 列白名单展开 ADD COLUMN IF NOT EXISTS -> %s" % (len(literals), table))
                break
    if out:
        return out
    # 通用模式（V4.2）：body 里普通的 ALTER TABLE ... ADD COLUMN 直接提取改写
    for m in re.finditer(r"ALTER\s+TABLE\s+`?([A-Za-z0-9_]+)`?\s+ADD\s+COLUMN\s+(.*?)(?:;|$)", body,
                         flags=re.I | re.S):
        defn, cmt = clean_column_def(m.group(2))
        table = m.group(1)
        out.append("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s;" % (quote_ident(table), defn))
        if cmt:
            col_name = re.match(r"`?([A-Za-z0-9_]+)`?", defn).group(1)
            out.append("COMMENT ON COLUMN %s.%s IS '%s';" % (quote_ident(table), quote_ident(col_name), cmt.replace("'", "''")))
        notes.append("  [存储过程] 提取 ADD COLUMN -> %s %s" % (table, defn.split()[0]))
    return out


# ------------------------------------------------------------
# 单条语句路由
# ------------------------------------------------------------
DROP_PREFIX = re.compile(
    r"^(SET\s+(NAMES|FOREIGN_KEY_CHECKS|SQL_MODE|character)|USE\b|CREATE\s+DATABASE|"
    r"DROP\s+PROCEDURE|CALL\b|PREPARE\b|EXECUTE\b|DEALLOCATE\b|LOCK\s+TABLES|UNLOCK\s+TABLES|SHOW\b)",
    re.I)


def convert_script(path: Path):
    sql = path.read_text(encoding="utf-8", errors="replace")
    # 去掉 DELIMITER 行（mysql 客户端指令，非 SQL）
    sql = re.sub(r"(?mi)^\s*DELIMITER\s+.*$", "", sql)
    raw_stmts = split_statements(sql)
    out, notes = [], []

    # 先把 DELIMITER 包裹的存储过程整块摘出来（CREATE PROCEDURE ... END$$）
    # 用 (过程名, 过程体) 存：CALL <name> 处必须就地展开，否则展开的 ADD COLUMN
    # 会排到后面的 UPDATE 之后，导致 "Column not found"（V4.1 踩坑）。
    proc_blocks = []  # list[(name, body)]
    filtered = []
    i = 0
    while i < len(raw_stmts):
        s = raw_stmts[i]
        if re.match(r"^CREATE\s+PROCEDURE\b", s, flags=re.I):
            # 存储过程体可能因 DELIMITER 分割错误而断成多条；合并到 END$$ 或最后一个 END。
            # 注意：END$$ 后可能紧跟 CALL（同一条 raw stmt 内），必须切出来放回队列，
            # 否则 CALL 被吞进过程体，展开的 ADD COLUMN 排到 UPDATE 之后 → Column not found。
            block = s
            tail = ""
            end_m = re.search(r"\bEND\s*\$\$([\s\S]*)$", block, flags=re.I)
            if end_m:
                tail = end_m.group(1).strip()
                block = block[:end_m.start()]
            else:
                while not re.search(r"\bEND\s*$", block, flags=re.I) and i + 1 < len(raw_stmts):
                    i += 1
                    nxt = raw_stmts[i]
                    end_m = re.search(r"\bEND\s*\$\$([\s\S]*)$", nxt, flags=re.I)
                    if end_m:
                        block += ";\n" + nxt[:end_m.start()]
                        tail = end_m.group(1).strip()
                        break
                    block += ";\n" + nxt
            block = re.sub(r"\bEND\s*$", "", block, flags=re.I).strip()
            name_m = re.match(r"CREATE\s+PROCEDURE\s+`?([A-Za-z0-9_]+)`?", block, flags=re.I)
            proc_blocks.append((name_m.group(1) if name_m else "", block))
            if tail:
                # tail 可能含 CALL 与 DROP 等多条：插到当前位置之后，下一轮循环自然读到，
                # 保证 CALL 在主循环被识别、在调用点就地展开（不能 i 跳过多条）。
                parts = [p.strip() for p in tail.split(";") if p.strip()]
                raw_stmts[i + 1:i + 1] = parts
            i += 1
            continue
        filtered.append(s)
        i += 1

    called = set()
    for stmt in filtered:
        # 归一化首词
        lead = re.match(r"^\s*(--.*\n\s*)*([A-Za-z`]+)", stmt)
        kw = lead.group(2).strip("`").upper() if lead else ""
        upper = stmt.upper()

        # CALL <proc>：在调用点就地展开过程体（ADD COLUMN 必须先于后续 UPDATE）
        call_m = re.match(r"^\s*CALL\s+`?([A-Za-z0-9_]+)`?", stmt, flags=re.I)
        if call_m:
            proc_name = call_m.group(1)
            called.add(proc_name.lower())
            expanded = False
            for pname, pbody in proc_blocks:
                if pname.lower() == proc_name.lower():
                    out.extend(transform_procedure(pbody, notes))
                    expanded = True
                    break
            if not expanded:
                notes.append("  [丢弃] CALL 未找到过程体: %s" % proc_name)
            continue

        if DROP_PREFIX.match(stmt):
            continue
        if re.match(r"^CREATE\s+PROCEDURE\b", stmt, flags=re.I):
            continue  # 已走 proc_blocks
        if "INFORMATION_SCHEMA" in upper:
            before = len(out)
            out.extend(transform_conditional_ddl(stmt, notes))
            if len(out) == before:
                notes.append("  [丢弃] information_schema 条件语句/诊断 SELECT")
            continue
        if kw == "SELECT":
            notes.append("  [丢弃] 纯 SELECT 诊断语句")
            continue
        if kw == "ALTER":
            if re.search(r"CONVERT\s+TO\s+CHARACTER\s+SET", stmt, flags=re.I):
                notes.append("  [丢弃] CONVERT TO CHARACTER SET（字符集统一，demo 不需要）")
                continue
            notes.append("  [透传] ALTER 语句")
            out.append(stmt + ";")
            continue
        if kw == "UPDATE":
            if re.search(r"\b(INNER\s+JOIN|LEFT\s+JOIN|RIGHT\s+JOIN)\b", stmt, flags=re.I):
                notes.append("  [丢弃] 多表 UPDATE ... JOIN（H2 不支持；demo 空库无存量数据）")
                continue
            out.append(stmt + ";")
            continue
        if kw == "INSERT":
            s2 = re.sub(r"INSERT\s+IGNORE\s+INTO", "INSERT IGNORE INTO", stmt, flags=re.I)
            out.append(s2 + ";")  # INSERT IGNORE 在 H2 MODE=MySQL 下支持，保持原样
            continue
        if kw == "DELETE" or kw == "CREATE" or kw == "DROP":
            if kw == "CREATE" and re.match(r"CREATE\s+TABLE\b", stmt, flags=re.I):
                out.extend(transform_create_table(stmt, notes))
                continue
            if kw == "DROP":
                notes.append("  [丢弃] DROP 语句（内存库每次全新建）")
                continue
            out.append(stmt + ";")
            continue
        # 其余（如 SET @x := 的变体）按条件 DDL 提取尝试
        res = transform_conditional_ddl(stmt, notes)
        if res:
            out.extend(res)
        else:
            notes.append("  [丢弃] 未识别语句: %s..." % re.sub(r"\s+", " ", stmt)[:60])

    # 兜底：未被 CALL 的孤立过程才展开到末尾（正常脚本过程都在 CALL 处就地展开）
    for pname, pbody in proc_blocks:
        if pname.lower() not in called:
            out.extend(transform_procedure(pbody, notes))

    return out, notes


# ------------------------------------------------------------
# 排序：数字版本号（V9.15 > V9.2），同版本按文件名
# ------------------------------------------------------------
def version_key(path: Path):
    m = re.match(r"V(\d+)\.(\d+)__(.+)\.sql", path.name)
    if not m:
        return (0, 0, path.name)
    return (int(m.group(1)), int(m.group(2)), path.name)


def main():
    files = sorted(SRC_DIR.glob("V*.sql"), key=version_key)
    if DST_DIR.exists():
        shutil.rmtree(DST_DIR)
    DST_DIR.mkdir(parents=True)
    total = 0
    for f in files:
        stmts, notes = convert_script(f)
        # 每条 note 必须带 `-- ` 前缀：spring.sql.init 按分号切分脚本，
        # 裸文本（如 "  [丢弃] DROP 语句"）会被当成 SQL 交给 H2 执行，直接语法错（V9.19 踩坑）。
        commented_notes = [("-- " + n.lstrip()) if not n.lstrip().startswith("--") else n for n in notes]
        # 全被丢弃的脚本（如 V6.0 只改 information_schema 列注释）不能生成空文件：
        # spring.sql.init 对空脚本抛 IllegalArgumentException: 'script' must not be null or empty。
        if not stmts:
            stmts = ["SELECT 1; -- H2 无对应语义，原脚本全部丢弃，留无害占位保证脚本可执行"]
            commented_notes.append("-- [空脚本] 原脚本在 H2 下无可执行语句，注入 SELECT 1 占位")
        header = [
            "-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成",
            "-- 原始脚本: docs/sql/%s" % f.name,
            "-- 转换记录:",
        ] + commented_notes
        content = "\n".join(header) + "\n\n" + "\n\n".join(stmts) + "\n"
        (DST_DIR / f.name).write_text(content, encoding="utf-8")
        total += len(stmts)
        print("%-40s -> %3d 条语句" % (f.name, len(stmts)))
        for n in notes[1:]:
            print("    " + n)

    # demo 种子（非登录所需：登录种子在 V1.0；这里只补演示字典）
    if SEED_FILE.exists():
        seed = SEED_FILE.read_text(encoding="utf-8")
        out_path = DST_DIR / "999_demo_seed.sql"
        out_path.write_text(
            "-- 来源: scripts/h2/999_demo_seed.sql（演示字典种子，登录种子在 V1.0__init.sql）\n\n" + seed,
            encoding="utf-8")
        print("%-40s -> 复制" % out_path.name)
        files.append(out_path)

    print("\n完成：%d 个脚本，共 %d 条语句 -> %s" % (len(files), total, DST_DIR))


if __name__ == "__main__":
    main()
