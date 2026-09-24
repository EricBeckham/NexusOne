# 禁止连生产 / 默认关闭
# 用法（当前窗口）：. .\scripts\demo\cleanup.ps1
# 只关闭开关；已存在的产品演示行须先运行各自 guarded clean-*.ps1 脚本。

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
Write-Output "演示开关已在当前进程关闭。默认关闭。禁止连生产。已有演示行请按产品卡运行对应清理脚本。"
