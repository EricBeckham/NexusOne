[CmdletBinding()]
param(
    [string]$JavaPath = "$env:JAVA_HOME\bin\java.exe"
)

$ErrorActionPreference = 'Stop'
$allowedUrls = @(
    'jdbc:postgresql://127.0.0.1:5432/nexus_p0_07',
    'jdbc:postgresql://127.0.0.1:55438/nexus_p0_07'
)
if ($env:P0_07_JDBC_URL -notin $allowedUrls -or $env:P0_07_DB_USER -notin @('postgres', 'nexus_poc')) {
    throw 'This local fault-injection script only targets the dedicated loopback nexus_p0_07 PoC database'
}
if (-not (Test-Path -LiteralPath $JavaPath)) { throw "Java executable not found: $JavaPath" }
$runId = [guid]::NewGuid().ToString()
$env:P0_07_FAILOVER_ID = $runId
$classpathFile = Join-Path $PSScriptRoot 'target/classpath.txt'
if (-not (Test-Path -LiteralPath $classpathFile)) { throw 'Run mvn compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt first' }
$classpath = (Join-Path $PSScriptRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$resultDir = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
$stopFile = Join-Path $resultDir "$runId.stop"
$env:P0_07_STOP_FILE = $stopFile
$nodeA = $null
$nodeB = $null

function Invoke-Mode([string]$mode) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $JavaPath '-cp' $classpath 'poc.Main' $mode 2>&1 | ForEach-Object { "$_" }
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($exitCode -ne 0) { throw "Mode $mode failed: $($output -join ' ')" }
    return $output
}

function Get-Status {
    $lines = Invoke-Mode 'failover-status'
    $values = @{}
    foreach ($line in $lines) {
        if ($line -match '^([A-Z_]+)=(\d+)$') { $values[$Matches[1]] = [int]$Matches[2] }
    }
    return $values
}

function Wait-For([scriptblock]$condition, [string]$label, [int]$seconds = 30) {
    $until = (Get-Date).AddSeconds($seconds)
    do {
        $state = Get-Status
        if (& $condition $state) { return $state }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $until)
    throw "Timed out waiting for $label"
}

function Wait-ForNodeReady([string]$logFile, [string]$label) {
    $until = (Get-Date).AddSeconds(30)
    do {
        if ((Test-Path -LiteralPath $logFile) -and (Get-Content -LiteralPath $logFile -Raw) -match 'FAILOVER_NODE_READY instance=') { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $until)
    throw "Timed out waiting for $label; inspect $logFile and its .err counterpart"
}

function Stop-OnlyPocNode($process) {
    if ($null -eq $process -or $process.HasExited) { return }
    $actual = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
    if ($null -eq $actual -or $actual.CommandLine -notmatch 'poc\.Main failover-node') {
        throw "Refusing to stop PID $($process.Id): not a verified P0-07 failover node"
    }
    Stop-Process -Id $process.Id -Force
}

try {
    $nodeALog = Join-Path $resultDir "$runId-a.out"
    $nodeA = Start-Process -FilePath $JavaPath -ArgumentList @('-cp', $classpath, 'poc.Main', 'failover-node') -WindowStyle Hidden -RedirectStandardOutput $nodeALog -RedirectStandardError (Join-Path $resultDir "$runId-a.err") -PassThru
    Wait-ForNodeReady $nodeALog 'node A registration'
    Invoke-Mode 'failover-schedule' | Write-Output
    Wait-For { param($s) $s.START -eq 1 } 'long-running job start' | Out-Null
    $nodeBLog = Join-Path $resultDir "$runId-b.out"
    $nodeB = Start-Process -FilePath $JavaPath -ArgumentList @('-cp', $classpath, 'poc.Main', 'failover-node') -WindowStyle Hidden -RedirectStandardOutput $nodeBLog -RedirectStandardError (Join-Path $resultDir "$runId-b.err") -PassThru
    Wait-ForNodeReady $nodeBLog 'node B registration'
    $state = Get-Status
    if ($state.COMPLETE -ne 0) { throw 'Job completed before failure injection; retry with a fresh run ID' }
    Stop-OnlyPocNode $nodeA
    Write-Output "STOPPED verified PoC node A PID $($nodeA.Id)"
    Wait-For { param($s) $s.RECOVER_START -eq 1 -and $s.COMPLETE -eq 1 } 'recovery completion' 35 | Out-Null
    Invoke-Mode 'failover-check' | Write-Output
    Set-Content -LiteralPath $stopFile -Value 'stop' -NoNewline
    Wait-Process -Id $nodeB.Id -Timeout 15
    Write-Output "RUN_ID=$runId"
    Write-Output "NODE_A_PID=$($nodeA.Id) NODE_B_PID=$($nodeB.Id)"
    Write-Output "NODE_LOGS=$resultDir"
} finally {
    Stop-OnlyPocNode $nodeA
    Stop-OnlyPocNode $nodeB
    Remove-Item -LiteralPath $stopFile -ErrorAction SilentlyContinue
    Remove-Item Env:P0_07_FAILOVER_ID -ErrorAction SilentlyContinue
    Remove-Item Env:P0_07_STOP_FILE -ErrorAction SilentlyContinue
}
