param([int]$TimeoutSeconds = 10)
$ErrorActionPreference = 'Stop'
$raw = $env:NEXUS_DEMO_POSTLOGIN_SMOKE_URL
if ([string]::IsNullOrWhiteSpace($raw)) {
    Write-Output 'skipped: NEXUS_DEMO_POSTLOGIN_SMOKE_URL is not configured (not acceptance)'
    exit 0
}
$target = $null
if (-not [Uri]::TryCreate($raw, [UriKind]::Absolute, [ref]$target) -or
    $target.Scheme -ne 'https' -or $target.UserInfo -or $target.Query -or $target.Fragment -or
    $TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 60) {
    Write-Output 'failed: invalid NEXUS_DEMO_POSTLOGIN_SMOKE_URL configuration'
    exit 1
}
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$handler.UseCookies = $false
$handler.UseDefaultCredentials = $false
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
try {
    # Anonymous HTTPS availability only. No tokens, browser sessions or response logging.
    $response = $client.GetAsync($target, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    $status = [int]$response.StatusCode
    $response.Dispose()
    Write-Output "probe: host=$($target.DnsSafeHost) status=$status (availability only)"
    if ($status -eq 200) { exit 0 }
    exit 1
} catch {
    Write-Output "failed: host=$($target.DnsSafeHost) status=unavailable"
    exit 1
} finally {
    $client.Dispose()
    $handler.Dispose()
}
