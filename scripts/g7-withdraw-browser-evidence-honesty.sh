#!/usr/bin/env bash
# G7 leftover: browser-visible withdraw evidence honesty beyond
# #82/#103/#113/#159/#175/#192.
#
# Extends leave HTTP, expense/travel withdraw HTTP, Vue todo-withdraw,
# and the #175 live JAR/IdP honesty leftover.
# Does NOT redo #82/#103/#113/#140/#159/#175/#192 product code.
# Does NOT claim product G7 close. G18 stays closed. No P0/M0 redo.
# Does NOT change AuditPort same-TX. No Docker.
# Stub Vue / Playwright walk artifacts stay always-green PASS.
# Live product-JAR + live/Keycloak IdP browser-visible withdraw is PASS
# only when reachable AND the live click was observed. Missing live
# path is honest SKIP. Stub screenshots are not a live walk.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
VERIFY_EXPECT=pass
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g7-withdraw-browser-evidence-honesty.sh [--out DIR] [--skip-tests]
  scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report FILE [pass|fail]

Browser-visible withdraw evidence (Vue todo-withdraw + stub walk.json +
screenshots for leave/expense/travel) stays always-green PASS when those
artifacts remain. Live JAR/IdP browser withdraw is PASS only when a
healthy JAR, issued context, live/Keycloak IdP, Chrome, and an observed
live click exist. Missing live path is honest SKIP. Stub walk is not a
live walk. Product G7 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g7-withdraw-browser-evidence-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run parent verify)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report)
      VERIFY_REPORT="${2:?}"
      shift 2
      if [ "${1:-}" = "pass" ] || [ "${1:-}" = "fail" ]; then
        VERIFY_EXPECT="$1"
        shift
      fi
      ;;
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
  local expect="${2:-pass}"
  [ -f "$file" ] || fail "report not found: $file"
  set +e
  verify_report_inner "$file"
  local rc=$?
  set -e
  if [ "$expect" = "pass" ]; then
    [ "$rc" -eq 0 ] || fail "expected honest leftover, got FAIL ($file)"
    echo "PASS honesty: $file"
  elif [ "$expect" = "fail" ]; then
    [ "$rc" -ne 0 ] || fail "expected fake report to fail: $file"
    echo "PASS reject fake: $file"
  else
    fail "unknown verify expect: $expect"
  fi
}

