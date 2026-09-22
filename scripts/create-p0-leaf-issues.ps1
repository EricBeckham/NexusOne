$ErrorActionPreference = 'Stop'
$repo = 'EricBeckham/NexusOne'
$source = Join-Path (Split-Path -Parent $PSScriptRoot) '团队领取任务清单_V1.1.md'

$labels = @(
    @{ Name='type:leaf'; Color='bfdadc'; Description='Individually assignable task' },
    @{ Name='handoff:unassigned'; Color='fef2c0'; Description='Waiting for a human owner and reviewer' },
    @{ Name='runtime:local-no-docker'; Color='c5def5'; Description='Local process/JAR runtime, no Docker' }
)
foreach ($label in $labels) {
    gh label create $label.Name --repo $repo --color $label.Color --description $label.Description --force | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to create label $($label.Name)" }
}

$names = @{
    'P0-01A'='Query AST 与 PostgreSQL Renderer'
    'P0-01B'='租户运行时 Guard 与 PlatformContext'
    'P0-01C'='NativeSqlPort 与 CI 绕过检查'
    'P0-02A'='RBAC 与显式拒绝语义'
    'P0-02B'='DataScope 与字段策略编译'
    'P0-02C'='PolicySnapshot 与 Redis 失效'
    'P0-03A'='Keycloak 本机运行及 PostgreSQL 配置'
    'P0-03B'='Keycloak 多租户认证契约'
    'P0-04A'='Flowable 同库同事务集成'
    'P0-04B'='Flowable BPMN/DMN 与升级回归'
    'P0-05A'='Redis 3.2 能力契约'
    'P0-05B'='Redis 故障与缓存失效验证'
    'P0-06A'='对象存储本机 S3 契约'
    'P0-06B'='文件授权与对象对账'
    'P0-07A'='Quartz JDBC 调度与故障转移'
    'P0-07B'='Flyway 静态迁移与恢复'
    'P0-08A'='SchemaService 与台账 Quota'
    'P0-08B'='动态台账容量基准与阈值'
    'P0-09A'='共享表前向兼容演进'
    'P0-09B'='单租户应用版本回滚验证'
    'P0-10A'='本机 JAR 运行 Profile'
    'P0-10B'='CryptoPort 国际与国密契约'
    'P0-10C'='供应链与恢复证据汇总'
}

$existing = @(gh issue list --repo $repo --state all --limit 200 --json title | ConvertFrom-Json | ForEach-Object title)
if ($LASTEXITCODE -ne 0) { throw 'Failed to list existing issues' }

$rows = Get-Content -LiteralPath $source -Encoding utf8 | Where-Object { $_ -match '^\| P0-\d{2}[A-C] \|' }
if ($rows.Count -ne $names.Count) { throw "Expected $($names.Count) P0 rows, found $($rows.Count)" }

foreach ($row in $rows) {
    $parts = $row.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
    $id, $priority, $parent, $deliverable, $done = $parts
    if (-not $names.ContainsKey($id)) { throw "Missing name for $id" }
    if ($parent -notmatch '#(\d+)') { throw "Missing parent issue for $id" }
    $parentNumber = $Matches[1]
    $title = "[$id] $($names[$id])"
    if ($existing -contains $title) {
        Write-Output "Exists: $title"
        continue
    }
    $body = @"
## 交接

父任务：#$parentNumber  |  优先级：$priority  |  执行范围：PostgreSQL 15、Redis 3.2、本机 JAR/进程，**不使用 Docker**。

此 Issue 供团队成员领取。领取人填写负责人、评审人、预计交付、代码位置和证据链接。此前 PoC 可参考，但不得替代产品验收。

## 独立交付物

$deliverable

## 前置与完成判定

$done

数据库密码仅通过领取人的本机环境变量或受控 Secret 提供，不写入 Git、Issue 或测试报告。当前仅建单交接，不执行任务。
"@
    $path = Join-Path $env:TEMP "nexusone-$id-leaf.md"
    Set-Content -LiteralPath $path -Value $body -Encoding utf8
    try {
        gh issue create --repo $repo --title $title --body-file $path --label 'phase:P0' --label 'database:PostgreSQL' --label 'type:leaf' --label 'handoff:unassigned' --label 'runtime:local-no-docker'
        if ($LASTEXITCODE -ne 0) { throw "Failed to create $title" }
    }
    finally {
        Remove-Item -LiteralPath $path -ErrorAction SilentlyContinue
    }
}
