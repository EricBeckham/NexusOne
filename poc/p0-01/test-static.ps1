$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$checker = Join-Path $root 'check-static.ps1'
$fixtures = Join-Path $root 'test\fixtures'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -SourceRoot (Join-Path $fixtures 'positive') -InfrastructureRoot (Join-Path $fixtures 'positive\infrastructure')
if ($LASTEXITCODE -ne 0) { throw 'positive static fixture was rejected' }
$cases = @('jdbc','spring','jpa','mybatis')
foreach ($case in $cases) {
  $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -SourceRoot (Join-Path $fixtures "negative\$case") 2>&1
  if ($LASTEXITCODE -eq 0) { throw "negative static fixture passed: $case" }
  Write-Host "PASS static rejection: $case"
}
exit 0
