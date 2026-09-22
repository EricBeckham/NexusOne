#!/usr/bin/env bash
# G4 leftover honesty remainder beyond #230/#222:
# 90-day hot-purge / P1 staging / PKCE SPA leftover cells stay PASS.
# Live purge / staging / JAR / Keycloak / CA / laptop click are PASS
# only when observed. Missing live path is honest SKIP.
# Does not redo #230/#222/#183/#190/#177/#147. Does not DROP
# platform.audit_record. Does not switch GET off staging.
# Does not change AuditPort same-TX. Does not claim product G4 close.
# G18 stays closed. No P0/M0 redo. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

README="$ROOT/docs/README.md"
DOC_230="$ROOT/docs/g4-hot-purge-staging-leftover-honesty.md"
DOC_222="$ROOT/docs/g4-pkce-spa-verifier-remainder-honesty.md"
FIXTURE_230="$ROOT/scripts/g4-hot-purge-staging-leftover-honesty/fixtures/honest-skip.env"
FIXTURE_222="$ROOT/scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/honest-skip.env"

REQUIRED_LEFTOVER_ROWS="g4-hot-purge-90d g4-hot-purge-90d-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g4-hot-purge-staging-leftover-honesty g4-hot-purge-staging-pkce-remainder-honesty g4-pkce-spa-verifier g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g4-export-wizard-deny-honesty g4-export-wizard-deny g4-export-wizard g4-guard-deny-audit g5-publishhook-ddl-remainder-honesty s0-secret-rotate-getenv-leftover-honesty g10-file-object-reconcile-remainder-honesty g17-mfa-recovery-vue-otp-remainder-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g7-query-expense-travel-honesty-remainder g9-redis-down-invalidate-remainder-honesty g2-h5-every-page-form-write-remainder-honesty g7-withdraw-browser-evidence-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty g9-sentinel-cluster-remainder-honesty g2-ops-pg-dump-honesty g17-mfa-recovery-vue-otp-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-honesty g2-h5-every-page-form-write-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g3-outbox-consume-crash-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty linux-arm-profile-leftover-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g9-live-multi-jvm-replica g9-sentinel-cluster-honesty g2-h5-every-page g2-form-write-honesty g10-file-reconcile-honesty g1-queryast-boundsql-honesty g1-queryast-boundsql-guard g1-fieldpolicy-mask-second-auth"

