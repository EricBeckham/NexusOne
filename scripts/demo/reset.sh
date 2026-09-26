#!/usr/bin/env bash
# 禁止连生产 / 默认关闭
# 一键重置本机演示种子包。用法：bash scripts/demo/reset.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
DEMO_DIR="$ROOT/scripts/demo"

echo "演示种子一键重置：将清理 approval/K20 → P5 → P4 → P3 → P2 → P1 固定演示行。"
echo "请先停止后端进程；清理完成后 source scripts/demo/enable.sh 并重启。"

run_ps() {
  local script="$1"
  if command -v pwsh >/dev/null 2>&1; then
    pwsh -File "$script"
  elif command -v powershell >/dev/null 2>&1; then
    powershell -File "$script"
  else
    echo "需要 pwsh/powershell 运行 $script" >&2
    exit 1
  fi
}

run_ps "$DEMO_DIR/clean-approval.ps1"
run_ps "$DEMO_DIR/clean-p5.ps1"
run_ps "$DEMO_DIR/clean-p4.ps1"
run_ps "$DEMO_DIR/clean-p3.ps1"
run_ps "$DEMO_DIR/clean-p2.ps1"
run_ps "$DEMO_DIR/clean-p1.ps1"

echo "演示行已清理。下一步：source scripts/demo/enable.sh 后重启后端。"
