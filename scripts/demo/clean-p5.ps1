param()

$ErrorActionPreference = "Stop"
$profiles = @($env:SPRING_PROFILES_ACTIVE -split ',' | ForEach-Object { $_.Trim() })
$enabled = $env:NEXUS_DEMO_ENABLED -ieq "true" -or $profiles -contains "demo"
if (-not $enabled) { throw "演示开关未打开；拒绝清理演示数据。" }

$environment = if ($env:NEXUS_DEMO_ENVIRONMENT) { $env:NEXUS_DEMO_ENVIRONMENT } else { "local" }
if ($environment -match '^(?i)prod(uction)?$') { throw "禁止连生产：拒绝清理演示数据。" }
$url = if ($env:NEXUS_DB_URL) { $env:NEXUS_DB_URL } else { "jdbc:postgresql://127.0.0.1:5432/nexusone" }
$match = [regex]::Match($url, '^jdbc:postgresql://(?<host>\[[^\]]+\]|[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
if (-not $match.Success -or $match.Groups['host'].Value -notmatch '^(127\.0\.0\.1|localhost|\[::1\])$') {
    throw "禁止连生产：演示数据清理只允许明确的本机 PostgreSQL 地址。"
}
$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) { throw "需要本机 PostgreSQL psql 客户端才能清理演示数据。" }

$port = if ($match.Groups['port'].Success) { $match.Groups['port'].Value } else { "5432" }
$hostName = $match.Groups['host'].Value -replace '^\[|\]$', ''
$user = if ($env:NEXUS_DB_USER) { $env:NEXUS_DB_USER } else { "postgres" }
$database = $match.Groups['database'].Value
$sqlPath = Join-Path $PSScriptRoot "clean-p5.sql"
$previousPassword = $env:PGPASSWORD
try {
    if ($env:NEXUS_DB_PASSWORD) { $env:PGPASSWORD = $env:NEXUS_DB_PASSWORD }
    & $psql.Source --host $hostName --port $port --username $user --dbname $database --set ON_ERROR_STOP=1 --file $sqlPath
    if ($LASTEXITCODE -ne 0) { throw "P5 演示数据清理失败，psql exit code=$LASTEXITCODE" }
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    else { $env:PGPASSWORD = $previousPassword }
}