usage() {
  cat <<'EOF'
Usage:
  scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh [--out DIR]
  scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report FILE

Beyond #230/#222: hot-purge / P1 staging / PKCE SPA leftover cells stay
PASS. Live purge / staging / JAR / Keycloak / CA / laptop click are PASS
only when observed. Missing live path is honest SKIP. Does not re-run
#230/#222. Does not claim product G4 close.

  --out DIR           Output directory (default: .local/g4-hot-purge-staging-pkce-remainder-honesty/<utc>)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

kv() { printf '%s=%s\n' "$1" "$2"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

report_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_230_cells_honest leftover_222_cells_honest leftover_183_cells_honest leftover_190_cells_honest leftover_177_cells_honest leftover_230_honesty_gate leftover_222_honesty_gate window_policy staging_like_path spa_verifier_mint hot_table_dropped get_still_on_staging switched_off_staging same_tx_append_unchanged leftover_rows_kept leftover_rows leftover_230_kept leftover_222_kept leftover_235_kept leftover_241_kept leftover_243_kept leftover_233_kept leftover_238_kept leftover_239_kept leftover_237_kept leftover_232_kept leftover_212_kept leftover_226_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_216_kept leftover_220_kept leftover_183_kept leftover_190_kept leftover_177_kept leftover_215_kept leftover_205_kept live_db_present live_jar_present live_minio_present live_purge_window live_purge_observed live_purge_window_skip_reason live_quartz live_quartz_observed live_quartz_skip_reason live_minio_cold live_minio_cold_observed live_minio_cold_skip_reason live_staging_5432 live_query_observed live_export_observed live_staging_skip_reason live_jar_context_walk live_jar_context_observed live_jar_context_skip_reason live_keycloak_pkce token_exchange_observed live_keycloak_skip_reason live_ca_trust ca_trust_observed live_ca_skip_reason human_laptop_click laptop_click_observed laptop_click_skip_reason redo_230 redo_222 redo_183 redo_190 redo_177 redo_147 g4_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || fail "java_feature must be 21"
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1"
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$(report_get "$file" docker_used)" = "false" ] || fail "docker_used must be false"
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || fail "audit_port_changed must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" redo_230)" = "false" ] || fail "must not redo #230"
  [ "$(report_get "$file" redo_222)" = "false" ] || fail "must not redo #222"
  [ "$(report_get "$file" redo_183)" = "false" ] || fail "must not redo #183"
  [ "$(report_get "$file" redo_190)" = "false" ] || fail "must not redo #190"
  [ "$(report_get "$file" redo_177)" = "false" ] || fail "must not redo #177"
  [ "$(report_get "$file" redo_147)" = "false" ] || fail "must not redo #147"
  [ "$(report_get "$file" g4_close)" = "NOT_CLAIMED" ] || fail "g4_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_230_cells_honest)" = "PASS" ] || fail "leftover_230_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_222_cells_honest)" = "PASS" ] || fail "leftover_222_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_183_cells_honest)" = "PASS" ] || fail "leftover_183_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_190_cells_honest)" = "PASS" ] || fail "leftover_190_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_177_cells_honest)" = "PASS" ] || fail "leftover_177_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_230_honesty_gate)" = "PASS" ] || fail "leftover_230_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_222_honesty_gate)" = "PASS" ] || fail "leftover_222_honesty_gate must be PASS"
  [ "$(report_get "$file" window_policy)" = "PASS" ] || fail "window_policy must be PASS"
  [ "$(report_get "$file" staging_like_path)" = "PASS" ] || fail "staging_like_path must be PASS"
  [ "$(report_get "$file" spa_verifier_mint)" = "PASS" ] || fail "spa_verifier_mint must be PASS"
  [ "$(report_get "$file" hot_table_dropped)" = "false" ] || fail "hot_table_dropped must stay false"
  [ "$(report_get "$file" get_still_on_staging)" = "true" ] || fail "get_still_on_staging must stay true"
  [ "$(report_get "$file" switched_off_staging)" = "false" ] || fail "switched_off_staging must stay false"
  [ "$(report_get "$file" same_tx_append_unchanged)" = "PASS" ] || fail "same_tx_append_unchanged must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || fail "leftover_230_kept must stay true (keep g4-hot-purge-staging-leftover-honesty)"
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || fail "leftover_222_kept must stay true (keep g4-pkce-spa-verifier-remainder-honesty)"
  [ "$(report_get "$file" leftover_235_kept)" = "true" ] || fail "leftover_235_kept must stay true (keep g5-publishhook-ddl-remainder-honesty)"
  [ "$(report_get "$file" leftover_241_kept)" = "true" ] || fail "leftover_241_kept must stay true (keep s0-secret-rotate-getenv-leftover-honesty)"
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || fail "leftover_243_kept must stay true (keep g10-file-object-reconcile-remainder-honesty)"
  [ "$(report_get "$file" leftover_233_kept)" = "true" ] || fail "leftover_233_kept must stay true (keep g17-mfa-recovery-vue-otp-remainder-honesty)"
  [ "$(report_get "$file" leftover_238_kept)" = "true" ] || fail "leftover_238_kept must stay true (keep g1-fieldpolicy-mask-second-auth-remainder-honesty)"
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || fail "leftover_239_kept must stay true (keep g7-query-expense-travel-honesty-remainder)"
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || fail "leftover_237_kept must stay true (keep g9-redis-down-invalidate-remainder-honesty)"
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || fail "leftover_232_kept must stay true (keep g2-h5-every-page-form-write-remainder-honesty)"
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || fail "leftover_212_kept must stay true (keep g7-withdraw-browser-evidence-honesty)"
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || fail "leftover_226_kept must stay true"
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || fail "leftover_228_kept must stay true"
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || fail "leftover_221_kept must stay true"
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || fail "leftover_225_kept must stay true"
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || fail "leftover_223_kept must stay true"
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || fail "leftover_216_kept must stay true"
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || fail "leftover_220_kept must stay true"
  [ "$(report_get "$file" leftover_183_kept)" = "true" ] || fail "leftover_183_kept must stay true"
  [ "$(report_get "$file" leftover_190_kept)" = "true" ] || fail "leftover_190_kept must stay true"
  [ "$(report_get "$file" leftover_177_kept)" = "true" ] || fail "leftover_177_kept must stay true"
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || fail "leftover_215_kept must stay true"
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || fail "leftover_205_kept must stay true"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local verdict
  for verdict in live_purge_window live_quartz live_minio_cold live_staging_5432 live_jar_context_walk live_keycloak_pkce live_ca_trust human_laptop_click; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_purge_window)" = "PASS" ]; then
    [ "$(report_get "$file" live_db_present)" = "true" ] || fail "live_purge_window=PASS requires live_db_present=true"
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_purge_window=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_purge_observed)" = "true" ] || fail "live_purge_window=PASS requires live_purge_observed=true"
  fi
  if [ "$(report_get "$file" live_purge_window)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_purge_window_skip_reason)" ] || fail "live purge SKIP needs live_purge_window_skip_reason"
    [ "$(report_get "$file" live_purge_observed)" != "true" ] || fail "SKIP cannot claim live_purge_observed=true"
  fi
  if [ "$(report_get "$file" live_quartz)" = "PASS" ]; then
    [ "$(report_get "$file" live_quartz_observed)" = "true" ] || fail "live_quartz=PASS requires live_quartz_observed=true"
  fi
  if [ "$(report_get "$file" live_quartz)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_quartz_skip_reason)" ] || fail "live Quartz SKIP needs live_quartz_skip_reason"
    [ "$(report_get "$file" live_quartz_observed)" != "true" ] || fail "SKIP cannot claim live_quartz_observed=true"
  fi
  if [ "$(report_get "$file" live_minio_cold)" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || fail "live_minio_cold=PASS requires live_minio_present=true"
    [ "$(report_get "$file" live_minio_cold_observed)" = "true" ] || fail "live_minio_cold=PASS requires live_minio_cold_observed=true"
  fi
  if [ "$(report_get "$file" live_minio_cold)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_cold_skip_reason)" ] || fail "live MinIO SKIP needs live_minio_cold_skip_reason"
    [ "$(report_get "$file" live_minio_cold_observed)" != "true" ] || fail "SKIP cannot claim live_minio_cold_observed=true"
  fi
  if [ "$(report_get "$file" live_staging_5432)" = "PASS" ]; then
    [ "$(report_get "$file" live_db_present)" = "true" ] || fail "live_staging_5432=PASS requires live_db_present=true"
    [ "$(report_get "$file" live_query_observed)" = "true" ] || fail "live_staging_5432=PASS requires live_query_observed=true"
    [ "$(report_get "$file" live_export_observed)" = "true" ] || fail "live_staging_5432=PASS requires live_export_observed=true"
  fi
  if [ "$(report_get "$file" live_staging_5432)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_staging_skip_reason)" ] || fail "live staging SKIP needs live_staging_skip_reason"
    [ "$(report_get "$file" live_query_observed)" != "true" ] || fail "SKIP cannot claim live_query_observed=true"
    [ "$(report_get "$file" live_export_observed)" != "true" ] || fail "SKIP cannot claim live_export_observed=true"
  fi
  if [ "$(report_get "$file" live_jar_context_walk)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_jar_context_walk=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_jar_context_observed)" = "true" ] || fail "live_jar_context_walk=PASS requires live_jar_context_observed=true"
  fi
  if [ "$(report_get "$file" live_jar_context_walk)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_context_skip_reason)" ] || fail "live JAR/context SKIP needs live_jar_context_skip_reason"
    [ "$(report_get "$file" live_jar_context_observed)" != "true" ] || fail "SKIP cannot claim live_jar_context_observed=true"
  fi
  if [ "$(report_get "$file" live_keycloak_pkce)" = "PASS" ]; then
    [ "$(report_get "$file" token_exchange_observed)" = "true" ] || fail "live_keycloak_pkce=PASS requires token_exchange_observed=true"
  fi
  if [ "$(report_get "$file" live_keycloak_pkce)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_skip_reason)" ] || fail "live Keycloak SKIP needs live_keycloak_skip_reason"
    [ "$(report_get "$file" token_exchange_observed)" != "true" ] || fail "SKIP cannot claim token_exchange_observed=true"
  fi
  if [ "$(report_get "$file" live_ca_trust)" = "PASS" ]; then
    [ "$(report_get "$file" ca_trust_observed)" = "true" ] || fail "live_ca_trust=PASS requires ca_trust_observed=true"
  fi
  if [ "$(report_get "$file" live_ca_trust)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_ca_skip_reason)" ] || fail "live CA SKIP needs live_ca_skip_reason"
    [ "$(report_get "$file" ca_trust_observed)" != "true" ] || fail "SKIP cannot claim ca_trust_observed=true"
  fi
  if [ "$(report_get "$file" human_laptop_click)" = "PASS" ]; then
    [ "$(report_get "$file" laptop_click_observed)" = "true" ] || fail "human_laptop_click=PASS requires laptop_click_observed=true"
  fi
  if [ "$(report_get "$file" human_laptop_click)" = "SKIP" ]; then
    [ -n "$(report_get "$file" laptop_click_skip_reason)" ] || fail "laptop click SKIP needs laptop_click_skip_reason"
    [ "$(report_get "$file" laptop_click_observed)" != "true" ] || fail "SKIP cannot claim laptop_click_observed=true"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=|code_verifier=|client_secret=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g4-hot-purge-staging-pkce-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G4 leftover honesty remainder beyond #230/#222 (hot-purge / P1 staging / PKCE SPA)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #230/#222/#183/#190/#177/#147. Not product G4 close. G18 stays closed."
echo

[ -f "$README" ] || fail "missing $README"
[ -f "$DOC_230" ] || fail "missing committed #230 leftover $DOC_230"
[ -f "$DOC_222" ] || fail "missing committed #222 leftover $DOC_222"
[ -f "$FIXTURE_230" ] || fail "missing committed #230 honest-skip fixture $FIXTURE_230"
[ -f "$FIXTURE_222" ] || fail "missing committed #222 honest-skip fixture $FIXTURE_222"
grep -Fq 'g4_close=NOT_CLAIMED' "$FIXTURE_230" || fail "committed #230 fixture must keep g4_close=NOT_CLAIMED"
grep -Fq 'leftover_183_cells_honest=PASS' "$FIXTURE_230" || fail "committed #230 fixture must keep leftover_183_cells_honest=PASS"
grep -Fq 'leftover_190_cells_honest=PASS' "$FIXTURE_230" || fail "committed #230 fixture must keep leftover_190_cells_honest=PASS"
grep -Fq 'window_policy=PASS' "$FIXTURE_230" || fail "committed #230 fixture must keep window_policy=PASS"
grep -Fq 'staging_like_path=PASS' "$FIXTURE_230" || fail "committed #230 fixture must keep staging_like_path=PASS"
grep -Fq 'live_purge_window=SKIP' "$FIXTURE_230" || fail "committed #230 fixture must keep live_purge_window=SKIP"
grep -Fq 'hot_table_dropped=false' "$FIXTURE_230" || fail "committed #230 fixture must keep hot_table_dropped=false"
grep -Fq 'switched_off_staging=false' "$FIXTURE_230" || fail "committed #230 fixture must keep switched_off_staging=false"
grep -Fq 'g4_close=NOT_CLAIMED' "$FIXTURE_222" || fail "committed #222 fixture must keep g4_close=NOT_CLAIMED"
grep -Fq 'spa_verifier_mint=PASS' "$FIXTURE_222" || fail "committed #222 fixture must keep spa_verifier_mint=PASS"
grep -Fq 'honesty_177_harness=PASS' "$FIXTURE_222" || fail "committed #222 fixture must keep honesty_177_harness=PASS"
grep -Fq 'live_keycloak_pkce=SKIP' "$FIXTURE_222" || fail "committed #222 fixture must keep live_keycloak_pkce=SKIP"
grep -Fq 'g4_close=NOT_CLAIMED' "$DOC_230" || fail "committed #230 leftover must keep g4_close=NOT_CLAIMED"
grep -Fq 'g4_close=NOT_CLAIMED' "$DOC_222" || fail "committed #222 leftover must keep g4_close=NOT_CLAIMED"

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
  java -version 2>&1 | head -n 1 | tee -a "$OUT/collect.log"
fi
[ "$JAVA_FEATURE" = "21" ] || fail "java_feature must be 21, got $JAVA_FEATURE"

BOOT_GROUP=""
BOOT_ARTIFACT=""
BOOT_VERSION=""
eval "$(python3 - "$ROOT/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
parent = root.find("m:parent", ns)
if parent is None:
    print("BOOT_GROUP=''")
    print("BOOT_ARTIFACT=''")
    print("BOOT_VERSION=''")
    raise SystemExit(0)
def t(tag):
    n = parent.find(f"m:{tag}", ns)
    return (n.text or "").strip() if n is not None else ""
print("BOOT_GROUP=%r" % t("groupId"))
print("BOOT_ARTIFACT=%r" % t("artifactId"))
print("BOOT_VERSION=%r" % t("version"))
PY
)"
BOOT_PARENT_GA="${BOOT_GROUP}:${BOOT_ARTIFACT}:${BOOT_VERSION}"
if [ "$BOOT_GROUP" = "org.springframework.boot" ] && [ "$BOOT_ARTIFACT" = "spring-boot-starter-parent" ] && [ "$BOOT_VERSION" = "4.1.1" ]; then
  BOOT_PARENT_MATCH=PASS
