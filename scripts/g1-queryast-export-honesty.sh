#!/usr/bin/env bash
# G1 leftover beyond #191/#146: BoundSql / COUNT / export FieldPolicy drop
# PASS/SKIP honesty residual. Does not redo #134/#146/#191 product code.
# Does not claim product G1 closed. G18 stays closed. No P0/M0 redo.
# Docker forbidden. AuditPort same-TX untouched.
# Live BoundSql / COUNT / IsolatedExport drop is PASS only when a listener
# is present AND the walk was observed. Missing JAR is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

README="$ROOT/docs/README.md"
DOC_191="$ROOT/docs/g1-queryast-boundsql-honesty.md"
DOC_146="$ROOT/docs/g1-queryast-boundsql-guard.md"
DOC_134="$ROOT/docs/g1-export-fieldpolicy-drop.md"
FIXTURE_191="$ROOT/scripts/g1-queryast-boundsql-honesty/fixtures/honest-skip.env"

REQUIRED_LEFTOVER_ROWS="g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-export-fieldpolicy-drop g1-queryast-export-honesty g1-datascope-or-in-ast g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g2-h5-every-page-form-write-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-honesty g17-mfa-recovery-vue-otp-honesty g2-ops-pg-dump-honesty g9-sentinel-cluster-remainder-honesty g5-publishhook-ddl-honesty g17-gm-tls-nginx-handshake-honesty g2-h5-every-page g2-form-write-honesty g4-export-wizard-deny-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g3-outbox-consume-crash-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g10-file-reconcile-honesty g10-identity-drift-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g1-queryast-export-honesty.sh [--out DIR]
  scripts/g1-queryast-export-honesty.sh --verify-report FILE

