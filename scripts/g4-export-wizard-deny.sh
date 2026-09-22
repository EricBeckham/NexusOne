#!/usr/bin/env bash
# G4 leftover after #152: HTTP export wizard complete-deny on the hash chain.
# Does not change AuditPort same-TX. Does not claim product G4 closed.
# Does not redo #152 begin/complete happy path as a product close.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v mvn >/dev/null 2>&1; then
  if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
    export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
  fi
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn not on PATH" >&2
  exit 1
fi

echo "== G4 leftover: HTTP export wizard complete-deny =="
mvn -pl nexus-audit-core,nexus-permission-core,nexus-p3-studio,nexus-bootstrap -am test \
  -Dtest=G4ExportWizardDenyAuditTest,ExportWizardApiTest,G4ExportWizardDenyIT,JdbcAuditChainTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false

if command -v npm >/dev/null 2>&1 && [[ -f web/package.json ]]; then
  (cd web && npm run assert:g4-export-wizard-deny && npm run test:g4-export-wizard)
fi

echo "See docs/g4-export-wizard-deny.md"
echo "Not claimed: product G4 close / P1 GET off staging / AuditPort same-TX rewrite"
