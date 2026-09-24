# 禁止连生产 / 默认关闭
# 用法（当前窗口）：. .\scripts\demo\enable.ps1
# 本脚本不连接数据库，不插入业务行。

$ErrorActionPreference = "Stop"
$envName = if ($env:NEXUS_DEMO_ENVIRONMENT) { $env:NEXUS_DEMO_ENVIRONMENT } else { "local" }
$url = if ($env:NEXUS_DB_URL) { $env:NEXUS_DB_URL } else { "jdbc:postgresql://127.0.0.1:5432/nexusone" }

if ($envName -match '^(?i)prod(uction)?$') {
    Write-Error "禁止连生产：NEXUS_DEMO_ENVIRONMENT=$envName"
}
if ($url -notmatch '127\.0\.0\.1|localhost|\[::1\]') {
    Write-Error "禁止连生产：数据库地址不是本机：$url"
}

$env:NEXUS_DEMO_ENABLED = "true"
$env:NEXUS_DEMO_ENVIRONMENT = $envName
Write-Output "演示开关已在当前进程设为打开。默认关闭。禁止连生产。未插入业务行。"
Write-Output "若直接执行而不是点源（. .\scripts\demo\enable.ps1），父进程不会带上这个变量。"
