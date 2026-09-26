# 禁止连生产 / 默认关闭
# 一键重置本机演示种子包：先停后端，再按依赖逆序清理，然后重新 enable 并启动即可重播。
# 用法：.\scripts\demo\reset.ps1
# 需要：NEXUS_DEMO_ENABLED=true（或 demo profile）、本机回环 Postgres、psql。

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Output "演示种子一键重置：将清理 approval/K20 → P5 → P4 → P3 → P2 → P1 固定演示行。"
Write-Output "请先停止后端进程；清理完成后点源 enable.ps1 并重启，种子会幂等重播。"

& "$PSScriptRoot\clean-approval.ps1"
& "$PSScriptRoot\clean-p5.ps1"
& "$PSScriptRoot\clean-p4.ps1"
& "$PSScriptRoot\clean-p3.ps1"
& "$PSScriptRoot\clean-p2.ps1"
& "$PSScriptRoot\clean-p1.ps1"

Write-Output "演示行已清理。下一步："
Write-Output "  1) . .\scripts\demo\enable.ps1"
Write-Output "  2) 重启 nexus-bootstrap（demo 开关打开时自动重播种子）"
Write-Output "不建真 Keycloak / 外网主机；Flowable 部署若残留，重建本机库最干净。"
