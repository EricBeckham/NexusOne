# M0-01 local JAR smoke (Windows). No Docker.
# Required env: NEXUS_DB_URL, NEXUS_DB_USER, NEXUS_DB_PASSWORD
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not $env:NEXUS_DB_URL) { throw 'set NEXUS_DB_URL' }
if (-not $env:NEXUS_DB_USER) { throw 'set NEXUS_DB_USER' }
if ($null -eq $env:NEXUS_DB_PASSWORD) { throw 'set NEXUS_DB_PASSWORD' }
if (-not $env:NEXUS_HTTP_PORT) { $env:NEXUS_HTTP_PORT = '18080' }

mvn -q -DskipTests package
$jar = Get-ChildItem "$root\nexus-bootstrap\target\nexus-bootstrap-*.jar" | Where-Object { $_.Name -notlike '*-original-*' -and $_.Name -notlike 'original-*' } | Select-Object -First 1
$log = Join-Path $env:TEMP 'nexusone-m0-01.log'
$proc = Start-Process -FilePath java -ArgumentList '-jar', $jar.FullName -RedirectStandardOutput $log -RedirectStandardError $log -PassThru
try {
    $health = $null
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$($env:NEXUS_HTTP_PORT)/actuator/health"
            break
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if ($health.status -ne 'UP') { throw "health not UP: $health" }
    Write-Output 'PASS: health UP'
    $trace = '0199a1c0-0000-7000-8000-00000000beef'
    $runtime = Invoke-RestMethod "http://127.0.0.1:$($env:NEXUS_HTTP_PORT)/api/v1/meta/runtime" -Headers @{ 'X-Trace-Id' = $trace }
    if ($runtime.trace_id -ne $trace) { throw "trace mismatch: $($runtime.trace_id)" }
    if ($runtime.schema_manager -ne 'flyway_static_only') { throw 'schema_manager mismatch' }
    Write-Output "PASS: runtime $($runtime.spring_boot) $($runtime.schema_manager)"
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
}
