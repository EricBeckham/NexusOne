#!/usr/bin/env bash
# G10 leftover: Keycloak 8080 identity reconcile drift-fix dry-run.
# Detect + proposed fix. Default never applies P2 / IdP writes.
# Existing apply evidence stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh.
# No Docker. Does not claim SAML / LDAP / SMS MFA. Does not redo file reconcile.
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

if [[ "${NEXUS_G10_APPLY:-}" == "1" || "${NEXUS_G10_APPLY:-}" == "true" ]]; then
  echo "REFUSE: this leftover is detect + proposed-fix dry-run (no writes)." >&2
  echo "Apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh" >&2
  exit 2
fi

WORK="${G10_DRIFT_WORK:-${ROOT}/.local/g10-identity-drift-dry-run}"
mkdir -p "${WORK}"
chmod 700 "${WORK}" || true
REPORT="${WORK}/report.env"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

kv() {
  printf '%s=%s\n' "$1" "$2" | tee -a "${REPORT}"
}

: >"${REPORT}"
kv leftover g10-identity-drift-dry-run
kv recorded_at "${STAMP}"
kv java_version "$(java -version 2>&1 | head -n1 | tr -d '"')"
kv mvn_version "$(mvn -version | head -n1)"
kv boot "4.1.1"
kv apply_default false
kv file_reconcile_redone false
kv saml_claimed false
kv ldap_claimed false
kv sms_mfa_claimed false
kv g18_reopened false

echo "== G10 leftover always-green dry-run (Stub IdP, no Keycloak writes) =="
mvn -pl nexus-p2-iam -am test \
  -Dtest=PersonIdentityDriftDryRunTest,PersonIdentityReconcileTest,IdentityApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
kv stub_dry_run PASS

if [[ "${NEXUS_G10_DRIFT_SKIP_LIVE:-}" == "1" ]]; then
  kv live_keycloak_8080 SKIP
  kv live_skip_reason "NEXUS_G10_DRIFT_SKIP_LIVE=1"
  echo "NEXUS_G10_DRIFT_SKIP_LIVE=1 — not starting Keycloak"
  exit 0
fi

export KEYCLOAK_HTTP_PORT="${KEYCLOAK_HTTP_PORT:-8080}"
export KEYCLOAK_MGMT_PORT="${KEYCLOAK_MGMT_PORT:-9000}"
export NEXUS_KEYCLOAK_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-http://127.0.0.1:${KEYCLOAK_HTTP_PORT}/realms/nexus}"
export NEXUS_KEYCLOAK_BASE="${NEXUS_KEYCLOAK_BASE:-http://127.0.0.1:${KEYCLOAK_HTTP_PORT}}"
ENV_FILE="${KEYCLOAK_SMOKE_ENV_FILE:-/tmp/keycloak-smoke.env}"
if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET:-}" && -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

kc_up() {
  curl -fsS "${1}/.well-known/openid-configuration" >/dev/null 2>&1
}

LIVE_STATUS=SKIP
LIVE_REASON="Keycloak OIDC discovery not reachable on ${NEXUS_KEYCLOAK_ISSUER}"
if kc_up "${NEXUS_KEYCLOAK_ISSUER}"; then
  LIVE_STATUS=READY
  LIVE_REASON="already serving ${NEXUS_KEYCLOAK_ISSUER}"
  echo "Keycloak already serving ${NEXUS_KEYCLOAK_ISSUER}"
else
  KC_VER="${KEYCLOAK_VERSION:-26.5.2}"
  KC_HOME="${KEYCLOAK_HOME:-${HOME}/keycloak-${KC_VER}}"
  export KEYCLOAK_HOME="${KC_HOME}"
  if [[ ! -x "${KC_HOME}/bin/kc.sh" ]]; then
    kv live_keycloak_8080 SKIP
    kv live_skip_reason "Keycloak ${KC_VER} not found at ${KC_HOME}; leftover does not download IdP media"
    echo "SKIP live Keycloak 8080: ${KC_HOME}/bin/kc.sh missing"
    exit 0
  fi
  echo "Keycloak not on ${NEXUS_KEYCLOAK_ISSUER}; starting native 26.5.2 on :${KEYCLOAK_HTTP_PORT} (no Docker)"
  # shellcheck disable=SC1091
  source "${ROOT}/nexus-p2-iam/scripts/start-native-keycloak.sh"
  if kc_up "${NEXUS_KEYCLOAK_ISSUER}"; then
    LIVE_STATUS=READY
    LIVE_REASON="started native Keycloak on ${NEXUS_KEYCLOAK_ISSUER}"
  fi
fi

if [[ "${LIVE_STATUS}" != "READY" ]]; then
  kv live_keycloak_8080 SKIP
  kv live_skip_reason "${LIVE_REASON}"
  echo "SKIP live Keycloak 8080: ${LIVE_REASON}"
  exit 0
fi

export NEXUS_KEYCLOAK_SMOKE=1
echo "== live Keycloak 26.5.2 :8080 drift-fix dry-run (apply=false) =="
mvn -pl nexus-p2-iam -am test \
  -Dtest=LiveKeycloakIdentityDriftDryRunTest \
  -Dsurefire.failIfNoSpecifiedTests=false
kv live_keycloak_8080 PASS
kv live_issuer "${NEXUS_KEYCLOAK_ISSUER}"
kv live_apply false
echo "PASS: live dry-run proposed SYNC_P2 and left stale P2 email in place"
echo "See docs/g10-identity-drift-dry-run.md"
