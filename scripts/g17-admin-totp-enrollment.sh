#!/usr/bin/env bash
# G17 leftover: admin TOTP enrollment + live Keycloak OTP challenge.
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

echo "== always-green stub / driver slice (no Keycloak) =="
mvn -pl nexus-p2-iam -am test \
  -Dtest=TotpTest,AuthorizationCodeDriverTest,KeycloakAdminSupportTest,G18StubAdminLoginTest,AdminMfaApiTest,AdminMfaSessionTest,MfaClaimsTest,IdentityApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

if [[ "${NEXUS_G17_TOTP_SKIP_LIVE:-}" == "1" ]]; then
  echo "NEXUS_G17_TOTP_SKIP_LIVE=1 — not starting Keycloak"
  exit 0
fi

KC_VER="${KEYCLOAK_VERSION:-26.5.2}"
KC_HOME="${KEYCLOAK_HOME:-${HOME}/keycloak-${KC_VER}}"
export KEYCLOAK_HOME="${KC_HOME}"
if [[ ! -x "${KC_HOME}/bin/kc.sh" ]]; then
  echo "ERROR: Keycloak ${KC_VER} not found at ${KC_HOME}. Download the official tar.gz first." >&2
  exit 1
fi

# Native Keycloak 26.x (H2 if PostgreSQL is not listening).
# shellcheck disable=SC1091
source "${ROOT}/nexus-p2-iam/scripts/start-native-keycloak.sh"

# shellcheck disable=SC1091
source "${ROOT}/nexus-p2-iam/scripts/configure-keycloak-admin-totp.sh"

export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_ADMIN_TOTP_USERNAME="${NEXUS_KEYCLOAK_ADMIN_TOTP_USERNAME:-tenant-admin-totp}"
export NEXUS_KEYCLOAK_ADMIN_TOTP_EMAIL="${NEXUS_KEYCLOAK_ADMIN_TOTP_EMAIL:-tenant-admin-totp@example.com}"
if [[ -z "${NEXUS_KEYCLOAK_ADMIN_TOTP_PASSWORD:-}" ]]; then
  NEXUS_KEYCLOAK_ADMIN_TOTP_PASSWORD="${NEXUS_KEYCLOAK_SMOKE_PASSWORD}"
  export NEXUS_KEYCLOAK_ADMIN_TOTP_PASSWORD
fi

echo "== live Keycloak 26 admin TOTP walk =="
mvn -pl nexus-p2-iam -am test \
  -Dtest=LiveKeycloakAdminTotpTest,G18StubAdminLoginTest,AuthorizationCodeDriverTest \
  -Dsurefire.failIfNoSpecifiedTests=false
