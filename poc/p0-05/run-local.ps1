$ErrorActionPreference = 'Stop'
$cli = if ($env:REDIS_CLI) { $env:REDIS_CLI } else { 'D:\Software\Redis\redis-cli.exe' }
if (-not (Test-Path -LiteralPath $cli)) { throw "redis-cli not found: $cli" }
$hostArg = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { '127.0.0.1' }
$portArg = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { '6379' }

function Invoke-Redis([string[]]$RedisArgs) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $cli -h $hostArg -p $portArg --raw @RedisArgs 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($code -ne 0) { throw "redis-cli failed ($code): $($RedisArgs -join ' ')" }
    return (($output | Where-Object { $_ -ne $null }) -join "`n").Trim()
}
function Expect([string]$Actual, [string]$Expected, [string]$Label) {
    if ($Actual -ne $Expected) { throw "FAIL ${Label}: expected '$Expected', got '$Actual'" }
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$tenantA = '11111111-1111-4111-8111-111111111111'
$tenantB = '22222222-2222-4222-8222-222222222222'
$prefixA = "nexus:v1:{$tenantA}:cache:p0-05:$runId"
$prefixB = "nexus:v1:{$tenantB}:cache:p0-05:$runId"
$ttlKey = "${prefixA}:ttl"
$counterKey = "${prefixA}:counter"
$lockKey = "${prefixA}:lock"
$tenantAKey = "${prefixA}:identity"
$tenantBKey = "${prefixB}:identity"
# Windows redis-cli strips double quotes from arguments. Lua long brackets keep the command names.
$release = 'if redis.call([[GET]], KEYS[1]) == ARGV[1] then return redis.call([[DEL]], KEYS[1]) else return 0 end'

try {
    Expect (Invoke-Redis -RedisArgs @('PING')) 'PONG' 'redis availability'
    Write-Output 'PASS: Redis PING'

    Expect (Invoke-Redis -RedisArgs @('SET', $ttlKey, 'value', 'EX', '1')) 'OK' 'SET EX'
    $ttl = [int](Invoke-Redis -RedisArgs @('TTL', $ttlKey))
    if ($ttl -lt 0 -or $ttl -gt 1) { throw "FAIL TTL out of range: $ttl" }
    Start-Sleep -Seconds 2
    Expect (Invoke-Redis -RedisArgs @('EXISTS', $ttlKey)) '0' 'expiration'
    Write-Output 'PASS: TTL and expiration'

    Expect (Invoke-Redis -RedisArgs @('INCR', $counterKey)) '1' 'first INCR'
    Expect (Invoke-Redis -RedisArgs @('INCR', $counterKey)) '2' 'second INCR'
    Expect (Invoke-Redis -RedisArgs @('GET', $counterKey)) '2' 'atomic counter'
    Write-Output 'PASS: atomic INCR'

    $token = [guid]::NewGuid().ToString()
    $other = [guid]::NewGuid().ToString()
    Expect (Invoke-Redis -RedisArgs @('SET', $lockKey, $token, 'NX', 'PX', '5000')) 'OK' 'lock acquisition'
    $contended = Invoke-Redis -RedisArgs @('SET', $lockKey, $other, 'NX', 'PX', '5000')
    if ($contended -ne '') { throw "FAIL contended lock acquisition: expected empty nil, got '$contended'" }
    Expect (Invoke-Redis -RedisArgs @('EVAL', $release, '1', $lockKey, $other)) '0' 'wrong-token unlock'
    Expect (Invoke-Redis -RedisArgs @('GET', $lockKey)) $token 'lock survived wrong-token unlock'
    Expect (Invoke-Redis -RedisArgs @('EVAL', $release, '1', $lockKey, $token)) '1' 'owner unlock'
    Expect (Invoke-Redis -RedisArgs @('EXISTS', $lockKey)) '0' 'lock deletion'
    Write-Output 'PASS: NX/PX lock ownership and safe release'

    Expect (Invoke-Redis -RedisArgs @('SET', $tenantAKey, 'A', 'EX', '60')) 'OK' 'tenant A write'
    Expect (Invoke-Redis -RedisArgs @('SET', $tenantBKey, 'B', 'EX', '60')) 'OK' 'tenant B write'
    Expect (Invoke-Redis -RedisArgs @('GET', $tenantAKey)) 'A' 'tenant A value'
    Expect (Invoke-Redis -RedisArgs @('GET', $tenantBKey)) 'B' 'tenant B value'
    Expect (Invoke-Redis -RedisArgs @('EXISTS', "${prefixB}:identity-unwritten")) '0' 'tenant B unwritten key'
    Write-Output 'PASS: distinct tenant-prefixed keys'
    $version = Invoke-Redis -RedisArgs @('INFO', 'server')
    $versionLine = ($version -split "`n" | Where-Object { $_ -like 'redis_version:*' } | Select-Object -First 1)
    Write-Output "PASS: local Redis contract ($versionLine)"
    Write-Output 'NOTE: single-node contract only; Sentinel/replica failover was not run'
} finally {
    & $cli -h $hostArg -p $portArg --raw DEL $ttlKey $counterKey $lockKey $tenantAKey $tenantBKey | Out-Null
}
