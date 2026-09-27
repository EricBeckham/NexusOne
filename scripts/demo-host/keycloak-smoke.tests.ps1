$ErrorActionPreference = 'Stop'
$saved = $env:NEXUS_DEMO_KEYCLOAK_SMOKE_URL
try {
    $env:NEXUS_DEMO_KEYCLOAK_SMOKE_URL = ''
    $output = & pwsh -NoProfile -File "$PSScriptRoot/keycloak-smoke.ps1"
    if ($LASTEXITCODE -ne 0 -or "$output" -notmatch '^skipped:') { throw 'skip contract failed' }
    $env:NEXUS_DEMO_KEYCLOAK_SMOKE_URL = 'https://REPLACE_USER:REPLACE_SECRET@example.invalid/?REPLACE_TOKEN'
    $output = & pwsh -NoProfile -File "$PSScriptRoot/keycloak-smoke.ps1"
    if ($LASTEXITCODE -ne 1 -or "$output" -match 'REPLACE_SECRET|REPLACE_TOKEN|REPLACE_USER') { throw 'redaction contract failed' }
    $env:NEXUS_DEMO_KEYCLOAK_SMOKE_URL = 'https://127.0.0.1:1/REPLACE_PRIVATE_PATH'
    $output = & pwsh -NoProfile -File "$PSScriptRoot/keycloak-smoke.ps1" -TimeoutSeconds 1
    if ($LASTEXITCODE -ne 1 -or "$output" -match 'REPLACE_PRIVATE_PATH' -or "$output" -notmatch 'host=127.0.0.1 status=unavailable') { throw 'failure contract failed' }
    Write-Output 'passed: skip, credential rejection/redaction, unreachable HTTPS'
} finally { $env:NEXUS_DEMO_KEYCLOAK_SMOKE_URL = $saved }