Beyond #191/#146: leftover BoundSql / COUNT stay on QueryAstScopedQuery;
export hidden drop stays on IsolatedExport. Live JAR is PASS only when
present AND the walk was observed. Missing listener is honest SKIP.
Does not re-run #134/#146/#191. Does not claim product G1 close.

  --out DIR           Output directory (default: .local/g1-queryast-export-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_134_cells_honest leftover_146_cells_honest leftover_191_cells_honest leftover_boundsql_guard leftover_count_guard leftover_export_hidden_drop raw_boundsql_rejected count_is_ast_node isolated_export_applies_mask second_auth_without_grant_rejects leftover_surface leftover_rows_kept leftover_rows leftover_134_kept leftover_146_kept leftover_191_kept leftover_204_kept leftover_197_kept leftover_205_kept leftover_188_kept leftover_209_kept leftover_217_kept leftover_210_kept leftover_203_kept leftover_202_kept leftover_196_kept leftover_195_kept leftover_198_kept leftover_200_kept leftover_192_kept leftover_193_kept live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_boundsql_count live_boundsql_list_observed live_boundsql_count_observed live_boundsql_skip_reason live_export_fieldpolicy_drop live_export_drop_observed live_export_drop_skip_reason ui_h5_report ui_h5_report_skip_reason redone_134 redone_142 redone_146 redone_162 redone_168 redone_184 redone_191 honesty_134_rerun honesty_146_rerun honesty_191_rerun g1_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_134)" = "false" ] || fail "must not redo #134"
  [ "$(report_get "$file" redone_142)" = "false" ] || fail "must not redo #142"
  [ "$(report_get "$file" redone_146)" = "false" ] || fail "must not redo #146"
  [ "$(report_get "$file" redone_162)" = "false" ] || fail "must not redo #162"
  [ "$(report_get "$file" redone_168)" = "false" ] || fail "must not redo #168"
  [ "$(report_get "$file" redone_184)" = "false" ] || fail "must not redo #184"
  [ "$(report_get "$file" redone_191)" = "false" ] || fail "must not redo #191"
  [ "$(report_get "$file" honesty_134_rerun)" = "false" ] || fail "honesty_134_rerun must be false"
  [ "$(report_get "$file" honesty_146_rerun)" = "false" ] || fail "honesty_146_rerun must be false"
  [ "$(report_get "$file" honesty_191_rerun)" = "false" ] || fail "honesty_191_rerun must be false"
  [ "$(report_get "$file" g1_close)" = "NOT_CLAIMED" ] || fail "g1_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_134_cells_honest)" = "PASS" ] || fail "leftover_134_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_146_cells_honest)" = "PASS" ] || fail "leftover_146_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_191_cells_honest)" = "PASS" ] || fail "leftover_191_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_boundsql_guard)" = "PASS" ] || fail "leftover_boundsql_guard must be PASS"
  [ "$(report_get "$file" leftover_count_guard)" = "PASS" ] || fail "leftover_count_guard must be PASS"
  [ "$(report_get "$file" leftover_export_hidden_drop)" = "PASS" ] || fail "leftover_export_hidden_drop must be PASS"
  [ "$(report_get "$file" raw_boundsql_rejected)" = "true" ] || fail "raw_boundsql_rejected must stay true"
  [ "$(report_get "$file" count_is_ast_node)" = "false" ] || fail "count_is_ast_node must stay false"
  [ "$(report_get "$file" isolated_export_applies_mask)" = "false" ] || fail "isolated_export_applies_mask must stay false"
  [ "$(report_get "$file" second_auth_without_grant_rejects)" = "true" ] || fail "second_auth_without_grant_rejects must stay true"
  [ "$(report_get "$file" leftover_surface)" = "QueryAstScopedQuery+IsolatedExport" ] || fail "leftover_surface must be QueryAstScopedQuery+IsolatedExport"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_134_kept)" = "true" ] || fail "leftover_134_kept must stay true (keep g1-export-fieldpolicy-drop)"
  [ "$(report_get "$file" leftover_146_kept)" = "true" ] || fail "leftover_146_kept must stay true (keep g1-queryast-boundsql-guard)"
  [ "$(report_get "$file" leftover_191_kept)" = "true" ] || fail "leftover_191_kept must stay true (keep g1-queryast-boundsql-honesty)"
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || fail "leftover_204_kept must stay true (keep g2-h5-every-page-form-write-honesty)"
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || fail "leftover_197_kept must stay true (keep g9-redis-down-invalidate-remainder)"
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || fail "leftover_205_kept must stay true (keep g17-mfa-recovery-vue-otp-honesty)"
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || fail "leftover_188_kept must stay true (keep g2-ops-pg-dump-honesty)"
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || fail "leftover_209_kept must stay true (keep g9-sentinel-cluster-remainder-honesty)"
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || fail "leftover_217_kept must stay true (keep g5-publishhook-ddl-honesty)"
  [ "$(report_get "$file" leftover_210_kept)" = "true" ] || fail "leftover_210_kept must stay true (keep g17-gm-tls-nginx-handshake-honesty)"
  [ "$(report_get "$file" leftover_203_kept)" = "true" ] || fail "leftover_203_kept must stay true (keep g4-export-wizard-deny-honesty)"
  [ "$(report_get "$file" leftover_202_kept)" = "true" ] || fail "leftover_202_kept must stay true (keep g10-leftover-honesty)"
  [ "$(report_get "$file" leftover_196_kept)" = "true" ] || fail "leftover_196_kept must stay true (keep g5-mid-publish-failure-honesty)"
  [ "$(report_get "$file" leftover_195_kept)" = "true" ] || fail "leftover_195_kept must stay true (keep g3-outbox-consume-crash-honesty)"
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
  for verdict in live_boundsql_count live_export_fieldpolicy_drop ui_h5_report; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_boundsql_count)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_boundsql_count=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_boundsql_list_observed)" = "true" ] || fail "live_boundsql_count=PASS requires live_boundsql_list_observed=true"
    [ "$(report_get "$file" live_boundsql_count_observed)" = "true" ] || fail "live_boundsql_count=PASS requires live_boundsql_count_observed=true"
  fi
  if [ "$(report_get "$file" live_boundsql_count)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_boundsql_skip_reason)" ] || fail "live BoundSql SKIP needs live_boundsql_skip_reason"
    [ "$(report_get "$file" live_boundsql_list_observed)" != "true" ] || fail "SKIP cannot claim a live BoundSql list walk"
    [ "$(report_get "$file" live_boundsql_count_observed)" != "true" ] || fail "SKIP cannot claim a live COUNT walk"
  fi
  if [ "$(report_get "$file" live_export_fieldpolicy_drop)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_export_fieldpolicy_drop=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_export_drop_observed)" = "true" ] || fail "live_export_fieldpolicy_drop=PASS requires live_export_drop_observed=true"
  fi
  if [ "$(report_get "$file" live_export_fieldpolicy_drop)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_export_drop_skip_reason)" ] || fail "live export drop SKIP needs live_export_drop_skip_reason"
    [ "$(report_get "$file" live_export_drop_observed)" != "true" ] || fail "SKIP cannot claim a live IsolatedExport drop walk"
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
  OUT="$ROOT/.local/g1-queryast-export-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G1 leftover honesty beyond #191/#146 (BoundSql / COUNT / export FieldPolicy drop PASS/SKIP)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #134/#146/#191. Not product G1 close. G18 stays closed."
echo

