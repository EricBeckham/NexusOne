param(
    [int]$Start = 1,
    [int]$End = 22
)

$ErrorActionPreference = 'Stop'
$repo = 'EricBeckham/NexusOne'
$note = @'
## 当前交接口径

用户已指定：本轮**只拆分和交接任务，不执行实现、测试、数据库或部署操作**。后续由团队成员领取。环境为本机 PostgreSQL 15（localhost:5432）、Redis 3.2（localhost:6379）；Java 服务直接以本机 JAR/进程运行，**不使用 Docker/Compose**。密码由领取人通过本机环境变量或受控 Secret 提供，不写入仓库或 Issue。此前 PoC 结果只作历史参考，不等于产品验收。细粒度领取任务见 `docs/planning/团队领取任务清单_V1.1.md`。
'@

foreach ($number in $Start..$End) {
    $body = gh issue view $number --repo $repo --json body --jq .body
    if ($LASTEXITCODE -ne 0) { throw "Could not read issue #$number" }
    if ($body -notmatch '## 当前交接口径') {
        $body = $body + "`n`n" + $note
    }
    if ($number -le 10) {
        $body = $body.Replace('**当前执行者：** Codex', '**历史 PoC 执行者：** Codex')
        $body = $body.Replace('**当前执行者：** 待领取', '**待分配：** 团队成员')
    }
    $path = Join-Path $env:TEMP "nexusone-parent-$number.md"
    Set-Content -LiteralPath $path -Value $body -Encoding utf8
    try {
        gh issue edit $number --repo $repo --body-file $path --add-label 'runtime:local-no-docker' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not update issue #$number" }
    }
    finally {
        Remove-Item -LiteralPath $path -ErrorAction SilentlyContinue
    }
}
Write-Output "Updated parent issues #$Start-#$End for local handoff"
