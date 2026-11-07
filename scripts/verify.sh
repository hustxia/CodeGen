#!/usr/bin/env bash
# ============================================================================
# verify.sh —— V7.4 P0-2 唯一门禁入口（本地与 CI 跑同一份命令）
#
# 为什么要有它：2026-11-07 之前的验证靠人记命令，结果「240 例测试全绿」
# 不代表交付脚本能从空库跑起来（schema-test.sql 只有 7 张表），
# 「模板渲染不抛异常」不代表产物是合法 JS（192 种组合生成坏 JS 曾漏到线上）。
# 门禁必须把这几件事串成一条命令，漏一步就失败。
#
# 阶段（默认全跑，可用参数裁剪）：
#   1. mvn clean test                 —— 单测/契约测试/组合矩阵
#   2. 测试数阈值                     —— 按 <testcase> 节点数统计，0 例也算失败
#   3. 生成物 JS 语法校验             —— node scripts/check-generated-js.mjs
#   4. 前端构建                       —— codegen-web npm run build（--skip-web 跳过）
#   5. 空库冒烟                       —— 需 MySQL（--with-db 打开）
#
# 用法：
#   ./scripts/verify.sh                 # 本机全量（无 MySQL 时自动跳过冒烟）
#   ./scripts/verify.sh --with-db       # 额外跑空库冒烟
#   ./scripts/verify.sh --skip-web      # 只验后端
#   MIN_TESTS=240 ./scripts/verify.sh
# ============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MIN_TESTS="${MIN_TESTS:-240}"
MVN_BIN="${MVN_BIN:-mvn}"
JAVA_HOME="${JAVA_HOME:-}"
WITH_DB=0
SKIP_WEB=0
SKIP_SMOKE=0

for arg in "$@"; do
  case "$arg" in
    --with-db)  WITH_DB=1 ;;
    --skip-web) SKIP_WEB=1 ;;
    --skip-smoke) SKIP_SMOKE=1 ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

PASS=0
FAIL=0
FAILED_STEPS=()

step()  { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok()    { PASS=$((PASS+1)); printf '\033[32mPASS\033[0m  %s\n' "$1"; }
bad()   { FAIL=$((FAIL+1)); FAILED_STEPS+=("$1"); printf '\033[31mFAIL\033[0m  %s\n' "$1"; }
note()  { printf '\033[36mINFO\033[0m  %s\n' "$1"; }

# 每步独立判定，不在第一步就退出——一次跑完能看到全部问题
run_step() {
  local name="$1"; shift
  if "$@"; then ok "$name"; else bad "$name"; fi
}

# ---------------------------------------------------------------------------
# 1 + 2. Java 测试与测试数阈值
# ---------------------------------------------------------------------------
step "1/5 Java 测试（mvn -o clean test）"

# clean 在 Windows 开发机上常被 IDE 语言服务器锁住 target/classes（Trae/IDEA 索引中），
# 那属于本机环境问题而不是代码问题：降级为「只清 surefire 报告 + 不 clean 的 test」，
# 并明确提示，避免有人以为门禁坏了。CI（Linux）上 clean 永远能过。
if JAVA_HOME="$JAVA_HOME" "$MVN_BIN" -o clean > /dev/null 2>&1; then
  note "clean 完成"
else
  note "clean 失败（本机 IDE 正在索引 target/classes 是常见原因），改为只清 surefire 报告"
  rm -rf target/surefire-reports 2>/dev/null || true
fi

if JAVA_HOME="$JAVA_HOME" "$MVN_BIN" -o test > /tmp/verify-mvn.log 2>&1; then
  ok "mvn test"
else
  bad "mvn test（日志 /tmp/verify-mvn.log）"
  grep -a -E "ERROR|BUILD FAILURE|Tests run:.*Fail" /tmp/verify-mvn.log | head -20 || true
fi

step "2/5 测试数阈值（按 <testcase> 节点统计）"
COUNT=0
if compgen -G "target/surefire-reports/*.xml" > /dev/null; then
  COUNT=$(grep -a -o "<testcase " target/surefire-reports/*.xml | wc -l | tr -d ' ')
fi
if [ "$COUNT" -ge "$MIN_TESTS" ]; then
  ok "实际执行 ${COUNT} 个用例（阈值 ${MIN_TESTS}）"
else
  bad "只执行了 ${COUNT} 个用例，低于阈值 ${MIN_TESTS} —— 测试可能被跳过或没被 surefire 收集"
fi

# ---------------------------------------------------------------------------
# 3. 生成物 JS 语法校验（依赖阶段 1 产出的 target/gen-matrix）
# ---------------------------------------------------------------------------
step "3/5 生成物 JS 语法校验（1024 组合）"
if ! command -v node > /dev/null 2>&1; then
  bad "找不到 node —— JS 校验是必过项，不做静默跳过（装 Node 或把 node 加进 PATH）"
elif node scripts/check-generated-js.mjs > /tmp/verify-js.log 2>&1; then
  ok "$(grep -a '组合数' /tmp/verify-js.log | head -1)"
else
  bad "生成物存在语法错误（详见 /tmp/verify-js.log）"
  grep -a -E "坏组合|RESULT" /tmp/verify-js.log | head -5 || true
fi

# ---------------------------------------------------------------------------
# 4. 前端构建
# ---------------------------------------------------------------------------
step "4/5 前端构建（../codegen-web）"
if [ "$SKIP_WEB" = "1" ]; then
  note "已按 --skip-web 跳过"
elif [ ! -d ../codegen-web ]; then
  note "找不到 ../codegen-web，跳过"
# NODE_OPTIONS 清空：AI 宿主（如 WorkBuddy）会给所有 node 子进程注入 require 形式的 fs shim，
# vite 的 rimraf 走 shim 的「移到回收站」分支在 Windows 上会失败。CI 上本来就为空。
elif ( cd ../codegen-web && NODE_OPTIONS= npm run build > /tmp/verify-web.log 2>&1 ); then
  ok "npm run build"
else
  bad "前端构建失败（详见 /tmp/verify-web.log）"
fi

# ---------------------------------------------------------------------------
# 5. 空库冒烟（需 MySQL）
# ---------------------------------------------------------------------------
step "5/5 空库冒烟（docs/sql 全量脚本 + 幂等重跑）"
if [ "$WITH_DB" != "1" ] || [ "$SKIP_SMOKE" = "1" ]; then
  note "未开启（--with-db 打开；需要本机 MySQL 与 CG_DB_PASSWORD）"
elif node scripts/smoke-fresh-db.mjs > /tmp/verify-smoke.log 2>&1; then
  ok "$(grep -a 'RESULT' /tmp/verify-smoke.log | head -1)"
else
  bad "空库冒烟失败（详见 /tmp/verify-smoke.log）"
  grep -a -E "FAIL|ERROR" /tmp/verify-smoke.log | head -5 || true
fi

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
printf '\n\033[1m== 汇总 ==\033[0m\n'
printf '通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  printf '失败项：\n'
  for s in "${FAILED_STEPS[@]}"; do printf '  - %s\n' "$s"; done
  exit 1
fi
printf '\033[32mRESULT PASS\033[0m\n'
