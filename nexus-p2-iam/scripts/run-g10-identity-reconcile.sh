#!/usr/bin/env bash
# G10 identity half against native Keycloak 26.5.2 on loopback :8080 (no Docker).
# Starts Keycloak only when OIDC discovery is down and KEYCLOAK_HOME exists.
# Secrets stay in the environment / /tmp/keycloak-smoke.env (mode 0600).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export KEYCLOAK_HTTP_PORT="${KEYCLOAK_HTTP_PORT:-8080}"
export KEYCLOAK_MGMT_PORT="${KEYCLOAK_MGMT_PORT:-9000}"
export NEXUS_KEYCLOAK_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-http://127.0.0.1:${KEYCLOAK_HTTP_PORT}/realms/nexus}"
export NEXUS_KEYCLOAK_BASE="${NEXUS_KEYCLOAK_BASE:-http://127.0.0.1:${KEYCLOAK_HTTP_PORT}}"
export NEXUS_KEYCLOAK_SMOKE=1

ENV_FILE="${KEYCLOAK_SMOKE_ENV_FILE:-/tmp/keycloak-smoke.env}"
if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET:-}" && -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

if curl -fsS "${NEXUS_KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null 2>&1; then
  echo "Keycloak already serving ${NEXUS_KEYCLOAK_ISSUER}"
else
  echo "Keycloak not on ${NEXUS_KEYCLOAK_ISSUER}; using M0-04 native start helper (port ${KEYCLOAK_HTTP_PORT})"
  "${ROOT}/nexus-p2-iam/scripts/run-live-keycloak-smoke.sh"
  echo "M0-04 smoke finished; continuing with G10 person reconcile"
fi

cd "${ROOT}"
mvn -pl nexus-p2-iam -am test \
  -Dtest=LiveKeycloakPersonReconcileTest,PersonIdentityReconcileTest \
  -Dsurefire.failIfNoSpecifiedTests=false
