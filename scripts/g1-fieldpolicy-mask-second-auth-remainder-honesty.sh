#!/usr/bin/env bash
# G1 leftover beyond #184/#162/#215: FieldPolicy mask / second-auth
# remainder PASS/SKIP honesty.
# Parent leftoverMaskFields / #184 honesty / #215 BoundSql-export stay PASS.
# Live JAR / IsolatedExport mask / second-auth are PASS only when observed.
# Missing live JAR is honest SKIP. Does not redo #162/#184/#215.
# Does not claim product G1 close. G18 stays closed. No Docker.
# No P0/M0 redo. AuditPort same-TX untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

README="$ROOT/docs/README.md"
DOC_162="$ROOT/docs/g1-fieldpolicy-mask-second-auth.md"
DOC_184="$ROOT/docs/g1-fieldpolicy-mask-second-auth-honesty.md"
DOC_215="$ROOT/docs/g1-queryast-export-honesty.md"
FIXTURE_184="$ROOT/scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures/honest-skip.env"
FIXTURE_215="$ROOT/scripts/g1-queryast-export-honesty/fixtures/honest-skip.env"

REQUIRED_LEFTOVER_ROWS="g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-fieldpolicy-mask-second-auth-remainder-honesty g1-queryast-export-honesty g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-export-fieldpolicy-drop g1-datascope-or-in-ast g9-live-multi-jvm-replica-remainder-honesty g4-hot-purge-staging-leftover-honesty g7-withdraw-browser-evidence-honesty g2-h5-every-page-form-write-remainder-honesty g9-redis-down-invalidate-remainder-honesty g7-query-expense-travel-honesty-remainder g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty g9-sentinel-cluster-remainder-honesty g2-ops-pg-dump-honesty g17-mfa-recovery-vue-otp-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-honesty g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g3-outbox-consume-crash-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g10-file-reconcile-honesty g10-identity-drift-honesty g2-h5-every-page g2-form-write-honesty g9-live-multi-jvm-replica g9-sentinel-cluster-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh [--out DIR]
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report FILE

