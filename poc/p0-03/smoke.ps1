[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$HealthUrl = 'http://localhost:9000/health/ready',
    [int]$TimeoutSeconds = 120,
    [switch]$Login
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$health = $HealthUrl.TrimEnd('/')
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

do {
    try {
        $ready = Invoke-RestMethod -Uri $health -TimeoutSec 5
        if ($ready.status -eq 'UP') { break }
    } catch { }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

if ($ready.status -ne 'UP') { throw "Keycloak health/ready did not become UP within $TimeoutSeconds seconds" }
Write-Output 'PASS: Keycloak readiness UP'

$discovery = Invoke-RestMethod -Uri "$base/realms/nexus-poc/.well-known/openid-configuration" -TimeoutSec 10
if ($discovery.issuer -ne "$base/realms/nexus-poc") { throw 'OIDC issuer mismatch' }
if (-not $discovery.authorization_endpoint -or -not $discovery.token_endpoint -or -not $discovery.jwks_uri) {
    throw 'OIDC discovery lacks required endpoints'
}
Write-Output 'PASS: OIDC discovery issuer and endpoints'

if ($Login) {
    if (-not $env:P0_03_SMOKE_USERNAME -or -not $env:P0_03_SMOKE_PASSWORD) {
        throw 'Set P0_03_SMOKE_USERNAME and P0_03_SMOKE_PASSWORD in the current process before -Login'
    }
    $token = Invoke-RestMethod -Method Post -Uri $discovery.token_endpoint -ContentType 'application/x-www-form-urlencoded' -Body @{
        grant_type = 'password'
        client_id = 'p0-smoke'
        username = $env:P0_03_SMOKE_USERNAME
        password = $env:P0_03_SMOKE_PASSWORD
        scope = 'openid'
    } -TimeoutSec 15
    if (-not $token.access_token -or -not $token.id_token) { throw 'Direct-grant smoke returned no access or ID token' }
    $userInfo = Invoke-RestMethod -Uri $discovery.userinfo_endpoint -Headers @{ Authorization = "Bearer $($token.access_token)" } -TimeoutSec 15
    if ($userInfo.preferred_username -ne $env:P0_03_SMOKE_USERNAME) { throw 'UserInfo subject username mismatch' }
    Write-Output 'PASS: local test user login and UserInfo'
    Write-Output 'NOTE: direct grant is only a smoke path; authorization code + PKCE requires separate browser verification'
}

Write-Output 'DONE: only the checks printed as PASS were executed'
