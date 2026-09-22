$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root 'build'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -encoding UTF-8 -d $out (Join-Path $root 'src\Poc.java') (Join-Path $root 'test\PocTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp $out PocTest
exit $LASTEXITCODE