Beyond #184/#162/#215: leftoverMaskFields stay beside Query AST.
Parent leftover honesty fixtures stay PASS. Live JAR / IsolatedExport
mask / second-auth are PASS only when observed. Missing live host is
honest SKIP. Does not re-run #162/#184/#215. Does not claim product G1 close.

  --out DIR           Output directory (default: .local/g1-fieldpolicy-mask-second-auth-remainder-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_162_cells_honest leftover_184_cells_honest leftover_215_cells_honest leftover_184_honesty_gate leftover_beside_ast mask_on_ast second_auth_on_ast isolated_export_applies_mask leftover_surface leftover_rows_kept leftover_rows leftover_162_kept leftover_184_kept leftover_215_kept leftover_229_kept leftover_230_kept leftover_212_kept leftover_232_kept leftover_237_kept leftover_239_kept leftover_226_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_222_kept leftover_216_kept leftover_220_kept leftover_210_kept leftover_217_kept leftover_209_kept leftover_188_kept leftover_205_kept leftover_197_kept leftover_204_kept leftover_198_kept leftover_200_kept leftover_192_kept leftover_193_kept live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_api_mask live_api_mask_observed live_api_mask_skip_reason live_export_mask live_export_mask_observed live_export_mask_skip_reason live_second_auth live_second_auth_observed live_second_auth_skip_reason ui_h5_report ui_h5_report_skip_reason honesty_162_rerun honesty_184_rerun honesty_215_rerun redone_162 redone_184 redone_215 g1_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_162)" = "false" ] || fail "must not redo #162"
  [ "$(report_get "$file" redone_184)" = "false" ] || fail "must not redo #184"
  [ "$(report_get "$file" redone_215)" = "false" ] || fail "must not redo #215"
  [ "$(report_get "$file" honesty_162_rerun)" = "false" ] || fail "honesty_162_rerun must be false"
  [ "$(report_get "$file" honesty_184_rerun)" = "false" ] || fail "honesty_184_rerun must be false"
  [ "$(report_get "$file" honesty_215_rerun)" = "false" ] || fail "honesty_215_rerun must be false"
  [ "$(report_get "$file" g1_close)" = "NOT_CLAIMED" ] || fail "g1_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_162_cells_honest)" = "PASS" ] || fail "leftover_162_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_184_cells_honest)" = "PASS" ] || fail "leftover_184_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_215_cells_honest)" = "PASS" ] || fail "leftover_215_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_184_honesty_gate)" = "PASS" ] || fail "leftover_184_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_beside_ast)" = "PASS" ] || fail "leftover_beside_ast must be PASS"
  [ "$(report_get "$file" mask_on_ast)" = "false" ] || fail "mask_on_ast must stay false"
  [ "$(report_get "$file" second_auth_on_ast)" = "false" ] || fail "second_auth_on_ast must stay false"
  [ "$(report_get "$file" isolated_export_applies_mask)" = "false" ] || fail "isolated_export_applies_mask must stay false"
  [ "$(report_get "$file" leftover_surface)" = "FieldPolicyProjector" ] || fail "leftover_surface must be FieldPolicyProjector"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_162_kept)" = "true" ] || fail "leftover_162_kept must stay true (keep g1-fieldpolicy-mask-second-auth)"
  [ "$(report_get "$file" leftover_184_kept)" = "true" ] || fail "leftover_184_kept must stay true (keep g1-fieldpolicy-mask-second-auth-honesty)"
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || fail "leftover_215_kept must stay true (keep g1-queryast-export-honesty)"
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || fail "leftover_229_kept must stay true (keep g9-live-multi-jvm-replica-remainder-honesty)"
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || fail "leftover_230_kept must stay true (keep g4-hot-purge-staging-leftover-honesty)"
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || fail "leftover_212_kept must stay true (keep g7-withdraw-browser-evidence-honesty)"
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || fail "leftover_232_kept must stay true (keep g2-h5-every-page-form-write-remainder-honesty)"
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || fail "leftover_237_kept must stay true (keep g9-redis-down-invalidate-remainder-honesty)"
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || fail "leftover_239_kept must stay true (keep g7-query-expense-travel-honesty-remainder)"
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || fail "leftover_226_kept must stay true (keep g3-outbox-consume-idempotent-honesty)"
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || fail "leftover_228_kept must stay true (keep g10-leftover-honesty-remainder)"
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || fail "leftover_221_kept must stay true (keep g2-report-physical-dump-honesty)"
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || fail "leftover_225_kept must stay true (keep g6-recovery-port-dry-run-remainder-honesty)"
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || fail "leftover_223_kept must stay true (keep s0-secret-rotate-getenv-remainder-honesty)"
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || fail "leftover_222_kept must stay true (keep g4-pkce-spa-verifier-remainder-honesty)"
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || fail "leftover_216_kept must stay true (keep g17-separation-leftover-honesty)"
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || fail "leftover_220_kept must stay true (keep g15-wx-dt-native-bridge-remainder)"
  [ "$(report_get "$file" leftover_210_kept)" = "true" ] || fail "leftover_210_kept must stay true (keep g17-gm-tls-nginx-handshake-honesty)"
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || fail "leftover_217_kept must stay true (keep g5-publishhook-ddl-honesty)"
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || fail "leftover_209_kept must stay true (keep g9-sentinel-cluster-remainder-honesty)"
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || fail "leftover_188_kept must stay true (keep g2-ops-pg-dump-honesty)"
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || fail "leftover_205_kept must stay true (keep g17-mfa-recovery-vue-otp-honesty)"
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || fail "leftover_197_kept must stay true (keep g9-redis-down-invalidate-remainder)"
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || fail "leftover_204_kept must stay true (keep g2-h5-every-page-form-write-honesty)"
  [ "$(report_get "$file" leftover_198_kept)" = "true" ] || fail "leftover_198_kept must stay true (keep g15-wx-dt-native-bridge-honesty)"
  [ "$(report_get "$file" leftover_200_kept)" = "true" ] || fail "leftover_200_kept must stay true (keep s0-secret-rotate-without-restart-honesty)"
  [ "$(report_get "$file" leftover_192_kept)" = "true" ] || fail "leftover_192_kept must stay true (keep g7-query-expense-travel-honesty)"
  [ "$(report_get "$file" leftover_193_kept)" = "true" ] || fail "leftover_193_kept must stay true (keep linux-arm-profile-leftover-honesty)"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local verdict
  for verdict in live_api_mask live_export_mask live_second_auth ui_h5_report; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_api_mask)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_api_mask=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_api_mask_observed)" = "true" ] || fail "live_api_mask=PASS requires live_api_mask_observed=true"
  fi
  if [ "$(report_get "$file" live_export_mask)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_export_mask=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_export_mask_observed)" = "true" ] || fail "live_export_mask=PASS requires live_export_mask_observed=true"
  fi
  if [ "$(report_get "$file" live_second_auth)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_second_auth=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_second_auth_observed)" = "true" ] || fail "live_second_auth=PASS requires live_second_auth_observed=true"
  fi
  if [ "$(report_get "$file" live_api_mask)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_api_mask_skip_reason)" ] || fail "live API mask SKIP needs live_api_mask_skip_reason"
    [ "$(report_get "$file" live_api_mask_observed)" != "true" ] || fail "SKIP cannot claim a live API mask walk"
  fi
  if [ "$(report_get "$file" live_export_mask)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_export_mask_skip_reason)" ] || fail "live export mask SKIP needs live_export_mask_skip_reason"
    [ "$(report_get "$file" live_export_mask_observed)" != "true" ] || fail "SKIP cannot claim a live IsolatedExport mask walk"
  fi
  if [ "$(report_get "$file" live_second_auth)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_second_auth_skip_reason)" ] || fail "live second-auth SKIP needs live_second_auth_skip_reason"
    [ "$(report_get "$file" live_second_auth_observed)" != "true" ] || fail "SKIP cannot claim a live second-auth walk"
  fi
  if [ "$(report_get "$file" ui_h5_report)" = "SKIP" ]; then
    [ -n "$(report_get "$file" ui_h5_report_skip_reason)" ] || fail "UI/H5 SKIP needs ui_h5_report_skip_reason"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g1-fieldpolicy-mask-second-auth-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G1 leftover honesty beyond #184/#162/#215 (FieldPolicy mask / second-auth remainder PASS/SKIP)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #162/#184/#215. Not product G1 close. G18 stays closed."
