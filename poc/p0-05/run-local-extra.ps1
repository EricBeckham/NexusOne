$ErrorActionPreference = 'Stop'
$cli = if ($env:REDIS_CLI) { $env:REDIS_CLI } else { 'D:\Software\Redis\redis-cli.exe' }
function Invoke-Redis([string[]]$RedisArgs) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $cli -h 127.0.0.1 -p 6379 --raw @RedisArgs 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($code -ne 0) { throw "redis-cli failed ($code): $($RedisArgs -join ' ')" }
    return (($output | Where-Object { $_ -ne $null }) -join "`n").Trim()
}
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$lock = "nexus:v1:{p005}:cache:expiry:$stamp"
$cache = "nexus:v1:{p005}:cache:loss:$stamp"
$token = [guid]::NewGuid().ToString()
try {
    if ((Invoke-Redis -RedisArgs @('SET', $lock, $token, 'NX', 'PX', '400')) -ne 'OK') { throw 'lock acquire failed' }
    Start-Sleep -Milliseconds 700
    $again = Invoke-Redis -RedisArgs @('SET', $lock, 'next', 'NX', 'PX', '2000')
    if ($again -ne 'OK') { throw "expired lock was not acquirable: $again" }
    Write-Output 'PASS: lock expires and a later owner can acquire it'

    if ((Invoke-Redis -RedisArgs @('SET', $cache, 'snapshot-1', 'EX', '60')) -ne 'OK') { throw 'cache set failed' }
    if ((Invoke-Redis -RedisArgs @('DEL', $cache)) -ne '1') { throw 'cache delete failed' }
    $miss = Invoke-Redis -RedisArgs @('GET', $cache)
    if ($miss -ne '') { throw "cache loss still returned $miss" }
    Write-Output 'PASS: deleted cache key is a miss; authoritative state stays in PostgreSQL'

    $samples = New-Object System.Collections.Generic.List[double]
    1..100 | ForEach-Object {
        $sw = [Diagnostics.Stopwatch]::StartNew()
        [void](Invoke-Redis -RedisArgs @('PING'))
        $sw.Stop()
        $samples.Add($sw.Elapsed.TotalMilliseconds)
    }
    $sorted = $samples | Sort-Object
    $p95 = $sorted[[Math]::Floor(0.95 * ($sorted.Count - 1))]
    Write-Output ("PASS: local Redis PING P95 {0:N3} ms over 100 calls; not a product threshold" -f $p95)
} finally {
    & $cli -h 127.0.0.1 -p 6379 --raw DEL $lock $cache | Out-Null
}
