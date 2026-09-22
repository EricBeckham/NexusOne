#!/usr/bin/env bash
# G1 leftover honesty: FieldPolicy mask / second-auth stay beside Query AST.
# Does not redo #115/#130/#134/#142/#146. Does not claim product G1 closed.
# Fake close / fake mask-on-AST reports must fail. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

verify_report() {
  local file="$1"
  local expect="$2"
  python3 - "$file" "$expect" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
expect = sys.argv[2]
report = {}
for raw in path.read_text().splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    report[key.strip()] = value.strip()
errors = []
close = report.get("g1_close", "")
if close.upper() in {"CLOSED", "PASS", "TRUE"} or close != "NOT_CLAIMED":
    errors.append("g1_close must stay NOT_CLAIMED")
if report.get("mask_on_ast", "").lower() in {"true", "yes", "1"}:
    errors.append("mask_on_ast must stay false")
if report.get("second_auth_on_ast", "").lower() in {"true", "yes", "1"}:
    errors.append("second_auth_on_ast must stay false")
if report.get("leftover_surface") != "FieldPolicyProjector":
    errors.append("leftover_surface must be FieldPolicyProjector")
ok = not errors
if expect == "pass":
    if not ok:
        print("expected honest leftover, got: " + "; ".join(errors))
        sys.exit(1)
    print("PASS honesty: " + path.name)
elif expect == "fail":
    if ok:
        print("expected fake report to fail: " + path.name)
        sys.exit(1)
    print("PASS reject fake: " + path.name + " (" + "; ".join(errors) + ")")
else:
    sys.exit("unknown expect")
PY
}

if [ "${1:-}" = "--verify-report" ]; then
  file="${2:?report.env}"
  expect="${3:-pass}"
  verify_report "$file" "$expect"
  exit 0
fi

echo "== G1 FieldPolicy mask / second-auth honesty"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "== Honesty fixtures"
verify_report scripts/g1-fieldpolicy-mask-second-auth/fixtures/honest-leftover.env pass
verify_report scripts/g1-fieldpolicy-mask-second-auth/fixtures/fake-g1-close.env fail
verify_report scripts/g1-fieldpolicy-mask-second-auth/fixtures/fake-mask-on-ast.env fail

echo
echo "== Narrow harness"
mvn -pl nexus-permission-core,nexus-bootstrap -am test \
  -Dtest=FieldPolicyMaskHonestyTest,G1FieldPolicyMaskSecondAuthHonestyTest,QueryAstPolicyCompilerTest,FieldPolicyProjectorTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: mask / second-auth stay beside Query AST on FieldPolicyProjector"
echo "g1_close=NOT_CLAIMED"
echo "See docs/g1-fieldpolicy-mask-second-auth.md"