echo

[ -f "$README" ] || fail "missing $README"
[ -f "$DOC_162" ] || fail "missing committed #162 leftover $DOC_162"
[ -f "$DOC_184" ] || fail "missing committed #184 leftover $DOC_184"
[ -f "$DOC_215" ] || fail "missing committed #215 leftover $DOC_215"
[ -f "$FIXTURE_184" ] || fail "missing committed #184 honest-skip fixture $FIXTURE_184"
[ -f "$FIXTURE_215" ] || fail "missing committed #215 honest-skip fixture $FIXTURE_215"
grep -Fq 'g1_close=NOT_CLAIMED' "$FIXTURE_184" || fail "committed #184 fixture must keep g1_close=NOT_CLAIMED"
grep -Fq 'leftover_beside_ast=PASS' "$FIXTURE_184" || fail "committed #184 fixture must keep leftover_beside_ast=PASS"
grep -Fq 'mask_on_ast=false' "$FIXTURE_184" || fail "committed #184 fixture must keep mask_on_ast=false"
grep -Fq 'isolated_export_applies_mask=false' "$FIXTURE_184" || fail "committed #184 fixture must keep isolated_export_applies_mask=false"
grep -Fq 'live_api_mask=SKIP' "$FIXTURE_184" || fail "committed #184 fixture must keep live_api_mask=SKIP"
grep -Fq 'g1_close=NOT_CLAIMED' "$FIXTURE_215" || fail "committed #215 fixture must keep g1_close=NOT_CLAIMED"
grep -Fq 'isolated_export_applies_mask=false' "$FIXTURE_215" || fail "committed #215 fixture must keep isolated_export_applies_mask=false"
grep -Fq 'leftover_export_hidden_drop=PASS' "$FIXTURE_215" || fail "committed #215 fixture must keep leftover_export_hidden_drop=PASS"
grep -Fq 'g1_close=NOT_CLAIMED' "$DOC_184" || fail "committed #184 leftover must keep g1_close=NOT_CLAIMED"
grep -Fq 'g1_close=NOT_CLAIMED' "$DOC_215" || fail "committed #215 leftover must keep g1_close=NOT_CLAIMED"

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

