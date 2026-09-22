#!/usr/bin/env bash
# Native Keycloak 26.5.2 start helper (no Docker). Secrets from the environment only.
# Does not echo passwords or client secrets. Writes /tmp/keycloak-smoke.env (0600).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
KC_VER="${KEYCLOAK_VERSION:-26.5.2}"
KC_HOME="${KEYCLOAK_HOME:-/home/ubuntu/keycloak-${KC_VER}}"
KC_HTTP_PORT="${KEYCLOAK_HTTP_PORT:-8180}"
KC_MGMT_PORT="${KEYCLOAK_MGMT_PORT:-9000}"
KC_BASE="http://127.0.0.1:${KC_HTTP_PORT}"
ISSUER="${NEXUS_KEYCLOAK_ISSUER:-${KC_BASE}/realms/nexus}"
CLIENT_ID="${NEXUS_KEYCLOAK_CLIENT_ID:-nexus-platform}"
PKCE_CLIENT_ID="${NEXUS_KEYCLOAK_PKCE_CLIENT_ID:-nexus-web}"
SMOKE_USER="${NEXUS_KEYCLOAK_SMOKE_USERNAME:-carol}"
SMOKE_EMAIL="${NEXUS_KEYCLOAK_SMOKE_EMAIL:-carol@example.com}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-kcadmin}"
DB_NAME="${KC_DB_NAME:-keycloak}"
DB_USER="${KC_DB_USERNAME:-ubuntu}"
ENV_FILE="${KEYCLOAK_SMOKE_ENV_FILE:-/tmp/keycloak-smoke.env}"

if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET:-}" && -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

export KC_BOOTSTRAP_ADMIN_USERNAME="${ADMIN_USER}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-}"
export NEXUS_KEYCLOAK_CLIENT_SECRET="${NEXUS_KEYCLOAK_CLIENT_SECRET:-}"
export NEXUS_KEYCLOAK_SMOKE_PASSWORD="${NEXUS_KEYCLOAK_SMOKE_PASSWORD:-}"

log() { printf '%s\n' "$*"; }

kc_up() {
  local base="$1"
  curl -fsS "${base}/realms/nexus/.well-known/openid-configuration" >/dev/null 2>&1
}

if [[ ! -x "${KC_HOME}/bin/kc.sh" ]]; then
  log "ERROR: Keycloak ${KC_VER} not found at ${KC_HOME}. Download the official tar.gz first."
  exit 1
fi

log "Using Keycloak home ${KC_HOME}"
"${KC_HOME}/bin/kc.sh" --version | head -n 3

ALREADY_UP=0
if kc_up "${KC_BASE}"; then
  ALREADY_UP=1
  ISSUER="${KC_BASE}/realms/nexus"
elif kc_up "http://127.0.0.1:8080"; then
  ALREADY_UP=1
  KC_HTTP_PORT=8080
  KC_BASE="http://127.0.0.1:8080"
  ISSUER="${KC_BASE}/realms/nexus"
  KC_MGMT_PORT="${KEYCLOAK_MGMT_PORT:-9000}"
fi

if [[ "${ALREADY_UP}" -eq 1 ]]; then
  log "Keycloak already serving ${ISSUER}"
  if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET}" || -z "${NEXUS_KEYCLOAK_SMOKE_PASSWORD}" || -z "${KC_BOOTSTRAP_ADMIN_PASSWORD}" ]]; then
    log "ERROR: Keycloak is already running; source ${ENV_FILE} or export NEXUS_KEYCLOAK_CLIENT_SECRET, NEXUS_KEYCLOAK_SMOKE_PASSWORD, KC_BOOTSTRAP_ADMIN_PASSWORD."
    exit 1
  fi
