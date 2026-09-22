#!/usr/bin/env bash
# S0 leftover: env/process secret rotate-without-restart evidence.
# Mutable-map env is re-read on next get. OS System.getenv() is a Java 21
# process-start snapshot and is honestly SKIP.
# Does not introduce OpenBao / KMS. Does not claim a product S0 close.
# Does not reopen G18. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== S0 SecretPort env/process rotate-without-restart"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "Contract:"
echo "  1. EnvSecretAdapter does not cache; mutable env map rotate is picked up"
echo "  2. LocalThenEnvSecretPort env path rotates when there is no overlay"
echo "  3. local overlay still wins after an env rotate"
echo "  4. System.getenv() OS rotate-without-restart is Java 21 snapshot — SKIP"
echo "  5. MinioObjectStorage.fromSecretPort snapshots credentials at construct"
echo "  6. no OpenBao / KMS; not a product S0 close; G18 stays closed"
echo

mvn -pl nexus-crypto-core,nexus-file-core -am test \
  -Dtest=SecretPortRotateWithoutRestartTest,EnvSecretAdapterTest,SecretPortTest,FileSecretPortWiringTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: env-map rotate-without-restart picked up; OS getenv snapshot SKIP"
echo "See docs/s0-secret-rotate-without-restart.md"
echo "Not claimed: product S0 close / OpenBao / KMS / OS env rotate without restart"
