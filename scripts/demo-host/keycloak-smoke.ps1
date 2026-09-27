param([int]$TimeoutSeconds = 10)
$ErrorActionPreference = 'Stop'
$raw = $env:NEXUS_DEMO_KEYCLOAK_SMOKE_URL
if ([string]::IsNullOrWhiteSpace($raw)) {
    Write-Output 'skipped: NEXUS_DEMO_KEYCLOAK_SMOKE_URL is not configured (not acceptance)'
    exit 0
}
$target = $null
if (-not [Uri]::TryCreate($raw, [UriKind]::Absolute, [ref]$target) -or
    $target.Scheme -ne 'https' -or $target.UserInfo -or $target.Query -or $target.Fragment -or
    $TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 60) {
    Write-Output 'failed: invalid HTTPS smoke configuration; do not include credentials/query/fragment'
    exit 1
}
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
try {
    # No auth header, cookie or certificate bypass; never echo body/Location/path.
    $response = $client.GetAsync($target).GetAwaiter().GetResult()
    $status = [int]$response.StatusCode
    $response.Dispose()
    Write-Output "probe: host=$($target.DnsSafeHost) status=$status (not login acceptance)"
    if ($status -eq 200 -or $status -eq 302 -or $status -eq 303) { exit 0 }
    exit 1
} catch {
    Write-Output "failed: host=$($target.DnsSafeHost) status=unavailable"
    exit 1
} finally {
    $client.Dispose()
    $handler.Dispose()
}
