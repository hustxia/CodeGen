#!/usr/bin/env bash
# ============================================================================
# verify.sh —— V7.4 建立 / V9.2 补齐的唯一门禁入口（本地与 CI 跑同一份命令）
#
# 为什么要有它：2026-11-07 之前的验证靠人记命令，结果「240 例测试全绿」
# 不代表交付脚本能从空库跑起来（schema-test.sql 只有 7 张表），
# 「模板渲染不抛异常」不代表产物是合法 JS（192 种组合生成坏 JS 曾漏到线上）。
#
# V9.2 变更（对齐验证中心）：
#   - MIN_TESTS 240 → 312（V9.1 实际已 312，旧阈值少跑 72 例也不会失败）
#   - 新增 i18n 快照契约（S2）与 206 语言键覆盖（S4）两个 P0 步骤，
#     直接复用 scripts/testcenter-suites.mjs，与控制台「验证中心」同一入口
#   - 日志不再写 /tmp（Windows 不通用、重启即丢），改落 logs/testcenter/
#   - 每步写 steps.tsv，末尾由 testcenter-summary.mjs 产出
#     codegen.testcenter/v1 JSON 摘要，与执行器落库的摘要字段级一致
#
# 阶段（默认全跑，可用参数裁剪）：
#   1. mvn clean test                 —— 单测/契约测试/组合矩阵
#   2. 测试数阈值                     —— 按 <testcase> 节点数统计，0 例也算失败
#   3. i18n 快照契约 9 模式           —— node scripts/testcenter-suites.mjs S2
#   4. 206 语言键覆盖                 —— node scripts/testcenter-suites.mjs S4
#   5. 生成物 JS 语法校验             —— node scripts/check-generated-js.mjs
#   6. 前端构建                       —— codegen-web npm run build（--skip-web 跳过）
#   7. 空库冒烟                       —— 需 MySQL（--with-db 打开）
#
# 用法：
#   ./scripts/verify.sh                 # 本机全量（无 MySQL 时自动跳过冒烟）
#   ./scripts/verify.sh --with-db       # 额外跑空库冒烟
#   ./scripts/verify.sh --skip-web      # 只验后端
#   MIN_TESTS=312 MVN_BIN=/d/tool/apache-maven-3.6.3/bin/mvn ./scripts/verify.sh
# ============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MIN_TESTS="${MIN_TESTS:-312}"
MVN_BIN="${MVN_BIN:-mvn}"
JAVA_HOME="${JAVA_HOME:-}"
LOG_DIR="${TC_LOG_DIR:-logs/testcenter}"
WITH_DB=0
SKIP_WEB=0
SKIP_SMOKE=0

for arg in "$@"; do
  case "$arg" in
    --with-db)  WITH_DB=1 ;;
    --skip-web) SKIP_WEB=1 ;;
    --skip-smoke) SKIP_SMOKE=1 ;;
    -h|--help) sed -n '2,34p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$LOG_DIR"
STEPS="$LOG_DIR/steps.tsv"
SUMMARY="$LOG_DIR/summary.json"
: > "$STEPS"

PASS=0
FAIL=0
FAILED_STEPS=()