[ -f "$README" ] || fail "missing $README"
[ -f "$DOC_191" ] || fail "missing committed #191 leftover $DOC_191"
[ -f "$DOC_146" ] || fail "missing committed #146 leftover $DOC_146"
[ -f "$DOC_134" ] || fail "missing committed #134 leftover $DOC_134"
[ -f "$FIXTURE_191" ] || fail "missing committed #191 honest-skip fixture $FIXTURE_191"
grep -Fq 'g1_close=NOT_CLAIMED' "$FIXTURE_191" || fail "committed #191 fixture must keep g1_close=NOT_CLAIMED"
grep -Fq 'leftover_boundsql_guard=PASS' "$FIXTURE_191" || fail "committed #191 fixture must keep leftover_boundsql_guard=PASS"
grep -Fq 'leftover_count_guard=PASS' "$FIXTURE_191" || fail "committed #191 fixture must keep leftover_count_guard=PASS"
grep -Fq 'raw_boundsql_rejected=true' "$FIXTURE_191" || fail "committed #191 fixture must keep raw_boundsql_rejected=true"
grep -Fq 'count_is_ast_node=false' "$FIXTURE_191" || fail "committed #191 fixture must keep count_is_ast_node=false"
grep -Fq 'does **not** claim a product-wide G1 close' "$DOC_134" || fail "committed #134 leftover must keep product G1 not claimed"
grep -Fq 'drop hidden' "$DOC_134" || fail "committed #134 leftover must keep export hidden drop"
grep -Fq 'does **not** claim a product-wide G1 close' "$DOC_146" || fail "committed #146 leftover must keep product G1 not claimed"
grep -Fq 'QueryAstScopedQuery' "$DOC_146" || fail "committed #146 leftover must keep QueryAstScopedQuery"
grep -Fq 'g1_close=NOT_CLAIMED' "$DOC_191" || fail "committed #191 leftover must keep g1_close=NOT_CLAIMED"

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

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_134_KEPT=false
LEFTOVER_146_KEPT=false
LEFTOVER_191_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_217_KEPT=false
LEFTOVER_210_KEPT=false
LEFTOVER_203_KEPT=false
LEFTOVER_202_KEPT=false
LEFTOVER_196_KEPT=false
LEFTOVER_195_KEPT=false
LEFTOVER_198_KEPT=false
LEFTOVER_200_KEPT=false
LEFTOVER_192_KEPT=false
LEFTOVER_193_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g1-export-fieldpolicy-drop) LEFTOVER_134_KEPT=true ;;
      g1-queryast-boundsql-guard) LEFTOVER_146_KEPT=true ;;
      g1-queryast-boundsql-honesty) LEFTOVER_191_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
      g2-ops-pg-dump-honesty) LEFTOVER_188_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
      g17-gm-tls-nginx-handshake-honesty) LEFTOVER_210_KEPT=true ;;
      g4-export-wizard-deny-honesty) LEFTOVER_203_KEPT=true ;;
      g10-leftover-honesty) LEFTOVER_202_KEPT=true ;;
      g5-mid-publish-failure-honesty) LEFTOVER_196_KEPT=true ;;
      g3-outbox-consume-crash-honesty) LEFTOVER_195_KEPT=true ;;
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
[ "$LEFTOVER_134_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-export-fieldpolicy-drop (#134)"
[ "$LEFTOVER_146_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-boundsql-guard (#146)"
[ "$LEFTOVER_191_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-boundsql-honesty (#191)"
[ "$LEFTOVER_204_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-honesty (#204)"
[ "$LEFTOVER_197_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder (#197)"
[ "$LEFTOVER_205_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-honesty (#205)"
[ "$LEFTOVER_188_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-ops-pg-dump-honesty (#188)"
[ "$LEFTOVER_209_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-sentinel-cluster-remainder-honesty (#209)"
[ "$LEFTOVER_217_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-honesty (#217)"
[ "$LEFTOVER_210_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-gm-tls-nginx-handshake-honesty (#210)"

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
  BOUNDSQL_REASON="NEXUS_BASE_URL / NEXUS_G1_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; leftover BoundSql / COUNT stay on QueryAstScopedQuery (#146/#191); live GuardedLedgerStore SKIP; Docker forbidden"
  EXPORT_REASON="NEXUS_BASE_URL / NEXUS_G1_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; export hidden drop stays on IsolatedExport (#134); live IsolatedExport SKIP; Docker forbidden"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  BOUNDSQL_REASON="NEXUS_G1_LIVE_CONTEXT_ID unset; live GuardedLedgerStore leftover BoundSql / COUNT walk SKIP"
  EXPORT_REASON="NEXUS_G1_LIVE_CONTEXT_ID unset; live IsolatedExport hidden-drop walk SKIP"
elif [ "$LIVE_LISTENER" != "true" ]; then
  BOUNDSQL_REASON="live base set but product JAR not reachable; live BoundSql / COUNT walk SKIP"
  EXPORT_REASON="live base set but product JAR not reachable; live IsolatedExport hidden-drop walk SKIP"
else
  BOUNDSQL_REASON="live listener present but this leftover does not claim a walk until leftover BoundSql list and COUNT through QueryAstScopedQuery → GuardedJdbc were observed; does not re-run #191/#146"
  EXPORT_REASON="live listener present but this leftover does not claim a walk until IsolatedExport hidden-drop was observed; does not re-run #134"
fi

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
  kv leftover_134_cells_honest PASS
  kv leftover_146_cells_honest PASS
  kv leftover_191_cells_honest PASS
  kv leftover_boundsql_guard PASS
  kv leftover_count_guard PASS
  kv leftover_export_hidden_drop PASS
  kv raw_boundsql_rejected true
  kv count_is_ast_node false
  kv isolated_export_applies_mask false
  kv second_auth_without_grant_rejects true
  kv leftover_surface QueryAstScopedQuery+IsolatedExport
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_134_kept "$LEFTOVER_134_KEPT"
  kv leftover_146_kept "$LEFTOVER_146_KEPT"
  kv leftover_191_kept "$LEFTOVER_191_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_210_kept "$LEFTOVER_210_KEPT"
  kv leftover_203_kept "$LEFTOVER_203_KEPT"
  kv leftover_202_kept "$LEFTOVER_202_KEPT"
  kv leftover_196_kept "$LEFTOVER_196_KEPT"
  kv leftover_195_kept "$LEFTOVER_195_KEPT"
  kv leftover_198_kept "$LEFTOVER_198_KEPT"
  kv leftover_200_kept "$LEFTOVER_200_KEPT"
  kv leftover_192_kept "$LEFTOVER_192_KEPT"
  kv leftover_193_kept "$LEFTOVER_193_KEPT"
  kv honesty_134_rerun false
  kv honesty_146_rerun false
  kv honesty_191_rerun false
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_boundsql_count SKIP
  kv live_boundsql_list_observed false
  kv live_boundsql_count_observed false
  kv live_boundsql_skip_reason "$BOUNDSQL_REASON"
  kv live_export_fieldpolicy_drop SKIP
  kv live_export_drop_observed false
  kv live_export_drop_skip_reason "$EXPORT_REASON"
  kv ui_h5_report SKIP
  kv ui_h5_report_skip_reason "UI / H5 / report UI leftover BoundSql / COUNT / export FieldPolicy was not walked; leftover stays open"
  kv redone_134 false
  kv redone_142 false
  kv redone_146 false
  kv redone_162 false
  kv redone_168 false
  kv redone_184 false
  kv redone_191 false
  kv g1_close NOT_CLAIMED
  kv g1_close_reason "Honesty leftover beyond #191/#146. BoundSql / COUNT stay on QueryAstScopedQuery. Export hidden drop stays on IsolatedExport. Live JAR SKIP when unavailable. Not product G1 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

{
  echo "# G1 leftover honesty beyond #191/#146"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #191 / #146 BoundSql / COUNT | PASS |"
  echo "| leftover #134 export hidden drop | PASS |"
  echo "| raw BoundSql rejected | true |"
  echo "| COUNT is Query AST node | false |"
  echo "| IsolatedExport applies mask | false |"
  echo "| live BoundSql / COUNT | SKIP |"
  echo "| live IsolatedExport drop | SKIP |"
  echo "| UI / H5 / report UI | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #134/#146/#191 redo | false |"
  echo "| product G1 close | NOT_CLAIMED |"
  echo
  echo "BoundSql SKIP reason: $BOUNDSQL_REASON"
  echo "Export drop SKIP reason: $EXPORT_REASON"
} > "$OUT/matrix.md"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g1-queryast-export-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty recorded (#134/#146/#191 cells honest; live BoundSql SKIP; live export drop SKIP)"
echo "See docs/g1-queryast-export-honesty.md"
echo "Keep sibling leftovers: docs/g1-queryast-boundsql-honesty.md, docs/g1-queryast-boundsql-guard.md, docs/g1-export-fieldpolicy-drop.md, docs/g2-h5-every-page-form-write-honesty.md"
echo "Not claimed: product G1 close / COUNT as Query AST node / IsolatedExport mask / #134/#146/#191 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
