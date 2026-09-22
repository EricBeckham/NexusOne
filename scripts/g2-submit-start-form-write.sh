#!/usr/bin/env bash
# G2 remainder: path submit-start dual-check (form.write + process.instance/start).
# Deny before FormService.submit / Flowable start when form.write is missing.
# tenant_member seed may remain. AuditPort is not changed. G18 stays closed.
# Not a full product G2 close. No Docker. Boot 4.1.1 / Java 21 / PG 15.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 submit-start form.write dual-check"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-p3-studio -am test \
  -Dtest=G2SubmitStartFormWriteIT,G2SurfaceAgreementTest,FormSurfaceGuardTest,FormSubmissionWriteApiTest,G18BusinessPathIT \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: submit-start with form_id denies before start when form.write is missing"
echo "See docs/g2-submit-start-form-write.md"
