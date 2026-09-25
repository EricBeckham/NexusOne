# Build nexus-bootstrap JAR and print SHA-256 (M6 / P6).
# Does NOT deploy. Does NOT write secrets. Template ≠ deployed.
#
# Checklist: docs/engineering/runbooks/demo-host-prod-deployment.md
# Env template: docs/engineering/runbooks/demo-host-prod.env.template
#
# Usage (from repo root, Eric-Desktop / Cursor worktree):
#   powershell -File scripts/demo-host/build-bootstrap-jar-sha.ps1
# Optional:
#   -SkipTests   (default) already skips tests
#   -MavenCmd    path to mvn.cmd if not on PATH

param(
    [string]$MavenCmd = "",
    [switch]$KeepGoingOnDirty
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root

if (-not $KeepGoingOnDirty) {
    $dirty = git status --porcelain 2>$null
    if ($dirty) {
        Write-Host "Working tree has local changes. Re-run with -KeepGoingOnDirty to build anyway."
        Write-Host "Prefer building a clean checkout of the release SHA (P6 §0)."
    }
}

$sha = (git rev-parse HEAD).Trim()
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
Write-Host "git HEAD: $sha ($branch)"
Write-Host "NOTE: template/script ≠ demo host deployed. No secrets in this script."

if (-not $MavenCmd) {
    $candidates = @(
        "D:\tools\apache-maven-3.9.11\bin\mvn.cmd",
        "$env:USERPROFILE\apache-maven\bin\mvn.cmd"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $MavenCmd = $c; break }
    }
}
if (-not $MavenCmd) {
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { $MavenCmd = $cmd.Source }
}
if (-not $MavenCmd -or -not (Test-Path $MavenCmd)) {
    throw "mvn.cmd not found. Pass -MavenCmd or install Maven 3.9.x."
}

Write-Host "Maven: $MavenCmd"
& $MavenCmd -q -pl nexus-bootstrap -am -DskipTests package
if ($LASTEXITCODE) { exit $LASTEXITCODE }

$jar = Join-Path $Root "nexus-bootstrap\target\nexus-bootstrap-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    throw "JAR missing: $jar"
}

$hash = (Get-FileHash -Algorithm SHA256 -Path $jar).Hash.ToLowerInvariant()
$len = (Get-Item $jar).Length
Write-Host ""
Write-Host "JAR:      $jar"
Write-Host "SIZE:     $len bytes"
Write-Host "SHA-256:  $hash"
Write-Host "GIT_SHA:  $sha"
Write-Host ""
Write-Host "Record GIT_SHA + SHA-256 with the release; re-hash on the demo host after transfer (P6 §0)."
Write-Host "This script does not copy the JAR to any remote host."
