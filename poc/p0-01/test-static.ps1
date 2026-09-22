$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$checker = Join-Path $root 'check-static.ps1'
$fixtures = Join-Path $root 'test\fixtures'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -SourceRoot (Join-Path $fixtures 'positive') -InfrastructureRoot (Join-Path $fixtures 'positive\infrastructure')
if ($LASTEXITCODE -ne 0) { throw 'positive static fixture was rejected' }
$cases = @('jdbc','spring','jpa','mybatis')
foreach ($case in $cases) {
  # Expected violations are written to stderr. Continue so that stderr does not abort before the exit code is checked.
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -SourceRoot (Join-Path $fixtures "negative\$case") 2>&1 | Out-Null
  $exitCode = $LASTEXITCODE
  $ErrorActionPreference = $previous
  if ($exitCode -eq 0) { throw "negative static fixture passed: $case" }
  Write-Host "PASS static rejection: $case"
}
exit 0