else
  if [[ -z "${KC_BOOTSTRAP_ADMIN_PASSWORD}" ]]; then
    KC_BOOTSTRAP_ADMIN_PASSWORD="$(openssl rand -hex 16)"
    export KC_BOOTSTRAP_ADMIN_PASSWORD
  fi
  if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET}" ]]; then
    NEXUS_KEYCLOAK_CLIENT_SECRET="$(openssl rand -hex 24)"
    export NEXUS_KEYCLOAK_CLIENT_SECRET
  fi
  if [[ -z "${NEXUS_KEYCLOAK_SMOKE_PASSWORD}" ]]; then
    NEXUS_KEYCLOAK_SMOKE_PASSWORD="$(openssl rand -hex 12)"
    export NEXUS_KEYCLOAK_SMOKE_PASSWORD
  fi

  IMPORT_DIR="${KC_HOME}/data/import"
  mkdir -p "${IMPORT_DIR}"
  python3 - <<PY
import json, os, pathlib
src = pathlib.Path("${ROOT}/nexus-p2-iam/src/main/resources/keycloak/nexus-realm.json")
realm = json.loads(src.read_text())
secret = os.environ["NEXUS_KEYCLOAK_CLIENT_SECRET"]
for client in realm.get("clients", []):
    if client.get("clientId") == "${CLIENT_ID}":
        client["secret"] = secret
path = pathlib.Path("${IMPORT_DIR}/nexus-realm.json")
path.write_text(json.dumps(realm, indent=2) + "\n")
print(f"wrote {path} (secret redacted from this log)")
PY

  USE_PG=0
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
    USE_PG=1
    if command -v createdb >/dev/null 2>&1; then
      createdb "${DB_NAME}" 2>/dev/null || true
    fi
    if [[ -z "${KC_DB_PASSWORD:-}" ]]; then
      export KC_DB_PASSWORD="${KC_DB_PASSWORD:-unused}"
    fi
    log "Starting Keycloak ${KC_VER} on ${KC_BASE} (management ${KC_MGMT_PORT}) with PostgreSQL database ${DB_NAME}"
    nohup "${KC_HOME}/bin/kc.sh" start-dev \
      --http-port="${KC_HTTP_PORT}" \
      --http-enabled=true \
      --hostname=127.0.0.1 \
      --hostname-strict=false \
      --health-enabled=true \
      --http-management-port="${KC_MGMT_PORT}" \
      --db=postgres \
      --db-url="jdbc:postgresql://127.0.0.1:5432/${DB_NAME}" \
      --db-username="${DB_USER}" \
      --db-password="${KC_DB_PASSWORD}" \
      --import-realm \
      >"${KC_HOME}/keycloak-smoke.log" 2>&1 &
  else
    log "PostgreSQL 15 is not listening; Keycloak start-dev bundled H2 (not the product Keycloak-on-PG path)"
    log "Starting Keycloak ${KC_VER} on ${KC_BASE} (management ${KC_MGMT_PORT}) with H2"
    nohup "${KC_HOME}/bin/kc.sh" start-dev \
      --http-port="${KC_HTTP_PORT}" \
      --http-enabled=true \
      --hostname=127.0.0.1 \
      --hostname-strict=false \
      --health-enabled=true \
      --http-management-port="${KC_MGMT_PORT}" \
      --import-realm \
      >"${KC_HOME}/keycloak-smoke.log" 2>&1 &
  fi
  echo $! >"${KC_HOME}/keycloak-smoke.pid"
  log "Keycloak pid $(cat "${KC_HOME}/keycloak-smoke.pid") postgres=${USE_PG}"
fi

