#!/bin/sh
# 禁止连生产 / 默认关闭
# 用法（当前 shell）：source scripts/demo/enable.sh
# 本脚本不连接数据库，不插入业务行。

env_name="${NEXUS_DEMO_ENVIRONMENT:-local}"
url="${NEXUS_DB_URL:-jdbc:postgresql://127.0.0.1:5432/nexusone}"

case "$env_name" in
  prod|production|PROD|PRODUCTION)
    echo "禁止连生产：NEXUS_DEMO_ENVIRONMENT=$env_name" >&2
    return 1 2>/dev/null || exit 1
    ;;
esac

case "$url" in
  *127.0.0.1*|*localhost*|*"[::1]"*) ;;
  *)
    echo "禁止连生产：数据库地址不是本机：$url" >&2
    return 1 2>/dev/null || exit 1
    ;;
esac

export NEXUS_DEMO_ENABLED=true
export NEXUS_DEMO_ENVIRONMENT="$env_name"
echo "演示开关已在当前进程设为打开。默认关闭。禁止连生产。未插入业务行。"
echo "若直接执行而不是 source，父进程不会带上这个变量。"
