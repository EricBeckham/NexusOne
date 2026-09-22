param(
  [Parameter(Mandatory=$true)][string]$SourceRoot,
  [string]$InfrastructureRoot
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $SourceRoot -PathType Container)) { throw "SourceRoot missing: $SourceRoot" }
$source = (Resolve-Path -LiteralPath $SourceRoot).Path.TrimEnd('\','/')
$infra = $null
if ($InfrastructureRoot) {
  if (-not (Test-Path -LiteralPath $InfrastructureRoot -PathType Container)) { throw "InfrastructureRoot missing: $InfrastructureRoot" }
  $infra = (Resolve-Path -LiteralPath $InfrastructureRoot).Path.TrimEnd('\','/')
  if (-not $infra.StartsWith($source + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'InfrastructureRoot must be inside SourceRoot'
  }
}
$rules = [ordered]@{
  'direct JDBC' = 'java\.sql\.(Connection|DriverManager|Statement|PreparedStatement)|\b(prep(?:areStatement)?|createStatement)\s*\('
  'Spring JDBC' = '\b(JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient)\b'
  'JPA native SQL' = '\bcreateNativeQuery\s*\(|\bnativeQuery\s*=\s*true\b'
  'MyBatis literal substitution' = '\$\{[^}]+\}'
}
$violations = @()
Get-ChildItem -LiteralPath $source -Recurse -File | Where-Object { $_.Extension -in '.java','.xml' } | ForEach-Object {
  $file = $_
  if ($infra -and $file.FullName.StartsWith($infra + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { return }
  $content = Get-Content -LiteralPath $file.FullName -Raw
  foreach ($rule in $rules.Keys) {
    if ($content -match $rules[$rule]) { $violations += "$($file.FullName): $rule" }
  }
}
if ($violations.Count -gt 0) {
  $violations | ForEach-Object { Write-Error $_ -ErrorAction Continue }
  exit 1
}
Write-Host "PASS static bypass check: $source"