umask 077
cat >"${ENV_FILE}" <<EOF
export KC_BOOTSTRAP_ADMIN_USERNAME='${ADMIN_USER}'
export KC_BOOTSTRAP_ADMIN_PASSWORD='${KC_BOOTSTRAP_ADMIN_PASSWORD}'
export NEXUS_KEYCLOAK_CLIENT_SECRET='${NEXUS_KEYCLOAK_CLIENT_SECRET}'
export NEXUS_KEYCLOAK_SMOKE_PASSWORD='${NEXUS_KEYCLOAK_SMOKE_PASSWORD}'
export NEXUS_KEYCLOAK_ISSUER='${ISSUER}'
export NEXUS_KEYCLOAK_CLIENT_ID='${CLIENT_ID}'
export NEXUS_KEYCLOAK_PKCE_CLIENT_ID='${PKCE_CLIENT_ID}'
export NEXUS_KEYCLOAK_BASE='${KC_BASE}'
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_SMOKE_USERNAME='${SMOKE_USER}'
export NEXUS_KEYCLOAK_SMOKE_EMAIL='${SMOKE_EMAIL}'
EOF
chmod 600 "${ENV_FILE}"
log "Wrote ${ENV_FILE} (mode 0600; not for git)"

for i in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${KC_MGMT_PORT}/health/ready" >/dev/null 2>&1 \
     || curl -fsS "${KC_BASE}/realms/nexus/.well-known/openid-configuration" >/dev/null 2>&1; then
    log "Keycloak ready after ${i}s (already_up=${ALREADY_UP})"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    log "ERROR: Keycloak did not become ready. Last log lines:"
    tail -n 80 "${KC_HOME}/keycloak-smoke.log" || true
    exit 1
  fi
  sleep 2
done

curl -fsS "${KC_BASE}/realms/nexus/.well-known/openid-configuration" | python3 -c \
  'import json,sys; d=json.load(sys.stdin); print("issuer", d.get("issuer")); print("authorization_endpoint", d.get("authorization_endpoint")); print("jwks_uri", d.get("jwks_uri"))'

"${KC_HOME}/bin/kcadm.sh" config credentials \
  --server "${KC_BASE}" --realm master \
  --user "${ADMIN_USER}" --password "${KC_BOOTSTRAP_ADMIN_PASSWORD}" >/dev/null

"${KC_HOME}/bin/kcadm.sh" update realms/nexus -s sslRequired=none >/dev/null 2>&1 || true

"${KC_HOME}/bin/kcadm.sh" add-roles -r nexus \
  --uusername "service-account-${CLIENT_ID}" \
  --cclientid realm-management \
  --rolename manage-users --rolename view-users --rolename query-users --rolename view-clients \
  --rolename manage-realm --rolename view-realm \
  >/dev/null 2>&1 || log "service-account roles already present or add-roles skipped"

CLIENT_UUID="$("${KC_HOME}/bin/kcadm.sh" get clients -r nexus -q "clientId=${CLIENT_ID}" --fields id \
  | python3 -c "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')")"
SCOPE_JSON="$("${KC_HOME}/bin/kcadm.sh" get client-scopes -r nexus --fields id,name)"
attach_scope() {
  local client_uuid="$1"
  local scope_name="$2"
  local scope_uuid
  scope_uuid="$(printf '%s' "${SCOPE_JSON}" | python3 -c "import json,sys
name='${scope_name}'
for scope in json.load(sys.stdin):
    if scope.get('name')==name:
        print(scope['id']); break
")"
  if [[ -n "${client_uuid}" && -n "${scope_uuid}" ]]; then
    "${KC_HOME}/bin/kcadm.sh" update "clients/${client_uuid}/default-client-scopes/${scope_uuid}" -r nexus -n \
      >/dev/null 2>&1 || true
  fi
}
for SCOPE_NAME in basic profile email roles web-origins acr; do
  attach_scope "${CLIENT_UUID}" "${SCOPE_NAME}"
done
log "attached default client scopes basic/profile/email/roles/web-origins/acr to ${CLIENT_ID} (${CLIENT_UUID})"

PKCE_UUID="$("${KC_HOME}/bin/kcadm.sh" get clients -r nexus -q "clientId=${PKCE_CLIENT_ID}" --fields id \
  | python3 -c "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')" || true)"
