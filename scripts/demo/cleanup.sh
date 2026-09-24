#!/bin/sh
# 禁止连生产 / 默认关闭
# 用法（当前 shell）：source scripts/demo/cleanup.sh
# 本卡没有插入业务行。清理只是关掉开关。

unset NEXUS_DEMO_ENABLED
if [ -n "${SPRING_PROFILES_ACTIVE:-}" ]; then
  kept=""
  old_ifs=$IFS
  IFS=,
  for part in $SPRING_PROFILES_ACTIVE; do
    [ "$part" = "demo" ] && continue
    if [ -z "$kept" ]; then
      kept=$part
    else
      kept=$kept,$part
    fi
  done
  IFS=$old_ifs
  if [ -z "$kept" ]; then
    unset SPRING_PROFILES_ACTIVE
  else
    export SPRING_PROFILES_ACTIVE="$kept"
  fi
fi
echo "演示开关已在当前进程关闭。默认关闭。禁止连生产。本卡没有业务行需要删除。"
