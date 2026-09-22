#!/usr/bin/env bash
# Native Keycloak 26.5.2 HTTPS loopback (no Docker). Isolated data dir so an
# HTTP smoke on :8180 can stay running. Secrets stay in /tmp (0600), not git.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KC_VER="${KEYCLOAK_VERSION:-26.5.2}"
KC_DIST="${KEYCLOAK_HOME:-/home/ubuntu/keycloak-${KC_VER}}"
KC_HOME="${KEYCLOAK_HTTPS_HOME:-/tmp/keycloak-${KC_VER}-https}"
KC_HTTP_PORT="${KEYCLOAK_HTTP_PORT:-8181}"
KC_HTTPS_PORT="${KEYCLOAK_HTTPS_PORT:-8543}"
KC_MGMT_PORT="${KEYCLOAK_MGMT_PORT:-9001}"
PRODUCT_HTTPS_PORT="${NEXUS_HTTPS_PORT:-18443}"
KC_HTTP_BASE="http://127.0.0.1:${KC_HTTP_PORT}"
KC_HTTPS_BASE="https://127.0.0.1:${KC_HTTPS_PORT}"
ISSUER="${NEXUS_KEYCLOAK_ISSUER:-${KC_HTTPS_BASE}/realms/nexus}"
CLIENT_ID="${NEXUS_KEYCLOAK_CLIENT_ID:-nexus-platform}"
PKCE_CLIENT_ID="${NEXUS_KEYCLOAK_PKCE_CLIENT_ID:-nexus-web}"
SMOKE_USER="${NEXUS_KEYCLOAK_SMOKE_USERNAME:-carol}"
SMOKE_EMAIL="${NEXUS_KEYCLOAK_SMOKE_EMAIL:-carol@example.com}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-kcadmin}"
DB_NAME="${KC_DB_NAME:-keycloak_https}"
DB_USER="${KC_DB_USERNAME:-ubuntu}"
ENV_FILE="${KEYCLOAK_HTTPS_ENV_FILE:-/tmp/keycloak-https.env}"
CERT_ENV="${NEXUS_G4_HTTPS_ENV_FILE:-/tmp/g4-https.env}"

if [[ -z "${NEXUS_G4_HTTPS_CERT:-}" && -f "${CERT_ENV}" ]]; then
  # shellcheck disable=SC1090
  source "${CERT_ENV}"
fi
if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET:-}" && -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

export KC_BOOTSTRAP_ADMIN_USERNAME="${ADMIN_USER}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-}"
export NEXUS_KEYCLOAK_CLIENT_SECRET="${NEXUS_KEYCLOAK_CLIENT_SECRET:-}"
export NEXUS_KEYCLOAK_SMOKE_PASSWORD="${NEXUS_KEYCLOAK_SMOKE_PASSWORD:-}"

log() { printf '%s\n' "$*"; }

if [[ ! -x "${KC_DIST}/bin/kc.sh" ]]; then
  log "ERROR: Keycloak ${KC_VER} not found at ${KC_DIST}. Download the official tar.gz first."
  exit 1
fi
if [[ -z "${NEXUS_G4_HTTPS_CERT:-}" || -z "${NEXUS_G4_HTTPS_KEY:-}" ]]; then
  log "ERROR: generate loopback certs first (scripts/g4-pkce-https-certs.sh) and source ${CERT_ENV}"
  exit 1
fi
if [[ ! -f "${NEXUS_G4_HTTPS_CERT}" || ! -f "${NEXUS_G4_HTTPS_KEY}" ]]; then
  log "ERROR: missing ${NEXUS_G4_HTTPS_CERT} or ${NEXUS_G4_HTTPS_KEY}"
  exit 1
fi

if [[ ! -x "${KC_HOME}/bin/kc.sh" ]]; then
  mkdir -p "${KC_HOME}"
  for item in bin lib providers themes; do
    if [[ -e "${KC_DIST}/${item}" ]]; then
      ln -sfn "${KC_DIST}/${item}" "${KC_HOME}/${item}"
    fi
  done
  cp -a "${KC_DIST}/conf" "${KC_HOME}/conf"
