#!/usr/bin/env bash
# G4 remainder: same-trace login → permission → admin → export → guard-reject (Zonky PG 15).
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-bootstrap -am test \
  -Dtest=G4SameTraceWalkIT \
  -Dsurefire.failIfNoSpecifiedTests=false
