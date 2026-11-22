#!/usr/bin/env bash
# 批量截取智慧村务系统关键页面，作为 CSDN 博客配图
# 依赖：无头 Chrome；系统已运行在 9040，后端 8140（自动 demo-login）
set -u
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://127.0.0.1:9040"
OUT="D:/workspace/xyh-devbranch/codegen-server/docs/blog/images"
mkdir -p "$OUT"

shoot() {
  local name="$1" url="$2" profile
  profile="/tmp/csdn-shot-${name}"
  rm -rf "$profile" 2>/dev/null
  "$CHROME" --headless=new --disable-gpu --no-sandbox \
    --user-data-dir="$profile" --virtual-time-budget=9000 \
    --window-size=1440,900 --hide-scrollbars \
    --screenshot="$OUT/${name}.png" "$url" >/dev/null 2>&1
  if [ -f "$OUT/${name}.png" ]; then
    echo "OK  ${name}.png  $(stat -c %s "$OUT/${name}.png") bytes"
  else
    echo "FAIL ${name}.png"
  fi
}

shoot 01-home            "$BASE/"
shoot 02-menu-expanded   "$BASE/?_menu=1"
shoot 03-resident-list   "$BASE/village_affairs/base/village_resident/list"
shoot 04-household-list  "$BASE/village_affairs/base/village_household/list"
shoot 05-party-member    "$BASE/village_affairs/party/village_party_member/list"
shoot 06-asset-list      "$BASE/village_affairs/assets/village_asset/list"
shoot 07-service-apply   "$BASE/village_affairs/service/village_service_apply/list"
shoot 08-approval        "$BASE/village_affairs/system/approval/list"
shoot 09-dispute         "$BASE/village_affairs/dispute/village_dispute/list"
shoot 10-grid-event      "$BASE/village_affairs/grid/village_grid_event/list"
echo "=== 截图完成 ==="
ls -la "$OUT"