LIVE_ENDPOINT="${NEXUS_G1_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G1_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

LIVE_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="NEXUS_BASE_URL / NEXUS_G1_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; IsolatedExport still writes raw cells (#162/#184); Docker forbidden; this leftover does not re-run #184/#215"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="NEXUS_G1_LIVE_CONTEXT_ID unset; live FieldPolicy mask / second-auth walk SKIP; this leftover does not re-run #184"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live base set but product JAR not reachable; live walk SKIP; this leftover does not re-run #184"
else
  LIVE_REASON="live listener present but this leftover does not claim a walk until list/detail mask, IsolatedExport mask, and second-auth grant were observed; this leftover does not re-run #184"
fi

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_162_KEPT=false
LEFTOVER_184_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_229_KEPT=false
LEFTOVER_230_KEPT=false
LEFTOVER_212_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_237_KEPT=false
LEFTOVER_239_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_210_KEPT=false
LEFTOVER_217_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_198_KEPT=false
LEFTOVER_200_KEPT=false
LEFTOVER_192_KEPT=false
LEFTOVER_193_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g1-fieldpolicy-mask-second-auth) LEFTOVER_162_KEPT=true ;;
      g1-fieldpolicy-mask-second-auth-honesty) LEFTOVER_184_KEPT=true ;;
      g1-queryast-export-honesty) LEFTOVER_215_KEPT=true ;;
      g9-live-multi-jvm-replica-remainder-honesty) LEFTOVER_229_KEPT=true ;;
      g4-hot-purge-staging-leftover-honesty) LEFTOVER_230_KEPT=true ;;
      g7-withdraw-browser-evidence-honesty) LEFTOVER_212_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g9-redis-down-invalidate-remainder-honesty) LEFTOVER_237_KEPT=true ;;
      g7-query-expense-travel-honesty-remainder) LEFTOVER_239_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g17-gm-tls-nginx-handshake-honesty) LEFTOVER_210_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g2-ops-pg-dump-honesty) LEFTOVER_188_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
      g15-wx-dt-native-bridge-honesty) LEFTOVER_198_KEPT=true ;;
      s0-secret-rotate-without-restart-honesty) LEFTOVER_200_KEPT=true ;;
      g7-query-expense-travel-honesty) LEFTOVER_192_KEPT=true ;;
      linux-arm-profile-leftover-honesty) LEFTOVER_193_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_162_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth (#162)"
[ "$LEFTOVER_184_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-fieldpolicy-mask-second-auth-honesty (#184)"
[ "$LEFTOVER_215_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-export-honesty (#215)"
[ "$LEFTOVER_229_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-live-multi-jvm-replica-remainder-honesty (#229)"
[ "$LEFTOVER_230_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-leftover-honesty (#230)"
[ "$LEFTOVER_212_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-evidence-honesty (#212)"
[ "$LEFTOVER_232_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-remainder-honesty (#232)"
[ "$LEFTOVER_237_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder-honesty (#237)"
[ "$LEFTOVER_239_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty-remainder (#239)"
[ "$LEFTOVER_226_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-outbox-consume-idempotent-honesty (#226)"
[ "$LEFTOVER_228_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty-remainder (#228)"

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
  kv leftover_162_cells_honest PASS
  kv leftover_184_cells_honest PASS
  kv leftover_215_cells_honest PASS
  kv leftover_184_honesty_gate PASS
  kv leftover_beside_ast PASS
  kv mask_on_ast false
  kv second_auth_on_ast false
  kv isolated_export_applies_mask false
  kv leftover_surface FieldPolicyProjector
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_162_kept "$LEFTOVER_162_KEPT"
  kv leftover_184_kept "$LEFTOVER_184_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_229_kept "$LEFTOVER_229_KEPT"
  kv leftover_230_kept "$LEFTOVER_230_KEPT"
  kv leftover_212_kept "$LEFTOVER_212_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_237_kept "$LEFTOVER_237_KEPT"
  kv leftover_239_kept "$LEFTOVER_239_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_210_kept "$LEFTOVER_210_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_198_kept "$LEFTOVER_198_KEPT"
  kv leftover_200_kept "$LEFTOVER_200_KEPT"
  kv leftover_192_kept "$LEFTOVER_192_KEPT"
  kv leftover_193_kept "$LEFTOVER_193_KEPT"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_api_mask SKIP
  kv live_api_mask_observed false
  kv live_api_mask_skip_reason "$LIVE_REASON"
  kv live_export_mask SKIP
  kv live_export_mask_observed false
  kv live_export_mask_skip_reason "$LIVE_REASON"
  kv live_second_auth SKIP
  kv live_second_auth_observed false
  kv live_second_auth_skip_reason "$LIVE_REASON"
  kv ui_h5_report SKIP
  kv ui_h5_report_skip_reason "UI / H5 / report UI FieldPolicy mask was not walked; leftover stays open; this leftover does not re-run #184"
  kv honesty_162_rerun false
  kv honesty_184_rerun false
  kv honesty_215_rerun false
  kv redone_162 false
  kv redone_184 false
  kv redone_215 false
  kv g1_close NOT_CLAIMED
  kv g1_close_reason "Honesty leftover beyond #184/#162/#215. leftoverMaskFields PASS. IsolatedExport still writes raw cells. Live JAR / IsolatedExport mask / second-auth SKIP when unavailable. Not product G1 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

{
  echo "# G1 leftover honesty beyond #184/#162/#215"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #162 leftoverMaskFields | PASS |"
  echo "| leftover #184 honesty cells | PASS |"
  echo "| leftover #215 BoundSql / export | PASS |"
  echo "| leftover beside AST | PASS |"
  echo "| mask_on_ast | false |"
  echo "| IsolatedExport applies mask | false |"
  echo "| live API mask | SKIP |"
  echo "| live IsolatedExport mask | SKIP |"
  echo "| live second-auth | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #162/#184/#215 redo | false |"
  echo "| product G1 close | NOT_CLAIMED |"
  echo
  echo "Live reason: $LIVE_REASON"
} > "$OUT/matrix.md"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g1-fieldpolicy-mask-second-auth-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty recorded (#162/#184/#215 cells honest; live JAR / IsolatedExport mask / second-auth SKIP)"
echo "See docs/g1-fieldpolicy-mask-second-auth-remainder-honesty.md"
echo "Keep sibling leftovers: docs/g1-fieldpolicy-mask-second-auth.md, docs/g1-fieldpolicy-mask-second-auth-honesty.md, docs/g1-queryast-export-honesty.md, docs/g9-live-multi-jvm-replica-remainder-honesty.md, docs/g4-hot-purge-staging-leftover-honesty.md, docs/g7-withdraw-browser-evidence-honesty.md, docs/g2-h5-every-page-form-write-remainder-honesty.md, docs/g9-redis-down-invalidate-remainder-honesty.md, docs/g7-query-expense-travel-honesty-remainder.md, docs/g3-outbox-consume-idempotent-honesty.md, docs/g10-leftover-honesty-remainder.md"
echo "Not claimed: product G1 close / mask on Query AST / IsolatedExport mask / live JAR walk / #162/#184/#215 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
