#!/usr/bin/env bash
# G5 form-publish failed-path evidence (forms only).
# Runs the PostgreSQL 15 IT + HTTP tests. Does not start the product JAR.
# Product SchemaServicePublishHook CREATE/evolve on drift. Mid-publish failure:
# SchemaService evolve DDL abort on Zonky PG 15, plus PublishHook /
# SchemaServicePublishHook stand-ins.
# No Docker. Credentials are not required: Zonky embeds PostgreSQL 15.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G5 form publish rollback (forms only)"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-p3-studio -am test \
  -Dtest=JdbcFormPublishRollbackIT,FormPublishFailureApiTest,FormPublishStateMachineTest,SchemaServicePublishHookTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: validate-fail→DRAFT and publish-fail→unchanged Running/snapshot"
echo "See docs/g5-form-publish-rollback.md"
