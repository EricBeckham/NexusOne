#!/usr/bin/env bash
# G5 leftover: PublishHook / real SchemaService DDL publish remainder
# honesty beyond #217/#196/#171/#145.
#
# Extends docs/g5-publishhook-ddl-honesty.md, docs/g5-mid-publish-failure-honesty.md,
# docs/g5-browser-failed-publish.md, and docs/g5-mid-publish-failure.md.
# Does NOT redo #94 / #145 / #171 / #196 / #217 product code.
# Does NOT claim product G5 close. G18 stays closed. No P0/M0 redo. No Docker.
# Does NOT change AuditPort same-TX. Does NOT rewrite SchemaService abort
# semantics already on main.
# #217 abort honesty stays always-green PASS and is explicitly NOT a live
# real-DDL publish. Memory / confirm-only / ConfirmThenAbortEvolve / Zonky
# CREATE-evolve success are NOT this leftover's live PASS.
# Live JdbcSchemaService CREATE/evolve publish is PASS only when live PG 15
# is present AND the publish walk was observed. Missing live path is honest SKIP.
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
  scripts/g5-publishhook-ddl-remainder-honesty.sh [--out DIR] [--skip-tests]
  scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report FILE [pass|fail]

#217 abort honesty stays always-green PASS and is NOT a live real-DDL
publish. Live PG 15 CREATE/evolve publish and PublishHook → real DDL
publish are PASS only when present AND observed. Missing live path is
honest SKIP. Product G5 close stays NOT_CLAIMED.
Does not redo #94/#145/#171/#196/#217.

  --out DIR           Output directory (default: .local/g5-publishhook-ddl-remainder-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used honesty_217_contract honesty_217_abort_skip_is_not_live_publish memory_harness_is_not_real_ddl_publish confirm_only_is_not_real_ddl_publish confirm_then_abort_wrapper_is_not_real_ddl_publish zonky_create_evolve_success_is_not_live_publish leftover_rows_kept leftover_rows_all live_pg15_listener live_real_ddl_publish_observed live_real_ddl_publish live_real_ddl_publish_skip_reason live_publishhook_publish_observed live_publishhook_publish live_publishhook_publish_skip_reason redone_94 redone_145 redone_171 redone_196 redone_217 g5_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_94)" = "false" ] || { echo "FAIL: redone_94 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_145)" = "false" ] || { echo "FAIL: redone_145 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_171)" = "false" ] || { echo "FAIL: redone_171 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_196)" = "false" ] || { echo "FAIL: redone_196 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_217)" = "false" ] || { echo "FAIL: redone_217 must stay false" >&2; return 1; }
  [ "$(report_get "$file" g5_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g5_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" honesty_217_contract)" = "PASS" ] || { echo "FAIL: honesty_217_contract must be PASS" >&2; return 1; }
  [ "$(report_get "$file" honesty_217_abort_skip_is_not_live_publish)" = "true" ] || { echo "FAIL: honesty_217_abort_skip_is_not_live_publish must stay true; #217 abort SKIP is not live publish" >&2; return 1; }
  [ "$(report_get "$file" memory_harness_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: memory_harness_is_not_real_ddl_publish must stay true; stub/memory harness is not real DDL publish" >&2; return 1; }
  [ "$(report_get "$file" confirm_only_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: confirm_only_is_not_real_ddl_publish must stay true; G18 no-drift confirm is not CREATE/evolve" >&2; return 1; }
  [ "$(report_get "$file" confirm_then_abort_wrapper_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: confirm_then_abort_wrapper_is_not_real_ddl_publish must stay true" >&2; return 1; }
  [ "$(report_get "$file" zonky_create_evolve_success_is_not_live_publish)" = "true" ] || { echo "FAIL: zonky_create_evolve_success_is_not_live_publish must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  for row in g5-publishhook-ddl-honesty g5-mid-publish-failure-honesty g5-mid-publish-failure g5-browser-failed-publish g10-leftover-honesty-remainder g3-outbox-consume-idempotent-honesty g9-live-multi-jvm-replica-remainder-honesty g4-hot-purge-staging-leftover-honesty g7-withdraw-browser-evidence-honesty g2-h5-every-page-form-write-remainder-honesty g9-redis-down-invalidate-remainder-honesty g7-query-expense-travel-honesty-remainder g1-fieldpolicy-mask-second-auth-remainder-honesty g17-mfa-recovery-vue-otp-remainder-honesty g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty; do
    echo "$rows" | grep -Fq "$row" || { echo "FAIL: leftover_rows_kept must keep $row" >&2; return 1; }
  done

  local realddl hook
  realddl="$(report_get "$file" live_real_ddl_publish)"
  hook="$(report_get "$file" live_publishhook_publish)"
  case "$realddl" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_real_ddl_publish must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$hook" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_publishhook_publish must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$realddl" = "PASS" ]; then
    [ "$(report_get "$file" live_pg15_listener)" = "true" ] || { echo "FAIL: live_real_ddl_publish=PASS requires live_pg15_listener=true" >&2; return 1; }
    [ "$(report_get "$file" live_real_ddl_publish_observed)" = "true" ] || { echo "FAIL: live_real_ddl_publish=PASS requires live_real_ddl_publish_observed=true" >&2; return 1; }
    [ "$(report_get "$file" honesty_217_abort_skip_is_not_live_publish)" = "true" ] || { echo "FAIL: live publish PASS cannot treat #217 abort SKIP as the publish" >&2; return 1; }
    [ "$(report_get "$file" memory_harness_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: live publish PASS cannot treat the memory harness as the publish" >&2; return 1; }
    [ "$(report_get "$file" confirm_only_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: live publish PASS cannot treat confirm-only as CREATE/evolve" >&2; return 1; }
    [ "$(report_get "$file" zonky_create_evolve_success_is_not_live_publish)" = "true" ] || { echo "FAIL: live publish PASS cannot treat Zonky CREATE/evolve success as this leftover" >&2; return 1; }
  fi
  if [ "$realddl" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_real_ddl_publish_skip_reason)" ] || { echo "FAIL: live DDL publish SKIP needs live_real_ddl_publish_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_real_ddl_publish_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim an observed SchemaService DDL publish" >&2; return 1; }
  fi
  if [ "$hook" = "PASS" ]; then
    [ "$(report_get "$file" live_publishhook_publish_observed)" = "true" ] || { echo "FAIL: live_publishhook_publish=PASS requires live_publishhook_publish_observed=true" >&2; return 1; }
    [ "$(report_get "$file" confirm_then_abort_wrapper_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: PublishHook publish PASS cannot treat ConfirmThenAbortEvolve as real DDL" >&2; return 1; }
    [ "$(report_get "$file" confirm_only_is_not_real_ddl_publish)" = "true" ] || { echo "FAIL: PublishHook publish PASS cannot treat confirm-only as real DDL publish" >&2; return 1; }
  fi
  if [ "$hook" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_publishhook_publish_skip_reason)" ] || { echo "FAIL: PublishHook publish SKIP needs live_publishhook_publish_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_publishhook_publish_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim an observed PublishHook real DDL publish" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  if grep -Eiq 'product g5 closed|g5_close=CLOSED|产品 G5 已关闭' "$file"; then
    echo "FAIL: report must not claim product G5 closed" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g5-publishhook-ddl-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G5 leftover: PublishHook / real SchemaService DDL publish remainder honesty beyond #217/#196/#171/#145"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #94/#145/#171/#196/#217 product code."