step()  { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok()    { PASS=$((PASS+1)); printf '\033[32mPASS\033[0m  %s\n' "$1"; }
bad()   { FAIL=$((FAIL+1)); FAILED_STEPS+=("$1"); printf '\033[31mFAIL\033[0m  %s\n' "$1"; }
note()  { printf '\033[36mINFO\033[0m  %s\n' "$1"; }

# 机器可读摘要的一行：suiteId / 名称 / PASS|FAIL|SKIP / 耗时ms / 日志 / 指标JSON
record() {
  local suite="$1" name="$2" status="$3" ms="$4" log="$5" metrics="${6:-{}}"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$suite" "$name" "$status" "$ms" "$log" "$metrics" >> "$STEPS"
}

# 每步独立判定，不在第一步就退出——一次跑完能看到全部问题
run_step() {
  local name="$1"; shift
  if "$@"; then ok "$name"; else bad "$name"; fi
}

# ---------------------------------------------------------------------------
# 1 + 2. Java 测试与测试数阈值
# ---------------------------------------------------------------------------
step "1/7 Java 测试（mvn -o clean test）"
T0=$SECONDS

# clean 在 Windows 开发机上常被 IDE 语言服务器锁住 target/classes（Trae/IDEA 索引中），
# 那属于本机环境问题而不是代码问题：降级为「只清 surefire 报告 + 不 clean 的 test」，
# 并明确提示，避免有人以为门禁坏了。CI（Linux）上 clean 永远能过。
if JAVA_HOME="$JAVA_HOME" "$MVN_BIN" -o clean > /dev/null 2>&1; then
  note "clean 完成"
else
  note "clean 失败（本机 IDE 正在索引 target/classes 是常见原因），改为只清 surefire 报告"
  rm -rf target/surefire-reports 2>/dev/null || true
fi

MVN_OK=1
if JAVA_HOME="$JAVA_HOME" "$MVN_BIN" -o test > "$LOG_DIR/verify-mvn.log" 2>&1; then
  ok "mvn test"
else
  MVN_OK=0
  bad "mvn test（日志 $LOG_DIR/verify-mvn.log）"
  grep -a -E "ERROR|BUILD FAILURE|Tests run:.*Fail" "$LOG_DIR/verify-mvn.log" | head -20 || true
fi

step "2/7 测试数阈值（按 <testcase> 节点统计）"
COUNT=0
if compgen -G "target/surefire-reports/*.xml" > /dev/null; then
  COUNT=$(grep -a -o "<testcase " target/surefire-reports/*.xml | wc -l | tr -d ' ')
fi
if [ "$COUNT" -ge "$MIN_TESTS" ]; then
  ok "实际执行 ${COUNT} 个用例（阈值 ${MIN_TESTS}）"
  [ "$MVN_OK" = "1" ] && record S1 "Java 单测 + 测试数阈值" PASS "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-mvn.log" "{\"tests\":$COUNT,\"minTests\":$MIN_TESTS}" \
    || record S1 "Java 单测 + 测试数阈值" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-mvn.log" "{\"tests\":$COUNT,\"minTests\":$MIN_TESTS}"
else
  bad "只执行了 ${COUNT} 个用例，低于阈值 ${MIN_TESTS} —— 测试可能被跳过或没被 surefire 收集"
  record S1 "Java 单测 + 测试数阈值" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-mvn.log" "{\"tests\":$COUNT,\"minTests\":$MIN_TESTS}"
fi

# ---------------------------------------------------------------------------
# 3. i18n 快照契约（V9.2 新增：原先只在独立 workflow 里跑，未入唯一门禁）
# ---------------------------------------------------------------------------
step "3/7 i18n 快照契约（9 模式 OFF/ON 零 diff）"
T0=$SECONDS
if node scripts/testcenter-suites.mjs S2 > "$LOG_DIR/verify-i18n.log" 2>&1; then
  ok "$(grep -a 'RESULT' "$LOG_DIR/verify-i18n.log" | tail -1)"
  record S2 "i18n 快照契约" PASS "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-i18n.log" '{"patterns":9,"passed":9}'
else
  bad "i18n 快照契约失败（详见 $LOG_DIR/verify-i18n.log）"
  grep -a -E "FAIL|ERROR" "$LOG_DIR/verify-i18n.log" | head -8 || true
  record S2 "i18n 快照契约" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-i18n.log" '{"patterns":9}'
fi

# ---------------------------------------------------------------------------
# 4. 206 语言键覆盖（V9.2 新增：此前未接任何门禁）
# ---------------------------------------------------------------------------
step "4/7 206 语言键覆盖（8 精译 / 12 RTL）"
T0=$SECONDS
if node scripts/testcenter-suites.mjs S4 > "$LOG_DIR/verify-locales.log" 2>&1; then
  ok "$(grep -a 'RESULT' "$LOG_DIR/verify-locales.log" | tail -1)"
  record S4 "206 语言键覆盖" PASS "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-locales.log" '{"locales":206,"covered":206,"builtin":8,"rtl":12}'
else
  bad "206 语言键覆盖失败（详见 $LOG_DIR/verify-locales.log）"
  grep -a -E "RESULT FAIL|缺" "$LOG_DIR/verify-locales.log" | head -8 || true
  record S4 "206 语言键覆盖" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-locales.log" '{"locales":206}'
fi

# ---------------------------------------------------------------------------
# 5. 生成物 JS 语法校验（依赖阶段 1 产出的 target/gen-matrix）
# ---------------------------------------------------------------------------
step "5/7 生成物 JS 语法校验（1024 组合）"
T0=$SECONDS
if ! command -v node > /dev/null 2>&1; then
  bad "找不到 node —— JS 校验是必过项，不做静默跳过（装 Node 或把 node 加进 PATH）"
  record S5 "生成物 JS 语法" FAIL "$(((SECONDS-T0)*1000))" "" '{}'
elif node scripts/check-generated-js.mjs > "$LOG_DIR/verify-js.log" 2>&1; then
  ok "$(grep -a '组合数' "$LOG_DIR/verify-js.log" | head -1)"
  record S5 "生成物 JS 语法" PASS "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-js.log" '{"combinations":1024,"badCombinations":0}'
else
  bad "生成物存在语法错误（详见 $LOG_DIR/verify-js.log）"
  grep -a -E "坏组合|RESULT" "$LOG_DIR/verify-js.log" | head -5 || true
  record S5 "生成物 JS 语法" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-js.log" '{}'
fi

# ---------------------------------------------------------------------------
# 6. 前端构建
# ---------------------------------------------------------------------------
step "6/7 前端构建（../codegen-web）"
T0=$SECONDS
if [ "$SKIP_WEB" = "1" ]; then
  note "已按 --skip-web 跳过"
  record S6 "控制台前端构建" SKIP 0 "" '{"reason":"--skip-web"}'
elif [ ! -d ../codegen-web ]; then
  note "找不到 ../codegen-web，跳过"
  record S6 "控制台前端构建" SKIP 0 "" '{"reason":"codegen-web 不存在"}'
# NODE_OPTIONS 清空：AI 宿主（如 WorkBuddy）会给所有 node 子进程注入 require 形式的 fs shim，
# vite 的 rimraf 走 shim 的「移到回收站」分支在 Windows 上会失败。CI 上本来就为空。
elif ( cd ../codegen-web && NODE_OPTIONS= npm run build > "$ROOT/$LOG_DIR/verify-web.log" 2>&1 ); then
  ok "npm run build"
  record S6 "控制台前端构建" PASS "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-web.log" '{"build":"success"}'
else
  bad "前端构建失败（详见 $LOG_DIR/verify-web.log）"
  record S6 "控制台前端构建" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-web.log" '{"build":"failed"}'
fi

# ---------------------------------------------------------------------------
# 7. 空库冒烟（需 MySQL）
# ---------------------------------------------------------------------------
step "7/7 空库冒烟（docs/sql 全量脚本 + 幂等重跑）"
T0=$SECONDS
if [ "$WITH_DB" != "1" ] || [ "$SKIP_SMOKE" = "1" ]; then
  note "未开启（--with-db 打开；需要本机 MySQL 与 CG_DB_PASSWORD）"
  record S7 "空库冒烟" SKIP 0 "" '{"reason":"未开启 --with-db"}'
elif node scripts/smoke-fresh-db.mjs > "$LOG_DIR/verify-smoke.log" 2>&1; then
  ok "$(grep -a 'RESULT' "$LOG_DIR/verify-smoke.log" | head -1)"
  record S7 "空库冒烟" PASS "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-smoke.log" '{"smoke":"passed"}'
else
  bad "空库冒烟失败（详见 $LOG_DIR/verify-smoke.log）"
  grep -a -E "FAIL|ERROR" "$LOG_DIR/verify-smoke.log" | head -5 || true
  record S7 "空库冒烟" FAIL "$(((SECONDS-T0)*1000))" "$LOG_DIR/verify-smoke.log" '{"smoke":"failed"}'
fi

# ---------------------------------------------------------------------------
# 汇总 + 机器可读摘要（CI 可直接归档 logs/testcenter/summary.json）
# ---------------------------------------------------------------------------
if command -v node > /dev/null 2>&1; then
  node scripts/testcenter-summary.mjs "$STEPS" "$SUMMARY" > "$LOG_DIR/summary-stdout.log" 2>&1 || true
  grep -a '^TC_SUMMARY' "$LOG_DIR/summary-stdout.log" || true
else
  note "找不到 node，跳过 JSON 摘要输出"
fi

printf '\n\033[1m== 汇总 ==\033[0m\n'
printf '通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  printf '失败项：\n'
  for s in "${FAILED_STEPS[@]}"; do printf '  - %s\n' "$s"; done
  exit 1
fi
printf '\033[32mRESULT PASS\033[0m\n'