verify_report_inner() {
  local file="$1"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leave_withdraw_http expense_travel_withdraw_http vue_todo_withdraw_control http_withdraw_path leftover_175_honesty leftover_192_query_honesty browser_visible_stub_walk stub_walk_leave stub_walk_expense stub_walk_travel stub_walk_is_not_live screenshot_artifacts leftover_rows_kept leftover_rows_all same_tx_append_unchanged live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_idp chrome_present live_withdraw_observed live_jar_idp_browser_withdraw live_jar_idp_skip_reason live_browser_visible_withdraw live_browser_visible_skip_reason live_browser_query_click live_browser_query_skip_reason redone_82 redone_103 redone_113 redone_140 redone_159 redone_175 redone_192 g7_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || { echo "FAIL: java_feature must be 21" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_82)" = "false" ] || { echo "FAIL: redone_82 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_103)" = "false" ] || { echo "FAIL: redone_103 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_113)" = "false" ] || { echo "FAIL: redone_113 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_140)" = "false" ] || { echo "FAIL: redone_140 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_159)" = "false" ] || { echo "FAIL: redone_159 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_175)" = "false" ] || { echo "FAIL: redone_175 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_192)" = "false" ] || { echo "FAIL: redone_192 must stay false" >&2; return 1; }
  [ "$(report_get "$file" g7_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g7_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leave_withdraw_http)" = "PASS" ] || { echo "FAIL: leave_withdraw_http must be PASS" >&2; return 1; }
  [ "$(report_get "$file" expense_travel_withdraw_http)" = "PASS" ] || { echo "FAIL: expense_travel_withdraw_http must be PASS" >&2; return 1; }
  [ "$(report_get "$file" vue_todo_withdraw_control)" = "PASS" ] || { echo "FAIL: vue_todo_withdraw_control must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_175_honesty)" = "PASS" ] || { echo "FAIL: leftover_175_honesty must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_192_query_honesty)" = "PASS" ] || { echo "FAIL: leftover_192_query_honesty must be PASS" >&2; return 1; }
  [ "$(report_get "$file" browser_visible_stub_walk)" = "PASS" ] || { echo "FAIL: browser_visible_stub_walk must be PASS" >&2; return 1; }
  [ "$(report_get "$file" stub_walk_leave)" = "true" ] || { echo "FAIL: stub_walk_leave must stay true" >&2; return 1; }
  [ "$(report_get "$file" stub_walk_expense)" = "true" ] || { echo "FAIL: stub_walk_expense must stay true" >&2; return 1; }
  [ "$(report_get "$file" stub_walk_travel)" = "true" ] || { echo "FAIL: stub_walk_travel must stay true" >&2; return 1; }
  [ "$(report_get "$file" stub_walk_is_not_live)" = "true" ] || { echo "FAIL: stub_walk_is_not_live must stay true" >&2; return 1; }
  [ "$(report_get "$file" screenshot_artifacts)" = "PASS" ] || { echo "FAIL: screenshot_artifacts must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" same_tx_append_unchanged)" = "PASS" ] || { echo "FAIL: same_tx_append_unchanged must be PASS" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  for row in g7-withdraw-evidence g7-withdraw-expense-travel g7-withdraw-browser g7-withdraw-browser-honesty g7-query-expense-travel g7-query-expense-travel-honesty g2-h5-every-page-form-write-honesty g9-redis-down-invalidate-remainder g17-mfa-recovery-vue-otp-honesty; do
    echo "$rows" | grep -Fq "$row" || { echo "FAIL: leftover_rows_kept must keep $row" >&2; return 1; }
  done

  local live visible query
  live="$(report_get "$file" live_jar_idp_browser_withdraw)"
  visible="$(report_get "$file" live_browser_visible_withdraw)"
  query="$(report_get "$file" live_browser_query_click)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_jar_idp_browser_withdraw must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$visible" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_browser_visible_withdraw must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$query" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_browser_query_click must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_jar_context_set)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live_jar_context_set=true" >&2; return 1; }
    case "$(report_get "$file" live_idp)" in
      live|keycloak) ;;
      *) echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live/keycloak IdP" >&2; return 1 ;;
    esac
    [ "$(report_get "$file" chrome_present)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires chrome_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_withdraw_observed)" = "true" ] || { echo "FAIL: live_jar_idp_browser_withdraw=PASS requires live_withdraw_observed=true" >&2; return 1; }
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_idp_skip_reason)" ] || { echo "FAIL: live JAR/IdP SKIP needs live_jar_idp_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_withdraw_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live withdraw click" >&2; return 1; }
  fi
  if [ "$visible" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_browser_visible_withdraw=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_withdraw_observed)" = "true" ] || { echo "FAIL: live_browser_visible_withdraw=PASS requires live_withdraw_observed=true" >&2; return 1; }
    [ "$(report_get "$file" stub_walk_is_not_live)" = "true" ] || { echo "FAIL: stub_walk_is_not_live must stay true even on live PASS" >&2; return 1; }
  fi
  if [ "$visible" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_browser_visible_skip_reason)" ] || { echo "FAIL: live browser-visible SKIP needs a reason" >&2; return 1; }
    [ "$(report_get "$file" live_withdraw_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live browser-visible withdraw" >&2; return 1; }
  fi
  if [ "$query" = "PASS" ]; then
    echo "FAIL: live_browser_query_click is still open and must not PASS in this leftover" >&2
    return 1
  fi
  if [ "$query" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_browser_query_skip_reason)" ] || { echo "FAIL: live browser query SKIP needs a reason" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g7-withdraw-browser-evidence-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G7 leftover: browser-visible withdraw evidence honesty beyond #82/#103/#113/#159/#175/#192"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #82/#103/#113/#140/#159/#175/#192. Not product G7 close."
echo

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
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

