$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    mvn.cmd -q -DskipTests package dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $cp = "target/classes;" + (Get-Content 'target/classpath.txt' -Raw).Trim()
    java -cp $cp ContractTest
    exit $LASTEXITCODE
} finally { Pop-Location }