fi
mkdir -p "${KC_HOME}/data/import"

CURL_HTTPS=(curl -fsS --cacert "${NEXUS_G4_HTTPS_CERT}")
kc_https_up() {
  "${CURL_HTTPS[@]}" "${KC_HTTPS_BASE}/realms/nexus/.well-known/openid-configuration" >/dev/null 2>&1
}

ALREADY_UP=0
if kc_https_up; then
  ALREADY_UP=1
  ISSUER="${KC_HTTPS_BASE}/realms/nexus"
  log "Keycloak already serving ${ISSUER}"
  if [[ -z "${NEXUS_KEYCLOAK_CLIENT_SECRET}" || -z "${NEXUS_KEYCLOAK_SMOKE_PASSWORD}" || -z "${KC_BOOTSTRAP_ADMIN_PASSWORD}" ]]; then
    log "ERROR: HTTPS Keycloak is already running; source ${ENV_FILE} or export secrets."
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

  python3 - <<PY
import json, os, pathlib
src = pathlib.Path("${ROOT}/nexus-p2-iam/src/main/resources/keycloak/nexus-realm.json")
realm = json.loads(src.read_text())
secret = os.environ["NEXUS_KEYCLOAK_CLIENT_SECRET"]
https_cb = [
    "https://127.0.0.1:${PRODUCT_HTTPS_PORT}/api/v1/auth/callback",
    "https://127.0.0.1:8443/api/v1/auth/callback",
    "https://127.0.0.1:18088/api/v1/auth/callback",
]
https_origin = [
    "https://127.0.0.1:${PRODUCT_HTTPS_PORT}",
    "https://127.0.0.1:8443",
    "https://127.0.0.1:18088",
]
for client in realm.get("clients", []):
    if client.get("clientId") == "${CLIENT_ID}":
        client["secret"] = secret
    uris = list(client.get("redirectUris") or [])
    origins = list(client.get("webOrigins") or [])
    for item in https_cb:
        if item not in uris:
            uris.append(item)
    for item in https_origin:
        if item not in origins:
            origins.append(item)
    client["redirectUris"] = uris
    client["webOrigins"] = origins
path = pathlib.Path("${KC_HOME}/data/import/nexus-realm.json")
path.write_text(json.dumps(realm, indent=2) + "\n")
print(f"wrote {path} (secret redacted from this log)")
PY

  log "Starting Keycloak ${KC_VER} HTTPS ${KC_HTTPS_BASE} HTTP-admin ${KC_HTTP_BASE} mgmt ${KC_MGMT_PORT}"
  nohup "${KC_HOME}/bin/kc.sh" start-dev \
    --http-port="${KC_HTTP_PORT}" \
    --http-enabled=true \
    --https-port="${KC_HTTPS_PORT}" \
    --https-certificate-file="${NEXUS_G4_HTTPS_CERT}" \
    --https-certificate-key-file="${NEXUS_G4_HTTPS_KEY}" \
    --hostname="${KC_HTTPS_BASE}" \
    --hostname-strict=true \
    --health-enabled=true \
    --http-management-port="${KC_MGMT_PORT}" \
    --import-realm \
    >"${KC_HOME}/keycloak-https.log" 2>&1 &
  echo $! >"${KC_HOME}/keycloak-https.pid"
  log "Keycloak HTTPS pid $(cat "${KC_HOME}/keycloak-https.pid") (bundled H2; not the product Keycloak-on-PG path)"
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
export NEXUS_KEYCLOAK_BASE='${KC_HTTPS_BASE}'
export NEXUS_KEYCLOAK_HTTPS=1
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_SMOKE_USERNAME='${SMOKE_USER}'
export NEXUS_KEYCLOAK_SMOKE_EMAIL='${SMOKE_EMAIL}'
export NEXUS_HTTPS_PORT='${PRODUCT_HTTPS_PORT}'
export NEXUS_KEYCLOAK_REDIRECT_URI='https://127.0.0.1:${PRODUCT_HTTPS_PORT}/api/v1/auth/callback'
EOF
if [[ -f "${CERT_ENV}" ]]; then
  # shellcheck disable=SC1090
  source "${CERT_ENV}"
  cat >>"${ENV_FILE}" <<EOF
