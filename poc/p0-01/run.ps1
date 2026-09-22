$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root 'build'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -encoding UTF-8 -d $out (Join-Path $root 'src\Poc.java') (Join-Path $root 'test\PocTest.java') (Join-Path $root 'test\PgIntegrationTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp $out PocTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $root 'test-static.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($env:PGDATABASE -and $env:PGUSER) {
    Push-Location $root
    try {
        mvn.cmd -q -f pom.xml dependency:copy-dependencies "-DoutputDirectory=$out"
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        java -cp "$out;$out/*" PgIntegrationTest
        exit $LASTEXITCODE
    } finally { Pop-Location }
}
Write-Host 'SKIP PostgreSQL integration: set PGDATABASE and PGUSER (plus PGHOST/PGPORT/PGPASSWORD as needed)'
exit 0