if [[ -z "${PKCE_UUID}" ]]; then
  "${KC_HOME}/bin/kcadm.sh" create clients -r nexus \
    -s clientId="${PKCE_CLIENT_ID}" \
    -s name="NexusOne public browser PKCE client" \
    -s enabled=true \
    -s publicClient=true \
    -s standardFlowEnabled=true \
    -s implicitFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s serviceAccountsEnabled=false \
    -s 'redirectUris=["http://127.0.0.1:18088/api/v1/auth/callback","http://127.0.0.1:8088/api/v1/auth/callback","http://127.0.0.1:8080/api/v1/auth/callback","http://127.0.0.1:5173/callback"]' \
    -s 'webOrigins=["http://127.0.0.1:18088","http://127.0.0.1:8088","http://127.0.0.1:8080","http://127.0.0.1:5173"]' \
    -s 'attributes."pkce.code.challenge.method"=S256' >/dev/null
  PKCE_UUID="$("${KC_HOME}/bin/kcadm.sh" get clients -r nexus -q "clientId=${PKCE_CLIENT_ID}" --fields id \
    | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")"
  log "created public client ${PKCE_CLIENT_ID} (${PKCE_UUID})"
else
  log "public client ${PKCE_CLIENT_ID} already present (${PKCE_UUID})"
fi
for SCOPE_NAME in basic profile email roles web-origins acr; do
  attach_scope "${PKCE_UUID}" "${SCOPE_NAME}"
done

USER_ID="$("${KC_HOME}/bin/kcadm.sh" get users -r nexus -q "username=${SMOKE_USER}" --fields id,username 2>/dev/null | python3 -c \
  "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')" || true)"
if [[ -z "${USER_ID}" ]]; then
  "${KC_HOME}/bin/kcadm.sh" create users -r nexus \
    -s username="${SMOKE_USER}" \
    -s email="${SMOKE_EMAIL}" \
    -s enabled=true \
    -s emailVerified=true \
    -s firstName=Carol \
    -s lastName=Smoke >/dev/null
  USER_ID="$("${KC_HOME}/bin/kcadm.sh" get users -r nexus -q "username=${SMOKE_USER}" --fields id | python3 -c \
    "import json,sys; print(json.load(sys.stdin)[0]['id'])")"
fi
"${KC_HOME}/bin/kcadm.sh" set-password -r nexus --userid "${USER_ID}" \
  --new-password "${NEXUS_KEYCLOAK_SMOKE_PASSWORD}" >/dev/null

log "realm nexus sslRequired=$("${KC_HOME}/bin/kcadm.sh" get realms/nexus --fields sslRequired | python3 -c 'import json,sys; print(json.load(sys.stdin).get("sslRequired"))')"
log "smoke user ${SMOKE_USER} id ${USER_ID}"
"${KC_HOME}/bin/kcadm.sh" get clients -r nexus --fields id,clientId,publicClient,standardFlowEnabled,directAccessGrantsEnabled,serviceAccountsEnabled \
  | python3 -c 'import json,sys
for c in json.load(sys.stdin):
    if c.get("clientId") in ("nexus-platform", "nexus-web"):
        print("client", c.get("clientId"), "id", c.get("id"),
              "publicClient", c.get("publicClient"),
              "standardFlow", c.get("standardFlowEnabled"),
              "directGrant", c.get("directAccessGrantsEnabled"),
              "serviceAccounts", c.get("serviceAccountsEnabled"))'

export NEXUS_KEYCLOAK_ISSUER="${ISSUER}"
export NEXUS_KEYCLOAK_CLIENT_ID="${CLIENT_ID}"
export NEXUS_KEYCLOAK_PKCE_CLIENT_ID="${PKCE_CLIENT_ID}"
export NEXUS_KEYCLOAK_BASE="${KC_BASE}"
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_SMOKE_USERNAME="${SMOKE_USER}"
export NEXUS_KEYCLOAK_SMOKE_EMAIL="${SMOKE_EMAIL}"
export NEXUS_KEYCLOAK_CLIENT_SECRET
export NEXUS_KEYCLOAK_SMOKE_PASSWORD
export KC_BOOTSTRAP_ADMIN_PASSWORD

log "Native Keycloak ready issuer=${ISSUER}"