export NEXUS_G4_HTTPS_DIR='${NEXUS_G4_HTTPS_DIR}'
export NEXUS_G4_HTTPS_KEYSTORE='${NEXUS_G4_HTTPS_KEYSTORE}'
export NEXUS_G4_HTTPS_TRUSTSTORE='${NEXUS_G4_HTTPS_TRUSTSTORE}'
export NEXUS_G4_HTTPS_CERT='${NEXUS_G4_HTTPS_CERT}'
export NEXUS_G4_HTTPS_KEY='${NEXUS_G4_HTTPS_KEY}'
export NEXUS_G4_HTTPS_STORE_PASSWORD='${NEXUS_G4_HTTPS_STORE_PASSWORD}'
EOF
fi
chmod 600 "${ENV_FILE}"
log "Wrote ${ENV_FILE} (mode 0600; not for git)"

for i in $(seq 1 90); do
  if kc_https_up; then
    log "Keycloak HTTPS ready after ${i}s (already_up=${ALREADY_UP})"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    log "ERROR: Keycloak HTTPS did not become ready. Last log lines:"
    tail -n 80 "${KC_HOME}/keycloak-https.log" || true
    exit 1
  fi
  sleep 2
done

"${CURL_HTTPS[@]}" "${ISSUER}/.well-known/openid-configuration" | python3 -c \
  'import json,sys; d=json.load(sys.stdin); print("issuer", d.get("issuer")); print("authorization_endpoint", d.get("authorization_endpoint")); print("jwks_uri", d.get("jwks_uri"))'

"${KC_DIST}/bin/kcadm.sh" config credentials \
  --server "${KC_HTTP_BASE}" --realm master \
  --user "${ADMIN_USER}" --password "${KC_BOOTSTRAP_ADMIN_PASSWORD}" >/dev/null

"${KC_DIST}/bin/kcadm.sh" update realms/nexus -s sslRequired=external >/dev/null 2>&1 || true

"${KC_DIST}/bin/kcadm.sh" add-roles -r nexus \
  --uusername "service-account-${CLIENT_ID}" \
  --cclientid realm-management \
  --rolename manage-users --rolename view-users --rolename query-users --rolename view-clients \
  >/dev/null 2>&1 || log "service-account roles already present or add-roles skipped"

CLIENT_UUID="$("${KC_DIST}/bin/kcadm.sh" get clients -r nexus -q "clientId=${CLIENT_ID}" --fields id \
  | python3 -c "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')")"
SCOPE_JSON="$("${KC_DIST}/bin/kcadm.sh" get client-scopes -r nexus --fields id,name)"
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
    "${KC_DIST}/bin/kcadm.sh" update "clients/${client_uuid}/default-client-scopes/${scope_uuid}" -r nexus -n \
      >/dev/null 2>&1 || true
  fi
}
for SCOPE_NAME in basic profile email roles web-origins acr; do
  attach_scope "${CLIENT_UUID}" "${SCOPE_NAME}"
done

add_https_redirects() {
  local uuid="$1"
  [[ -z "${uuid}" ]] && return 0
  "${KC_DIST}/bin/kcadm.sh" update "clients/${uuid}" -r nexus \
    -s "redirectUris=[\"http://127.0.0.1:18088/api/v1/auth/callback\",\"http://127.0.0.1:8088/api/v1/auth/callback\",\"http://127.0.0.1:8080/api/v1/auth/callback\",\"http://127.0.0.1:5173/callback\",\"https://127.0.0.1:${PRODUCT_HTTPS_PORT}/api/v1/auth/callback\",\"https://127.0.0.1:8443/api/v1/auth/callback\",\"https://127.0.0.1:18088/api/v1/auth/callback\"]" \
    -s "webOrigins=[\"http://127.0.0.1:18088\",\"http://127.0.0.1:8088\",\"http://127.0.0.1:8080\",\"http://127.0.0.1:5173\",\"https://127.0.0.1:${PRODUCT_HTTPS_PORT}\",\"https://127.0.0.1:8443\",\"https://127.0.0.1:18088\"]" \
    >/dev/null 2>&1 || true
}
add_https_redirects "${CLIENT_UUID}"

