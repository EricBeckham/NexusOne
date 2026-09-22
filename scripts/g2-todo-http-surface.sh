#!/usr/bin/env bash
# G2 leftover: todo list HTTP on SurfaceGuard (process.task / read).
# AuditPort is not changed. G18 stays closed. Not a full product G2 close.
# Does not redo #144 tenant_only/catalog or #137 physical dump. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 todo HTTP SurfaceGuard"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2TodoSameEngineTest,TodoSurfaceGuardTest,G2TodoSurfaceAgreementTest,TodoSurfaceApiTest,SurfaceGuardBypassGateTest,G2H5SameEngineTest,G2SameEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: todo HTTP decide through SurfaceGuard; P2.PERM.0003/0002/0001 before TodoQueryService"
echo "See docs/g2-todo-http-surface.md"