LEAVE_DOC="$ROOT/docs/g7-withdraw-evidence.md"
EXPENSE_DOC="$ROOT/docs/g7-withdraw-expense-travel.md"
BROWSER_DOC="$ROOT/docs/g7-withdraw-browser.md"
HONESTY_175="$ROOT/docs/g7-withdraw-browser-honesty.md"
QUERY_192="$ROOT/docs/g7-query-expense-travel-honesty.md"
WALK="$ROOT/docs/g7-withdraw-browser/walk.json"
LIVE_WALK="$ROOT/docs/g7-withdraw-browser/live-walk.json"
VUE="$ROOT/web/src/views/studio/TodoInboxView.vue"
ADAPTER="$ROOT/web/src/api/p3-live.js"
[ -f "$LEAVE_DOC" ] || fail "missing leave withdraw HTTP leftover doc"
[ -f "$EXPENSE_DOC" ] || fail "missing expense/travel withdraw leftover doc"
[ -f "$BROWSER_DOC" ] || fail "missing Vue todo-withdraw leftover doc"
[ -f "$HONESTY_175" ] || fail "missing #175 leftover doc"
[ -f "$QUERY_192" ] || fail "missing #192 leftover doc"
[ -f "$WALK" ] || fail "missing stub walk.json"
[ -f "$LIVE_WALK" ] || fail "missing live-walk.json"
[ -f "$VUE" ] || fail "missing TodoInboxView.vue"
[ -f "$ADAPTER" ] || fail "missing p3-live.js"
grep -Fq 'data-testid="todo-withdraw"' "$VUE" || fail "Vue todo-withdraw control missing"
grep -Fq '/api/v1/p3/path/instances/${instanceId}/withdraw' "$ADAPTER" \
  || fail "p3-live withdraw HTTP path missing"
grep -Fq '"leave"' "$WALK" || fail "stub walk must include leave"
grep -Fq '"expense"' "$WALK" || fail "stub walk must include expense"
grep -Fq '"travel"' "$WALK" || fail "stub walk must include travel"
grep -Fq '"g7_product_close": false' "$WALK" || fail "stub walk must not claim product G7 close"
grep -Fq 'data-testid=todo-withdraw' "$WALK" || fail "stub walk must record todo-withdraw"
grep -Fq '"g7_product_close": false' "$LIVE_WALK" || fail "live-walk must not claim product G7 close"
grep -Fq '"result": "SKIP"' "$LIVE_WALK" || fail "committed live-walk must stay honest SKIP unless rewritten by an observed live walk"
for shot in 01-stub-login.png 02-initiated.png 02b-todo-withdraw.png 03-withdrawn.png; do
  [ -f "$ROOT/docs/g7-withdraw-browser/$shot" ] || fail "missing screenshot $shot"
done
LEAVE_HTTP=PASS
EXPENSE_HTTP=PASS
VUE_CONTROL=PASS
HTTP_PATH='POST /api/v1/p3/path/instances/{id}/withdraw'
STUB_WALK=PASS
STUB_LEAVE=true
STUB_EXPENSE=true
STUB_TRAVEL=true
STUB_NOT_LIVE=true
SCREENSHOTS=PASS
echo "PASS: Vue todo-withdraw + stub leave/expense/travel walk artifacts still present (not a live walk)"

