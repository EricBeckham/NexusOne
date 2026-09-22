#!/usr/bin/env bash
# G4 remainder: object-store / cold archive (Zonky PG 15 + CountingObjectStorage Fake).
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-common,nexus-audit-core,nexus-file-core,modules/isolation-kernel,nexus-bootstrap -am test \
  -Dtest=AuditColdArchiveJobTest,JdbcAuditColdArchiveIT,G4AuditColdArchiveJobTest,JdbcAuditChainTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false
