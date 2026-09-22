#!/usr/bin/env bash
# G3 leftover: Outbox consume-idempotent stress / duplicate-delivery evidence.
# At-least-once delivery + platform.exec_idempotency skips a second side effect.
# Does not start the product JAR. Does not rebuild Outbox / Dispatcher.
# Does not claim a product G3 close. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G3 Outbox consume-idempotent leftover"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "Scenario (in-process Zonky PG 15, not a product G3 close):"
echo "  1. first dispatch delivers notify.inbox at least once"
echo "  2. same outbox id is requeued many times; handler side effect stays 1"
echo "  3. concurrent dispatchOne after the DB key exists skips the handler"
echo "  4. concurrent claim on the same (scope, key) yields one FIRST"
echo "  5. InAppDeliveryHandler DUPLICATE skips a second inbox update"
echo "  6. product boot requeue after DELIVERED does not add a second inbox row"
echo

mvn -pl nexus-event-core -am test \
  -Dtest=G3OutboxConsumeIdempotentTest,OutboxEventContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl nexus-notification-core -am test \
  -Dtest=G3InboxConsumeIdempotentTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl nexus-bootstrap -am test \
  -Dtest=NexusOneOutboxDispatcherTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: at-least-once + exec_idempotency skips second side effects"
echo "See docs/g3-outbox-consume-idempotent.md"
echo "Not claimed: product G3 close / Outbox rebuild / multi-node dispatcher / Kafka"
