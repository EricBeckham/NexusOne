# 禁止连生产 / 默认关闭
# 用法（当前窗口）：. .\scripts\demo\cleanup.ps1
# 本卡没有插入业务行。清理只是关掉开关。

$ErrorActionPreference = "Stop"
Remove-Item Env:NEXUS_DEMO_ENABLED -ErrorAction SilentlyContinue
if ($env:SPRING_PROFILES_ACTIVE) {
    $kept = @($env:SPRING_PROFILES_ACTIVE -split ',' | Where-Object { $_.Trim() -and $_.Trim() -ne 'demo' })
    if ($kept.Count -eq 0) {
        Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    } else {
        $env:SPRING_PROFILES_ACTIVE = ($kept -join ',')
    }
}
Write-Output "演示开关已在当前进程关闭。默认关闭。禁止连生产。本卡没有业务行需要删除。"
