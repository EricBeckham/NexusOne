param(
  [ValidateRange(1,1000)][int]$TenantCount = 10,
  [ValidateRange(0,100)][int]$CustomPerTenant = 1,
  [ValidateRange(1,100)][int]$SharedTables = 2
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$psql = if ($env:PSQL_EXE) { $env:PSQL_EXE } else { 'D:\Software\PostgreSQL\15\bin\psql.exe' }
if (-not (Test-Path -LiteralPath $psql)) { throw "psql not found: $psql; set PSQL_EXE" }
if (-not $env:PGDATABASE -or -not $env:PGUSER) { throw 'Set PGDATABASE and PGUSER (plus PGHOST/PGPORT/PGPASSWORD as needed)' }
New-Item -ItemType Directory -Force -Path (Join-Path $root 'results') | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = Join-Path $root "results/bench-$stamp.log"
Push-Location $root
try {
  & $psql -X -v ON_ERROR_STOP=1 -v "tenant_count=$TenantCount" -v "custom_per_tenant=$CustomPerTenant" -v "shared_tables=$SharedTables" -f 'contract.sql' 2>&1 | Tee-Object -FilePath $log
  if ($LASTEXITCODE -ne 0) { throw 'Contract failed; see log' }
  & $psql -X -v ON_ERROR_STOP=1 -v "tenant_count=$TenantCount" -v "custom_per_tenant=$CustomPerTenant" -v "shared_tables=$SharedTables" -f 'bench.sql' 2>&1 | Tee-Object -FilePath $log -Append
  if ($LASTEXITCODE -ne 0) { throw 'Benchmark failed; see log' }
  Write-Host "Benchmark log: $log"
} finally { Pop-Location }