PKCE_UUID="$("${KC_DIST}/bin/kcadm.sh" get clients -r nexus -q "clientId=${PKCE_CLIENT_ID}" --fields id \
  | python3 -c "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')" || true)"
if [[ -z "${PKCE_UUID}" ]]; then
  "${KC_DIST}/bin/kcadm.sh" create clients -r nexus \
    -s clientId="${PKCE_CLIENT_ID}" \
    -s name="NexusOne public browser PKCE client" \
    -s enabled=true \
    -s publicClient=true \
    -s standardFlowEnabled=true \
    -s implicitFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s serviceAccountsEnabled=false \
    -s "redirectUris=[\"https://127.0.0.1:${PRODUCT_HTTPS_PORT}/api/v1/auth/callback\",\"https://127.0.0.1:5173/callback\"]" \
    -s "webOrigins=[\"https://127.0.0.1:${PRODUCT_HTTPS_PORT}\",\"https://127.0.0.1:5173\"]" \
    -s 'attributes."pkce.code.challenge.method"=S256' >/dev/null
  PKCE_UUID="$("${KC_DIST}/bin/kcadm.sh" get clients -r nexus -q "clientId=${PKCE_CLIENT_ID}" --fields id \
    | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")"
  log "created public client ${PKCE_CLIENT_ID} (${PKCE_UUID})"
else
  add_https_redirects "${PKCE_UUID}"
  log "public client ${PKCE_CLIENT_ID} already present (${PKCE_UUID})"
fi
for SCOPE_NAME in basic profile email roles web-origins acr; do
  attach_scope "${PKCE_UUID}" "${SCOPE_NAME}"
done

USER_ID="$("${KC_DIST}/bin/kcadm.sh" get users -r nexus -q "username=${SMOKE_USER}" --fields id,username 2>/dev/null | python3 -c \
  "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')" || true)"
if [[ -z "${USER_ID}" ]]; then
  "${KC_DIST}/bin/kcadm.sh" create users -r nexus \
    -s username="${SMOKE_USER}" \
    -s email="${SMOKE_EMAIL}" \
    -s enabled=true \
    -s emailVerified=true \
    -s firstName=Carol \
    -s lastName=Smoke >/dev/null
  USER_ID="$("${KC_DIST}/bin/kcadm.sh" get users -r nexus -q "username=${SMOKE_USER}" --fields id | python3 -c \
    "import json,sys; print(json.load(sys.stdin)[0]['id'])")"
fi
"${KC_DIST}/bin/kcadm.sh" set-password -r nexus --userid "${USER_ID}" \
  --new-password "${NEXUS_KEYCLOAK_SMOKE_PASSWORD}" >/dev/null

log "realm nexus sslRequired=$("${KC_DIST}/bin/kcadm.sh" get realms/nexus --fields sslRequired | python3 -c 'import json,sys; print(json.load(sys.stdin).get("sslRequired"))')"
log "smoke user ${SMOKE_USER} id ${USER_ID}"

export NEXUS_KEYCLOAK_ISSUER="${ISSUER}"
export NEXUS_KEYCLOAK_CLIENT_ID="${CLIENT_ID}"
export NEXUS_KEYCLOAK_PKCE_CLIENT_ID="${PKCE_CLIENT_ID}"
export NEXUS_KEYCLOAK_BASE="${KC_HTTPS_BASE}"
export NEXUS_KEYCLOAK_HTTPS=1
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_SMOKE_USERNAME="${SMOKE_USER}"
export NEXUS_KEYCLOAK_SMOKE_EMAIL="${SMOKE_EMAIL}"
export NEXUS_KEYCLOAK_REDIRECT_URI="https://127.0.0.1:${PRODUCT_HTTPS_PORT}/api/v1/auth/callback"
export NEXUS_KEYCLOAK_CLIENT_SECRET
export NEXUS_KEYCLOAK_SMOKE_PASSWORD
export KC_BOOTSTRAP_ADMIN_PASSWORD

log "Native Keycloak HTTPS ready issuer=${ISSUER}"
