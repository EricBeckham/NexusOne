#!/usr/bin/env bash
# G4 leftover: native Keycloak 26.x Authorization Code + PKCE, then LOGIN on the hash chain.
# No Docker. Secrets stay in the environment / /tmp/keycloak-smoke.env (0600).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

if ! command -v mvn >/dev/null 2>&1; then
  if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
    export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
  fi
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn not on PATH" >&2
  exit 1
fi

# Always-green slice (stub IdP). Proves PKCE API + stub LOGIN on chain without Keycloak.
mvn -pl nexus-p2-iam -am test \
  -Dtest=PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest,AuthorizationCodeDriverTest,SessionLoginAuditTest,IdentityApiTest,AdminMfaApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Native Keycloak 26.x (H2 if PostgreSQL is not listening).
# shellcheck disable=SC1091
source "${ROOT}/nexus-p2-iam/scripts/start-native-keycloak.sh"

export NEXUS_KEYCLOAK_SMOKE=1
mvn -pl nexus-p2-iam,nexus-bootstrap -am test \
  -Dtest=LiveKeycloakPkceLoginAuditTest,G4PkceLoginAuditIT,AuthorizationCodeDriverTest \
  -Dsurefire.failIfNoSpecifiedTests=false
