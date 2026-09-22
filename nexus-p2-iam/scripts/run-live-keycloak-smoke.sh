#!/usr/bin/env bash
# Native Keycloak 26.5.2 + live nexus-p2-iam OIDC smoke (no Docker).
# Secrets from the environment only. Do not echo passwords or client secrets.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KC_VER="${KEYCLOAK_VERSION:-26.5.2}"
KC_HOME="${KEYCLOAK_HOME:-/home/ubuntu/keycloak-${KC_VER}}"
KC_HTTP_PORT="${KEYCLOAK_HTTP_PORT:-8180}"
KC_MGMT_PORT="${KEYCLOAK_MGMT_PORT:-9000}"
KC_BASE="http://127.0.0.1:${KC_HTTP_PORT}"
ISSUER="${NEXUS_KEYCLOAK_ISSUER:-${KC_BASE}/realms/nexus}"
CLIENT_ID="${NEXUS_KEYCLOAK_CLIENT_ID:-nexus-platform}"
SMOKE_USER="${NEXUS_KEYCLOAK_SMOKE_USERNAME:-carol}"
SMOKE_EMAIL="${NEXUS_KEYCLOAK_SMOKE_EMAIL:-carol@example.com}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-kcadmin}"
DB_NAME="${KC_DB_NAME:-keycloak}"
DB_USER="${KC_DB_USERNAME:-ubuntu}"
ENV_FILE="${KEYCLOAK_SMOKE_ENV_FILE:-/tmp/keycloak-smoke.env}"

if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET:-}" && -f "${ENV_FILE}" ]]; then
  # Reuse secrets from a previous native start on this VM. File mode should be 0600.
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

export KC_BOOTSTRAP_ADMIN_USERNAME="${ADMIN_USER}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-}"
export NEXUS_KEYCLOAK_CLIENT_SECRET="${NEXUS_KEYCLOAK_CLIENT_SECRET:-}"
export NEXUS_KEYCLOAK_SMOKE_PASSWORD="${NEXUS_KEYCLOAK_SMOKE_PASSWORD:-}"

log() { printf '%s\n' "$*"; }

kc_up() {
  curl -fsS "${KC_BASE}/realms/nexus/.well-known/openid-configuration" >/dev/null 2>&1
}

if [[ ! -x "${KC_HOME}/bin/kc.sh" ]]; then
  log "ERROR: Keycloak ${KC_VER} not found at ${KC_HOME}. Download the official tar.gz first."
  exit 1
fi

log "Using Keycloak home ${KC_HOME}"
"${KC_HOME}/bin/kc.sh" --version | head -n 3

ALREADY_UP=0
if kc_up; then
  ALREADY_UP=1
  log "Keycloak already serving ${ISSUER}"
  if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET}" ]]; then
    log "ERROR: Keycloak is already running; export NEXUS_KEYCLOAK_CLIENT_SECRET from the original start (or source ${ENV_FILE})."
    exit 1
  fi
  if [[ -z "${NEXUS_KEYCLOAK_SMOKE_PASSWORD}" ]]; then
    log "ERROR: export NEXUS_KEYCLOAK_SMOKE_PASSWORD from the original start (or source ${ENV_FILE})."
    exit 1
  fi
  if [[ -z "${KC_BOOTSTRAP_ADMIN_PASSWORD}" ]]; then
    log "ERROR: export KC_BOOTSTRAP_ADMIN_PASSWORD from the original start (or source ${ENV_FILE})."
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
  if [[ -z "${KC_DB_PASSWORD:-}" ]]; then
    # Local trust for ubuntu on 127.0.0.1; Keycloak still requires a non-empty JDBC password field.
    export KC_DB_PASSWORD="${KC_DB_PASSWORD:-unused}"
  fi

  createdb "${DB_NAME}" 2>/dev/null || true

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

  log "Starting Keycloak ${KC_VER} on ${KC_BASE} (management ${KC_MGMT_PORT}) with PostgreSQL database ${DB_NAME}"
  log "Start method: native process, nohup ${KC_HOME}/bin/kc.sh start-dev (not Docker; not a target Profile)"
  # Native process, start-dev, health on management port. --hostname-port is not a 26.5.2 flag.
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
  echo $! >"${KC_HOME}/keycloak-smoke.pid"
  log "Keycloak pid $(cat "${KC_HOME}/keycloak-smoke.pid")"
fi

umask 077
cat >"${ENV_FILE}" <<EOF
export KC_BOOTSTRAP_ADMIN_USERNAME='${ADMIN_USER}'
export KC_BOOTSTRAP_ADMIN_PASSWORD='${KC_BOOTSTRAP_ADMIN_PASSWORD}'
export NEXUS_KEYCLOAK_CLIENT_SECRET='${NEXUS_KEYCLOAK_CLIENT_SECRET}'
export NEXUS_KEYCLOAK_SMOKE_PASSWORD='${NEXUS_KEYCLOAK_SMOKE_PASSWORD}'
export NEXUS_KEYCLOAK_ISSUER='${ISSUER}'
export NEXUS_KEYCLOAK_CLIENT_ID='${CLIENT_ID}'
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

# Admin CLI: assign service-account roles and ensure smoke user (password from env).
"${KC_HOME}/bin/kcadm.sh" config credentials \
  --server "${KC_BASE}" --realm master \
  --user "${ADMIN_USER}" --password "${KC_BOOTSTRAP_ADMIN_PASSWORD}" >/dev/null

"${KC_HOME}/bin/kcadm.sh" update realms/nexus -s sslRequired=none >/dev/null 2>&1 || true

"${KC_HOME}/bin/kcadm.sh" add-roles -r nexus \
  --uusername "service-account-${CLIENT_ID}" \
  --cclientid realm-management \
  --rolename manage-users --rolename view-users --rolename query-users --rolename view-clients \
  >/dev/null 2>&1 || log "service-account roles already present or add-roles skipped"

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
    if c.get("clientId")=="nexus-platform":
        print("client", c.get("clientId"), "id", c.get("id"),
              "publicClient", c.get("publicClient"),
              "standardFlow", c.get("standardFlowEnabled"),
              "directGrant", c.get("directAccessGrantsEnabled"),
              "serviceAccounts", c.get("serviceAccountsEnabled"))'

export NEXUS_KEYCLOAK_ISSUER="${ISSUER}"
export NEXUS_KEYCLOAK_CLIENT_ID="${CLIENT_ID}"
export NEXUS_KEYCLOAK_BASE="${KC_BASE}"
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_SMOKE_USERNAME="${SMOKE_USER}"
export NEXUS_KEYCLOAK_SMOKE_EMAIL="${SMOKE_EMAIL}"

log "Running LiveKeycloakSmokeTest (Stub IdP off)"
cd "${ROOT}"
mvn -pl nexus-p2-iam -am test -Dtest=LiveKeycloakSmokeTest,AuthorizationCodeDriverTest -Dsurefire.failIfNoSpecifiedTests=false
