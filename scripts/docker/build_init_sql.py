#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 docs/sql/*.sql 按正确的版本顺序复制到 scripts/docker/init/NNN_*.sql，
供 MySQL 官方镜像的 docker-entrypoint-initdb.d 按文件名字典序依次执行。

为什么要这个脚本：
  docs/sql 的版本号是「数字序」（V6.9 < V6.10 < V9.2 < V9.15），
  而文件名字典序是 V6.10 < V6.9、V9.15 < V9.2，直接挂载 docs/sql 会乱序。
  本脚本把版本号按 (主版本, 次版本) 的整数元组排序后重命名为 001_xxx.sql 递增编号。

同版本号 tiebreak（如 V6.9__doc_menu 与 V6.9__sys_doc、两个 V9.2）：
  这些脚本互相独立且各自幂等（表 IF NOT EXISTS / 菜单先删后插），
  按文件名字典序固定先后即可，保证结果可重现。

幂等性：每次运行先清空 init/ 下旧的本脚本产物（匹配 NNN_*.sql），再全量重生成，
可反复执行不产生脏文件。

用法（仓库根目录执行）：
  python scripts/docker/build_init_sql.py
"""

import re
import shutil
import sys
from pathlib import Path

# 路径基准：脚本自身位于 <repo>/scripts/docker/ 下
REPO_ROOT = Path(__file__).resolve().parents[2]
SQL_DIR = REPO_ROOT / "docs" / "sql"
INIT_DIR = REPO_ROOT / "scripts" / "docker" / "init"

# 匹配 V1.0__init.sql / V6.10__monitor_menu.sql 等 Flyway 风格文件名
FILE_RE = re.compile(r"^V(\d+)\.(\d+)__([\w\-]+)\.sql$")


def version_key(path: Path):
    """按 (主版本, 次版本) 的整数元组排序；同版本号再按文件名稳定排序。"""
    m = FILE_RE.match(path.name)
    if not m:
        return None
    major, minor = int(m.group(1)), int(m.group(2))
    return (major, minor, path.name)


def main() -> int:
    if not SQL_DIR.is_dir():
        print(f"错误：找不到 {SQL_DIR}", file=sys.stderr)
        return 1

    sql_files = [p for p in SQL_DIR.glob("*.sql") if version_key(p)]
    skipped = [p.name for p in SQL_DIR.glob("*.sql") if not version_key(p)]
    sql_files.sort(key=version_key)

    if not sql_files:
        print(f"错误：{SQL_DIR} 下没有匹配 V*__*.sql 的脚本", file=sys.stderr)
        return 1

    INIT_DIR.mkdir(parents=True, exist_ok=True)

    # 幂等：先删除旧产物（只删本脚本命名的 NNN_*.sql，不动人工放置的其他文件）
    for old in INIT_DIR.glob("[0-9][0-9][0-9]_*.sql"):
        old.unlink()

    for idx, src in enumerate(sql_files, start=1):
        dest = INIT_DIR / f"{idx:03d}_{src.name}"
        shutil.copy2(src, dest)
        print(f"{dest.name}  <-  docs/sql/{src.name}")

    if skipped:
        print("\n未识别的文件（已跳过）：" + ", ".join(sorted(skipped)), file=sys.stderr)

    print(f"\n共生成 {len(sql_files)} 个脚本到 {INIT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