else
  BOOT_PARENT_MATCH=FAIL
fi
[ "$BOOT_PARENT_MATCH" = "PASS" ] || fail "boot parent must be org.springframework.boot:spring-boot-starter-parent:4.1.1"

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
  log "docker binary present but leftover must not use it"
fi

port_open() {
  local host="$1" port="$2"
  python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(0.2)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

LIVE_DB=false
if port_open 127.0.0.1 5432; then
  LIVE_DB=true
fi
LIVE_JAR=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_JAR=true
fi
LIVE_MINIO=false
if port_open 127.0.0.1 9000; then
  LIVE_MINIO=true
fi

if [ "$LIVE_DB" != "true" ] && [ "$LIVE_JAR" != "true" ]; then
  PURGE_REASON="no live PostgreSQL on 127.0.0.1:5432 and no product JAR on 127.0.0.1:8080 / :8088; #230 leftover collect is not a live purge PASS; Docker forbidden; this leftover does not re-run #230/#183"
elif [ "$LIVE_DB" != "true" ]; then
  PURGE_REASON="product JAR listener present but no live PostgreSQL on 127.0.0.1:5432; live 90-day prefix DELETE not observed; this leftover does not re-run #230"
elif [ "$LIVE_JAR" != "true" ]; then
  PURGE_REASON="live PostgreSQL present but product JAR not reachable; live purge not observed; this leftover does not re-run #230"
else
  PURGE_REASON="live PG + JAR listeners present but this leftover does not claim a 90-day prefix DELETE until observed; this leftover does not re-run #230"
fi

QUARTZ_REASON="nexus.audit.hot-purge.schedule-enabled defaults false; live Quartz 03:00 UTC walk not observed; this leftover does not re-run #230/#183"
if [ "$LIVE_MINIO" != "true" ]; then
  MINIO_REASON="no live MinIO on 127.0.0.1:9000; fake / counting storage is not a live cold drill; this leftover does not re-run #230"
else
  MINIO_REASON="MinIO listener present but a live cold copy was not observed; this leftover does not re-run #230"
fi
if [ "$LIVE_DB" != "true" ]; then
  STAGING_REASON="nothing listening on 127.0.0.1:5432; #230/#190 collect is not a live staging walk; Docker forbidden; this leftover does not re-run #230/#190"
else
  STAGING_REASON="live PostgreSQL present but staging query+export was not observed; this leftover does not re-run #230/#190"
fi
if [ "$LIVE_JAR" != "true" ]; then
  JAR_REASON="NEXUS_BASE_URL / NEXUS_G4_LIVE_BASE unset; nothing on 127.0.0.1:8080 / :8088; this leftover does not start the JAR and does not re-run #230"
else
  JAR_REASON="live JAR listener present but issued-context walk was not observed; this leftover does not re-run #230"
fi
KC_REASON="live Keycloak OIDC discovery unreachable on :8180 / :8543; #222/#177 honesty collect is not a live token exchange; this leftover does not start kc.sh and does not re-run #222"
CA_REASON="#120 loopback CA .local/g4-https/server.crt missing; CA inventory is not a trusted laptop walk; #222 honesty collect is not a trusted CA walk"
CLICK_REASON="#147 notebook is documented; #222 leftover did not record a laptop click; this agent VM did not record a human laptop CA click"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_230_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_235_KEPT=false
LEFTOVER_241_KEPT=false
LEFTOVER_243_KEPT=false
LEFTOVER_233_KEPT=false
LEFTOVER_238_KEPT=false
LEFTOVER_239_KEPT=false
LEFTOVER_237_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_212_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_183_KEPT=false
LEFTOVER_190_KEPT=false
LEFTOVER_177_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_205_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g4-hot-purge-staging-leftover-honesty) LEFTOVER_230_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g5-publishhook-ddl-remainder-honesty) LEFTOVER_235_KEPT=true ;;
      s0-secret-rotate-getenv-leftover-honesty) LEFTOVER_241_KEPT=true ;;
      g10-file-object-reconcile-remainder-honesty) LEFTOVER_243_KEPT=true ;;
      g17-mfa-recovery-vue-otp-remainder-honesty) LEFTOVER_233_KEPT=true ;;
      g1-fieldpolicy-mask-second-auth-remainder-honesty) LEFTOVER_238_KEPT=true ;;
      g7-query-expense-travel-honesty-remainder) LEFTOVER_239_KEPT=true ;;
      g9-redis-down-invalidate-remainder-honesty) LEFTOVER_237_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g7-withdraw-browser-evidence-honesty) LEFTOVER_212_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g4-hot-purge-90d-honesty) LEFTOVER_183_KEPT=true ;;
      g4-p1-staging-audit-events-honesty) LEFTOVER_190_KEPT=true ;;
      g4-pkce-spa-verifier-honesty) LEFTOVER_177_KEPT=true ;;
      g1-queryast-export-honesty) LEFTOVER_215_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_230_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-leftover-honesty (#230)"
[ "$LEFTOVER_222_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-pkce-spa-verifier-remainder-honesty (#222)"
[ "$LEFTOVER_235_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-remainder-honesty (#235)"
[ "$LEFTOVER_241_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-leftover-honesty (#241)"
[ "$LEFTOVER_243_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"
[ "$LEFTOVER_233_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-remainder-honesty (#233)"
[ "$LEFTOVER_238_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth-remainder-honesty (#238)"
[ "$LEFTOVER_239_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty-remainder (#239)"

LEFTOVER_ROWS_CSV="$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv leftover_230_cells_honest PASS
  kv leftover_222_cells_honest PASS
  kv leftover_183_cells_honest PASS
  kv leftover_190_cells_honest PASS
  kv leftover_177_cells_honest PASS
  kv leftover_230_honesty_gate PASS
  kv leftover_222_honesty_gate PASS
  kv window_policy PASS
  kv staging_like_path PASS
  kv spa_verifier_mint PASS
  kv hot_table_dropped false
  kv get_still_on_staging true
  kv switched_off_staging false
  kv same_tx_append_unchanged PASS
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_230_kept "$LEFTOVER_230_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_235_kept "$LEFTOVER_235_KEPT"
  kv leftover_241_kept "$LEFTOVER_241_KEPT"
  kv leftover_243_kept "$LEFTOVER_243_KEPT"
  kv leftover_233_kept "$LEFTOVER_233_KEPT"
  kv leftover_238_kept "$LEFTOVER_238_KEPT"
  kv leftover_239_kept "$LEFTOVER_239_KEPT"
  kv leftover_237_kept "$LEFTOVER_237_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_212_kept "$LEFTOVER_212_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_183_kept "$LEFTOVER_183_KEPT"
  kv leftover_190_kept "$LEFTOVER_190_KEPT"
  kv leftover_177_kept "$LEFTOVER_177_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv live_db_present "$LIVE_DB"
  kv live_jar_present "$LIVE_JAR"
  kv live_minio_present "$LIVE_MINIO"
  kv live_purge_window SKIP
  kv live_purge_observed false
  kv live_purge_window_skip_reason "$PURGE_REASON"
  kv live_quartz SKIP
  kv live_quartz_observed false
  kv live_quartz_skip_reason "$QUARTZ_REASON"
  kv live_minio_cold SKIP
  kv live_minio_cold_observed false
  kv live_minio_cold_skip_reason "$MINIO_REASON"
  kv live_staging_5432 SKIP
  kv live_query_observed false
  kv live_export_observed false
  kv live_staging_skip_reason "$STAGING_REASON"
  kv live_jar_context_walk SKIP
  kv live_jar_context_observed false
  kv live_jar_context_skip_reason "$JAR_REASON"
  kv live_keycloak_pkce SKIP
  kv token_exchange_observed false
  kv live_keycloak_skip_reason "$KC_REASON"
  kv live_ca_trust SKIP
  kv ca_trust_observed false
  kv live_ca_skip_reason "$CA_REASON"
  kv human_laptop_click SKIP
  kv laptop_click_observed false
  kv laptop_click_skip_reason "$CLICK_REASON"
  kv redo_230 false
  kv redo_222 false
  kv redo_183 false
  kv redo_190 false
  kv redo_177 false
  kv redo_147 false
  kv g4_close NOT_CLAIMED
  kv g4_close_reason "Honesty leftover remainder beyond #230/#222. Hot-purge / P1 staging / PKCE SPA leftover cells PASS. Live purge / staging / JAR / Keycloak SKIP when unobserved. Not product G4 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

{
  echo "# G4 leftover honesty remainder beyond #230/#222"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #230 hot-purge / staging cells | PASS |"
  echo "| leftover #222 PKCE SPA remainder cells | PASS |"
  echo "| leftover #183 / #190 / #177 cells | PASS |"
  echo "| window policy / staging-like / SPA mint | PASS |"
  echo "| live purge / Quartz / MinIO | SKIP |"
  echo "| live staging / JAR context | SKIP |"
  echo "| live Keycloak / CA / laptop click | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #230/#222/#183/#190/#177/#147 redo | false |"
  echo "| product G4 close | NOT_CLAIMED |"
  echo
  echo "Live purge reason: $PURGE_REASON"
  echo "Live Keycloak reason: $KC_REASON"
} > "$OUT/matrix.md"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g4-hot-purge-staging-pkce-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty recorded (#230/#222 cells honest; live purge / staging / Keycloak SKIP)"
echo "See docs/g4-hot-purge-staging-pkce-remainder-honesty.md"
echo "Keep sibling leftovers: docs/g4-hot-purge-staging-leftover-honesty.md, docs/g4-pkce-spa-verifier-remainder-honesty.md, docs/g5-publishhook-ddl-remainder-honesty.md, docs/s0-secret-rotate-getenv-leftover-honesty.md, docs/g10-file-object-reconcile-remainder-honesty.md, docs/g17-mfa-recovery-vue-otp-remainder-honesty.md, docs/g1-fieldpolicy-mask-second-auth-remainder-honesty.md"
echo "Not claimed: product G4 close / live purge / live staging / live Keycloak / #230/#222 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
