$ErrorActionPreference = 'Stop'
$saved = $env:NEXUS_DEMO_POSTLOGIN_SMOKE_URL
try {
    $env:NEXUS_DEMO_POSTLOGIN_SMOKE_URL = ''
    $output = & pwsh -NoProfile -File "$PSScriptRoot/postlogin-smoke.ps1"
    if ($LASTEXITCODE -ne 0 -or "$output" -notmatch '^skipped:') { throw 'skip contract failed' }
    foreach ($url in @('http://example.invalid/PRIVATE_PATH', 'https://USER:FAKE_SECRET@example.invalid/?FAKE_TOKEN#FAKE_COOKIE')) {
        $env:NEXUS_DEMO_POSTLOGIN_SMOKE_URL = $url
        $output = & pwsh -NoProfile -File "$PSScriptRoot/postlogin-smoke.ps1"
        if ($LASTEXITCODE -ne 1 -or "$output" -match 'PRIVATE_PATH|FAKE_SECRET|FAKE_TOKEN|FAKE_COOKIE|USER|example.invalid') { throw 'invalid configuration/redaction contract failed' }
    }
    $env:NEXUS_DEMO_POSTLOGIN_SMOKE_URL = 'https://127.0.0.1:1/PRIVATE_PATH'
    $output = & pwsh -NoProfile -File "$PSScriptRoot/postlogin-smoke.ps1" -TimeoutSeconds 1
    if ($LASTEXITCODE -ne 1 -or "$output" -match 'PRIVATE_PATH' -or "$output" -notmatch 'host=127.0.0.1 status=unavailable') { throw 'unreachable contract failed' }
    Write-Output 'passed: skipped, invalid/redacted HTTPS URL, unreachable host'
} finally { $env:NEXUS_DEMO_POSTLOGIN_SMOKE_URL = $saved }
