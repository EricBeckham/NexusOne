#!/usr/bin/env bash
# G10 file half: always-on counting storage, plus optional live MinIO on dedicated ports.
# No Docker. Does not bind product PostgreSQL :5432 for the live slice.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"

echo "G10 file reconcile: counting / memory fixtures (always)"
mvn -pl nexus-file-core,nexus-bootstrap -am test \
  -Dtest=FileObjectReconcileTest,FileReconcileApiTest,FileReconcileTaskHandlerTest,FileServiceIsolationTest,FileApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

if [[ -n "${S3_ENDPOINT:-}" && -n "${S3_ACCESS_KEY:-}" && -n "${S3_SECRET_KEY:-}" ]]; then
  echo "G10 file reconcile: live MinIO at ${S3_ENDPOINT}"
  mvn -pl nexus-file-core -am test \
    -Dtest=LiveMinioFileReconcileOptionalTest \
    -Dsurefire.failIfNoSpecifiedTests=false
else
  echo "G10 file reconcile: live MinIO SKIP (set S3_ENDPOINT / S3_ACCESS_KEY / S3_SECRET_KEY; dedicated default :19000)"
fi
