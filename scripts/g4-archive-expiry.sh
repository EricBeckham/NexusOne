#!/usr/bin/env bash
# G4 remainder: 365-day archive expiry / purge (Zonky PG 15).
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-common,nexus-audit-core,modules/isolation-kernel,nexus-bootstrap -am test \
  -Dtest=AuditArchiveJobTest,AuditFlywayContractTest,JdbcAuditChainTransactionTest,JdbcAuditArchiveIT,G4AuditArchiveJobTest \
  -Dsurefire.failIfNoSpecifiedTests=false
