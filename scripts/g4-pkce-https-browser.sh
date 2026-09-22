#!/usr/bin/env bash
# G4 leftover after #108: local self-signed HTTPS + cookie-honoring PKCE walk.
# Proves LOGIN on the hash chain. No Docker. Secrets stay in /tmp (0600).
# Does not claim a full G4 close. G18 stub path is unchanged.
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

echo "== always-green HTTPS cookie + stub PKCE slice (no Keycloak) =="
mvn -pl nexus-p2-iam -am test \
  -Dtest=AuthorizationCodeHttpsDriverTest,AuthorizationCodeDriverTest,PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "== generate loopback HTTPS material =="
"${ROOT}/scripts/g4-pkce-https-certs.sh"
# shellcheck disable=SC1091
source /tmp/g4-https.env

echo "== native Keycloak 26.x HTTPS (isolated home, no Docker) =="
# shellcheck disable=SC1091
source "${ROOT}/nexus-p2-iam/scripts/start-native-keycloak-https.sh"

export NEXUS_KEYCLOAK_HTTPS=1
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_G4_HTTPS_CHROME="${NEXUS_G4_HTTPS_CHROME:-1}"

echo "== live HTTPS PKCE → LOGIN on JDBC hash chain =="
set +e
mvn -pl nexus-p2-iam,nexus-bootstrap -am test \
  -Dtest=G4PkceHttpsBrowserIT,AuthorizationCodeHttpsDriverTest \
  -Dsurefire.failIfNoSpecifiedTests=false
LIVE_RC=$?
set -e
if [[ "${LIVE_RC}" -ne 0 ]]; then
  echo "BLOCKED: live HTTPS PKCE IT failed (exit ${LIVE_RC}). Partial progress: certs + Keycloak HTTPS start ran."
  echo "Keycloak HTTPS log tail:"
  tail -n 40 /tmp/keycloak-26.5.2-https/keycloak-https.log || true
  exit "${LIVE_RC}"
fi
echo "G4_HTTPS_PKCE_WALK_OK issuer=${NEXUS_KEYCLOAK_ISSUER}"