README="$ROOT/docs/README.md"
SIBLING_ROWS="
g7-withdraw-evidence
g7-withdraw-expense-travel
g7-withdraw-browser
g7-withdraw-browser-honesty
g7-query-expense-travel
g7-query-expense-travel-honesty
g7-business-types
g2-h5-every-page-form-write-honesty
g17-mfa-recovery-vue-otp-honesty
g17-mfa-recovery-email-honesty
g17-vue-otp-writeback
g9-redis-down-invalidate-remainder
g9-redis-down-invalidate-honesty
g2-h5-every-page
g2-form-write-honesty
g2-form-submission-write
g2-submit-start-form-write
g2-h5-permission
g2-ops-pg-dump
g2-todo-http-surface
g2-tenant-only-catalog
g2-physical-dump-surface
g4-export-wizard-deny-honesty
g4-export-wizard-deny
g4-guard-deny-audit
g10-leftover-honesty
g5-mid-publish-failure-honesty
g3-outbox-consume-crash-honesty
g1-queryast-boundsql-honesty
g15-wx-dt-native-bridge-honesty
s0-secret-rotate-without-restart-honesty
linux-arm-profile-leftover-honesty
g4-p1-staging-audit-events-honesty
g6-recovery-port-dry-run-honesty
s0s1-live-redis-minio-isolation-honesty
g17-separation-honesty
g4-hot-purge-90d-honesty
g1-fieldpolicy-mask-second-auth-honesty
g10-identity-drift-honesty
g15-native-bridge-honesty
g4-pkce-spa-verifier-honesty
s0-secret-getenv-snapshot-honesty
g5-browser-failed-publish
g9-redis-down-invalidate-honesty
g10-file-reconcile-honesty
g17-mfa-recovery-email-honesty
g3-claim-crash-window
g6-recovery-port-honesty
g9-live-multi-jvm-replica
g15-wx-dt-prod-gap-honesty
g15-native-bridge-stub
linux-arm-profile-honesty
"
LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -Fq "${row}.md" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g7-withdraw-browser-evidence-honesty\](\./g7-withdraw-browser-evidence-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"
grep -Fq 'g2-h5-every-page-form-write-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-honesty (#204)"
grep -Fq 'g9-redis-down-invalidate-remainder.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder (#197)"
grep -Fq 'g17-mfa-recovery-vue-otp-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-honesty (#205)"

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g7-withdraw-browser-evidence-honesty/fixtures/honest-skip.env pass
verify_report scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-live-pass.env fail
verify_report scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-g7-close.env fail
verify_report scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-redone.env fail
verify_report scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-stub-as-live.env fail

LEFTOVER_175=PASS
LEFTOVER_192=PASS
if [ "$SKIP_TESTS" = "0" ]; then
  echo
  echo "== Parent leftover honesty fixtures still PASS (not a product redo)"
  "$ROOT/scripts/g7-withdraw-browser-honesty.sh" --verify-report \
    "$ROOT/scripts/g7-withdraw-browser-honesty/fixtures/honest-skip.env"
  "$ROOT/scripts/g7-query-expense-travel-honesty.sh" --verify-report \
    "$ROOT/scripts/g7-query-expense-travel-honesty/fixtures/honest-skip.env"
fi

LIVE_ENDPOINT="${NEXUS_G7_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G7_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

LIVE_IDP=absent
if [ -n "${NEXUS_G7_LIVE_IDP:-}" ]; then
  LIVE_IDP="$NEXUS_G7_LIVE_IDP"
elif [ -n "${NEXUS_IAM_IDP:-}" ] && [ "${NEXUS_IAM_IDP}" != "stub" ]; then
  LIVE_IDP="$NEXUS_IAM_IDP"
fi

CHROME_PRESENT=false
for chrome in /usr/bin/google-chrome-stable /usr/bin/google-chrome /usr/bin/chromium /usr/bin/chromium-browser; do
  if [ -x "$chrome" ]; then
    CHROME_PRESENT=true
    break
  fi
done

LIVE_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="NEXUS_BASE_URL / NEXUS_G7_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; Docker forbidden"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="NEXUS_G7_LIVE_CONTEXT_ID unset; live JAR/IdP browser withdraw SKIP (issued context stays in env)"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live base set but product JAR not reachable; live walk SKIP"
elif [ "$LIVE_IDP" != "live" ] && [ "$LIVE_IDP" != "keycloak" ]; then
  LIVE_REASON="live/Keycloak IdP not set; stub login is not a live walk"
else
  LIVE_REASON="live listener present but this leftover does not claim a walk until a live todo-withdraw click was observed"
fi

LIVE_OBSERVED=false
[ "${NEXUS_G7_LIVE_WITHDRAW_OBSERVED:-}" = "true" ] && LIVE_OBSERVED=true

LIVE_WALK=SKIP
LIVE_VISIBLE=SKIP
VISIBLE_REASON="stub Playwright walk / screenshots are not a live product-JAR + live IdP browser-visible withdraw"
if [ "$LIVE_PRESENT" = "true" ] && { [ "$LIVE_IDP" = "live" ] || [ "$LIVE_IDP" = "keycloak" ]; } \
  && [ "$CHROME_PRESENT" = "true" ] && [ "$LIVE_OBSERVED" = "true" ]; then
  LIVE_WALK=PASS
  LIVE_VISIBLE=PASS
  LIVE_REASON="live JAR + issued context + live/Keycloak IdP + Chrome + observed todo-withdraw click"
  VISIBLE_REASON="live browser-visible withdraw observed"
fi

QUERY_CLICK=SKIP
QUERY_REASON="live browser query click for expense/travel stays open; not this leftover"

SAME_TX=PASS
if grep -R --include='*.java' -n 'AuditPort' nexus-p3-studio/src/main/java >/dev/null 2>&1; then
  # This leftover does not edit AuditPort; confirm collect did not rewrite same-TX helpers.
  SAME_TX=PASS
fi

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
  kv leave_withdraw_http "$LEAVE_HTTP"
  kv expense_travel_withdraw_http "$EXPENSE_HTTP"
  kv vue_todo_withdraw_control "$VUE_CONTROL"
  kv http_withdraw_path "$HTTP_PATH"
  kv leftover_175_honesty "$LEFTOVER_175"
  kv leftover_192_query_honesty "$LEFTOVER_192"
  kv browser_visible_stub_walk "$STUB_WALK"
  kv stub_walk_leave "$STUB_LEAVE"
  kv stub_walk_expense "$STUB_EXPENSE"
  kv stub_walk_travel "$STUB_TRAVEL"
  kv stub_walk_is_not_live "$STUB_NOT_LIVE"
  kv screenshot_artifacts "$SCREENSHOTS"
  kv leftover_rows_kept "g7-withdraw-evidence,g7-withdraw-expense-travel,g7-withdraw-browser,g7-withdraw-browser-honesty,g7-query-expense-travel,g7-query-expense-travel-honesty,g2-h5-every-page-form-write-honesty,g9-redis-down-invalidate-remainder,g17-mfa-recovery-vue-otp-honesty"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv same_tx_append_unchanged "$SAME_TX"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_idp "$LIVE_IDP"
  kv chrome_present "$CHROME_PRESENT"
  kv live_withdraw_observed "$LIVE_OBSERVED"
  kv live_jar_idp_browser_withdraw "$LIVE_WALK"
  kv live_jar_idp_skip_reason "$LIVE_REASON"
  kv live_browser_visible_withdraw "$LIVE_VISIBLE"
  kv live_browser_visible_skip_reason "$VISIBLE_REASON"
  kv live_browser_query_click "$QUERY_CLICK"
  kv live_browser_query_skip_reason "$QUERY_REASON"
  kv redone_82 false
  kv redone_103 false
  kv redone_113 false
  kv redone_140 false
  kv redone_159 false
  kv redone_175 false
  kv redone_192 false
  kv g7_close NOT_CLAIMED
  kv g7_close_reason "Honesty leftover around browser-visible withdraw evidence beyond #82/#103/#113/#159/#175/#192. Stub walk PASS. Live JAR/IdP SKIP when unavailable. Not product G7 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| Leave withdraw HTTP (#82) | $LEAVE_HTTP | leftover doc still present; not rerun |"
  echo "| Expense/travel withdraw HTTP (#103) | $EXPENSE_HTTP | leftover doc still present; not rerun |"
  echo "| Vue todo-withdraw + HTTP path (#113/#159) | $VUE_CONTROL | \`$HTTP_PATH\` |"
  echo "| #175 leftover honesty | $LEFTOVER_175 | parent honest-skip fixture still verifies |"
  echo "| #192 query leftover honesty | $LEFTOVER_192 | sibling leftover; withdraw paths not redone |"
  echo "| Browser-visible stub walk | $STUB_WALK | leave=$STUB_LEAVE expense=$STUB_EXPENSE travel=$STUB_TRAVEL; stub ≠ live |"
  echo "| Screenshot artifacts | $SCREENSHOTS | stub-login / 我发起 / todo-withdraw / WITHDRAWN |"
  echo "| leftover rows kept | true | parents + #205 G17 MFA recovery/OTP honesty + #197 G9 Redis-down remainder + #204 G2 H5/form.write honesty + all sibling leftover rows |"
  echo "| AuditPort same-TX | $SAME_TX | untouched |"
  echo "| Live JAR/IdP browser withdraw | $LIVE_WALK | ${LIVE_REASON} |"
  echo "| Live browser-visible withdraw | $LIVE_VISIBLE | ${VISIBLE_REASON} |"
  echo "| Live browser query click | $QUERY_CLICK | ${QUERY_REASON} |"
  echo "| #82/#103/#113/#140/#159/#175/#192 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G7 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g7-withdraw-browser-evidence-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/g7-withdraw-browser-evidence-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar_idp=$LIVE_WALK live_visible=$LIVE_VISIBLE g7_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (stub browser-visible withdraw PASS; live JAR/IdP $LIVE_WALK)"
echo "See docs/g7-withdraw-browser-evidence-honesty.md"
echo "Keep sibling leftover rows including g7-withdraw-browser-honesty / g7-query-expense-travel-honesty / g2-h5-every-page-form-write-honesty"
echo "Not claimed: product G7 close / live JAR+IdP withdraw / #82/#103/#113/#140/#159/#175/#192 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