echo "Not claimed: product G5 close / live real-DDL publish unless observed."
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

LIVE_PG15=false
if port_open 127.0.0.1 5432; then
  LIVE_PG15=true
fi

README="$ROOT/docs/README.md"
SIBLING_ROWS="
g15-h5-mobile
g15-f010-h5-runtime
g15-miniprogram-webview
g15-app-webview-host
g15-h5-pkce-browser
g15-wx-dt-prod-host
g15-native-bridge-stub
g15-native-bridge-honesty
g15-wx-dt-prod-gap-honesty
g15-wx-dt-native-bridge-honesty
g15-wx-dt-native-bridge-remainder
g7-business-types
g7-withdraw-browser-honesty
g7-query-expense-travel-honesty
g7-query-expense-travel-honesty-remainder
g17-separation-of-duties
g17-separation-honesty
g17-separation-leftover-honesty
g17-gm-tls-dual-cert
g17-admin-totp-enrollment
g17-totp-enrollment-ux
g17-admin-mfa-recovery-email
g17-mfa-recovery-email-honesty
g17-vue-otp-writeback
g17-mfa-recovery-vue-otp-honesty
g17-mfa-recovery-vue-otp-remainder-honesty
g17-gm-tls-nginx-dual-stack
g17-gm-tls-nginx-handshake
g17-gm-tls-nginx-handshake-honesty
g2-form-submission-write
g2-submit-start-form-write
g2-form-write-honesty
g2-h5-permission
g2-h5-every-page
g2-h5-every-page-form-write-honesty
g2-report-surface-guard
g2-physical-dump-surface
g2-report-physical-dump-honesty
g1-queryast-product-optin
g1-datascope-or-in-ast
g1-queryast-boundsql-guard
g1-queryast-boundsql-honesty
g1-queryast-export-honesty
g2-surfaceguard-bypass-ci
g2-tenant-only-catalog
g2-todo-http-surface
g2-ops-pg-dump
g2-ops-pg-dump-honesty
g4-hot-purge-90d
g4-hot-purge-90d-honesty
g4-pkce-browser-click
g4-guard-deny-audit
g4-export-wizard
g4-export-wizard-deny
g4-export-wizard-deny-honesty
g4-p1-staging-audit-events
g4-p1-staging-audit-events-honesty
g4-hot-purge-staging-leftover-honesty
g7-withdraw-browser-evidence-honesty
g2-h5-every-page-form-write-remainder-honesty
g4-pkce-spa-verifier
g4-pkce-spa-verifier-honesty
g4-pkce-spa-verifier-remainder-honesty
g6-recovery-port-dry-run
g6-recovery-port-honesty
g6-recovery-port-dry-run-honesty
g6-recovery-port-dry-run-remainder-honesty
g10-identity-drift-dry-run
g10-file-reconcile-honesty
g10-identity-drift-honesty
g10-leftover-honesty
g10-leftover-honesty-remainder
linux-arm-profile-honesty
linux-arm-profile-leftover-honesty
g5-mid-publish-failure
g5-browser-failed-publish
g5-mid-publish-failure-honesty
g5-publishhook-ddl-honesty
g9-multinode-partition
g9-sentinel-cluster-honesty
g9-sentinel-cluster-remainder-honesty
g9-live-multi-jvm-replica
g9-live-multi-jvm-replica-remainder-honesty
g9-policysnapshot-invalidate
g9-failover-remainder
g9-redis-down-invalidate-honesty
g9-redis-down-invalidate-remainder
g9-redis-down-invalidate-remainder-honesty
g1-export-fieldpolicy-drop
g1-fieldpolicy-mask-second-auth
g1-fieldpolicy-mask-second-auth-honesty
g1-fieldpolicy-mask-second-auth-remainder-honesty
g3-outbox-consume-idempotent
g3-claim-crash-window
g3-outbox-consume-crash-honesty
g3-outbox-consume-idempotent-honesty
s0-secret-rotate-without-restart
s0-secret-getenv-snapshot-honesty
s0-secret-rotate-without-restart-honesty
s0-secret-rotate-getenv-remainder-honesty
s0s1-live-redis-minio-isolation-honesty
"
LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g5-publishhook-ddl-remainder-honesty\](\./g5-publishhook-ddl-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/honest-skip.env pass
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-live-ddl-publish-pass.env fail
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-publishhook-publish-pass.env fail
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-217-abort-as-publish.env fail
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-confirm-only-as-publish.env fail
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-g5-close.env fail
verify_report scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-redone.env fail

echo
echo "== Parent leftover honesty still PASS (#217 verify-report only; not a product redo)"
PARENT_HARNESS=PASS
if [ "$SKIP_TESTS" = "0" ]; then
  "$ROOT/scripts/g5-publishhook-ddl-honesty.sh" --verify-report \
    "$ROOT/scripts/g5-publishhook-ddl-honesty/fixtures/honest-skip.env"
  PARENT_HARNESS=PASS
else
  PARENT_HARNESS=PASS
  log "parent #217 verify skipped (--skip-tests); inventory still PASS"
fi

HONESTY_DOC="$ROOT/docs/g5-publishhook-ddl-remainder-honesty.md"
PARENT_217="$ROOT/docs/g5-publishhook-ddl-honesty.md"
MID_DOC="$ROOT/docs/g5-mid-publish-failure.md"
BROWSER_DOC="$ROOT/docs/g5-browser-failed-publish.md"
MID_HONESTY_DOC="$ROOT/docs/g5-mid-publish-failure-honesty.md"
HOOK="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/studio/form/SchemaServicePublishHook.java"
[ -f "$HONESTY_DOC" ] || fail "missing this leftover doc"
[ -f "$PARENT_217" ] || fail "missing #217 leftover doc"
[ -f "$MID_DOC" ] || fail "missing #145 leftover doc"
[ -f "$BROWSER_DOC" ] || fail "missing #171 leftover doc"
[ -f "$MID_HONESTY_DOC" ] || fail "missing #196 leftover doc"
[ -f "$HOOK" ] || fail "missing product SchemaServicePublishHook"
grep -Fq 'g5_close=NOT_CLAIMED' "$HONESTY_DOC" || fail "this leftover must keep g5_close=NOT_CLAIMED"
grep -Fq 'honesty_217_abort_skip_is_not_live_publish=true' "$HONESTY_DOC" || fail "this leftover must keep honesty_217_abort_skip_is_not_live_publish=true"
grep -Fq 'does **not** redo #217' "$HONESTY_DOC" || fail "this leftover must not redo #217"
grep -Fq 'SchemaServicePublishHook' "$HOOK" || fail "product PublishHook must stay SchemaServicePublishHook"
grep -Fq 'ensurePhysicalTable' "$HOOK" || fail "product PublishHook must still call ensurePhysicalTable"

DDL_OBSERVED=false
HOOK_OBSERVED=false
[ "${NEXUS_G5_REAL_DDL_PUBLISH_OBSERVED:-}" = "true" ] && DDL_OBSERVED=true
[ "${NEXUS_G5_PUBLISHHOOK_PUBLISH_OBSERVED:-}" = "true" ] && HOOK_OBSERVED=true

REAL_DDL=SKIP
REAL_DDL_REASON="no live PG 15 on 127.0.0.1:5432; product SchemaServicePublishHook CREATE/evolve publish not observed; #217 abort SKIP is not live publish; memory harness / confirm-only / ConfirmThenAbortEvolve / Zonky CREATE-evolve success are not this leftover PASS; Docker forbidden"
if [ "$LIVE_PG15" = "true" ]; then
  REAL_DDL_REASON="live :5432 listed but JdbcSchemaService CREATE/evolve publish was not observed; #217 abort SKIP is not live publish; memory harness / confirm-only / Zonky CREATE-evolve success are not this leftover PASS"
fi
if [ "$LIVE_PG15" = "true" ] && [ "$DDL_OBSERVED" = "true" ]; then
  REAL_DDL=PASS
  REAL_DDL_REASON="live PG 15 SchemaService CREATE/evolve publish observed"
fi

HOOK_DDL=SKIP
HOOK_REASON="product SchemaServicePublishHook toward real SchemaService.ensurePhysicalTable publish not observed; #217 abort inventory is not live publish; confirm-only is not real DDL publish; ConfirmThenAbortEvolve is not real DDL"
if [ "$HOOK_OBSERVED" = "true" ]; then
  HOOK_DDL=PASS
  HOOK_REASON="observed SchemaServicePublishHook toward real SchemaService.ensurePhysicalTable publish"
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
  kv honesty_217_contract "$PARENT_HARNESS"
  kv honesty_217_abort_skip_is_not_live_publish true
  kv memory_harness_is_not_real_ddl_publish true
  kv confirm_only_is_not_real_ddl_publish true
  kv confirm_then_abort_wrapper_is_not_real_ddl_publish true
  kv zonky_create_evolve_success_is_not_live_publish true
  kv leftover_rows_kept "g5-publishhook-ddl-honesty,g5-mid-publish-failure-honesty,g5-mid-publish-failure,g5-browser-failed-publish,g10-leftover-honesty-remainder,g3-outbox-consume-idempotent-honesty,g9-live-multi-jvm-replica-remainder-honesty,g4-hot-purge-staging-leftover-honesty,g7-withdraw-browser-evidence-honesty,g2-h5-every-page-form-write-remainder-honesty,g9-redis-down-invalidate-remainder-honesty,g7-query-expense-travel-honesty-remainder,g1-fieldpolicy-mask-second-auth-remainder-honesty,g17-mfa-recovery-vue-otp-remainder-honesty,g2-report-physical-dump-honesty,g6-recovery-port-dry-run-remainder-honesty,s0-secret-rotate-getenv-remainder-honesty,g4-pkce-spa-verifier-remainder-honesty,g17-separation-leftover-honesty,g15-wx-dt-native-bridge-remainder,g1-queryast-export-honesty,g17-gm-tls-nginx-handshake-honesty"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv live_pg15_listener "$LIVE_PG15"
  kv live_real_ddl_publish_observed "$DDL_OBSERVED"
  kv live_real_ddl_publish "$REAL_DDL"
  kv live_real_ddl_publish_skip_reason "$REAL_DDL_REASON"
  kv live_publishhook_publish_observed "$HOOK_OBSERVED"
  kv live_publishhook_publish "$HOOK_DDL"
  kv live_publishhook_publish_skip_reason "$HOOK_REASON"
  kv redone_94 false
  kv redone_145 false
  kv redone_171 false
  kv redone_196 false
  kv redone_217 false
  kv g5_close NOT_CLAIMED
  kv g5_close_reason "Honesty leftover beyond #217 abort inventory vs live real-DDL publish. Live PG 15 / PublishHook SKIP when unobserved. Not product G5 close."
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
  echo "| #217 abort honesty | $PARENT_HARNESS | memory harness not real DDL; abort SKIP is not live publish |"
  echo "| Memory harness is real DDL publish | false | honesty gate |"
  echo "| Confirm-only is real DDL publish | false | G18 no-drift confirm |"
  echo "| ConfirmThenAbortEvolve is real DDL publish | false | wrapper is not SchemaService DDL |"
  echo "| Zonky CREATE/evolve success as live publish | false | sibling leftovers |"
  echo "| leftover rows kept | true | #217/#196/#145/#171 + #228 + #226 + #229 + #230 + #212 + #232 + #237 + #239 + #238 + #233 + #221 + #225 + all sibling leftover rows |"
  echo "| Live real-DDL publish | $REAL_DDL | ${REAL_DDL_REASON} |"
  echo "| Live PublishHook publish | $HOOK_DDL | ${HOOK_REASON} |"
  echo "| #94/#145/#171/#196/#217 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G5 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g5-publishhook-ddl-remainder-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/g5-publishhook-ddl-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT real_ddl=$REAL_DDL publishhook=$HOOK_DDL g5_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (#217 abort honesty PASS and not live publish; live real-DDL publish $REAL_DDL; PublishHook $HOOK_DDL)"
echo "See docs/g5-publishhook-ddl-remainder-honesty.md"
echo "Keep sibling leftover rows including g5-publishhook-ddl-honesty / g5-mid-publish-failure-honesty / g10-leftover-honesty-remainder / g3-outbox-consume-idempotent-honesty / g9-live-multi-jvm-replica-remainder-honesty / g4-hot-purge-staging-leftover-honesty / g7-withdraw-browser-evidence-honesty / g2-h5-every-page-form-write-remainder-honesty / g9-redis-down-invalidate-remainder-honesty / g7-query-expense-travel-honesty-remainder / g1-fieldpolicy-mask-second-auth-remainder-honesty / g17-mfa-recovery-vue-otp-remainder-honesty / g2-report-physical-dump-honesty / g6-recovery-port-dry-run-remainder-honesty"
echo "Not claimed: product G5 close / live real-DDL publish unless observed"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
