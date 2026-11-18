#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 village_affairs.json 的最新中文列注释同步到已生成产物页面。

背景：改了模板词库（xxx_id → 中文管理字段名）后，已生成的前端页面仍是旧注释
（archiveID / householdID 这类英文 token）。重跑整条流水线成本高，本脚本直接
按「javaField/ID token → comment」映射替换产物 index.vue 的三处 label：
  - <el-table-column ... label="xxxID">
  - <el-form-item ... label="xxxID">
  - <div class="detail-row"><span class="detail-label">xxxID</span>

用法：
  python scripts/village_sync_comments.py            # 写回产物
  python scripts/village_sync_comments.py --check    # 只统计，不写
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


def camel(name):
    parts = name.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def build_map():
    vo = json.load(open(TEMPLATE, encoding="utf-8"))
    mapping = {}
    for module in vo["modules"]:
        for table in module["tables"]:
            for col in table["columns"]:
                name, comment = col["name"], col["comment"]
                # 产物 label 的三种可能形态：驼峰 javaField / xxxID / 原名
                keys = {name, camel(name)}
                if name.endswith("_id"):
                    keys.add(camel(name[:-3]) + "ID")
                    keys.add(camel(name[:-3]) + "Id")
                for key in keys:
                    # 只登记"注释是中文、而 token 是英文"的映射，避免误改本就中文的 label
                    if re.search(r"[A-Za-z]", key) and not re.search(r"[A-Za-z]", comment):
                        mapping[key] = comment
    return mapping


def sync(web_dir, mapping, check_only=False):
    views = os.path.join(web_dir, "src", "views")
    files = []
    for root, _dirs, names in os.walk(views):
        for n in names:
            if n == "index.vue":
                files.append(os.path.join(root, n))
    changed_files = 0
    changed_labels = 0
    samples = []
    pattern = re.compile(r'(label="|detail-label">)([A-Za-z][A-Za-z0-9]*ID)(?="|<)')

    def repl(m):
        nonlocal changed_labels
        token = m.group(2)
        if token in mapping:
            changed_labels += 1
            if len(samples) < 8:
                samples.append("%s → %s" % (token, mapping[token]))
            return m.group(1) + mapping[token]
        return m.group(0)

    for path in files:
        src = open(path, encoding="utf-8").read()
        new = pattern.sub(repl, src)
        if new != src:
            changed_files += 1
            if not check_only:
                open(path, "w", encoding="utf-8", newline="\n").write(new)
    return changed_files, changed_labels, samples, len(files)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--web", default=DEFAULT_WEB)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    mapping = build_map()
    files, labels, samples, total = sync(args.web, mapping, args.check)
    mode = "检查" if args.check else "已同步"
    print("%s：%d/%d 个页面，替换 %d 处英文 label" % (mode, files, total, labels))
    for s in samples:
        print("   ", s)
    if args.check and labels:
        print("（--check 模式，未写文件）")


if __name__ == "__main__":
    main()
