#!/usr/bin/env python3
"""G7 leftover: expense + travel list / detail / todo query against a live JAR.

Uses the #69 catalog provisioner already on main. Does not rewrite leave,
withdraw ITs, or AuditPort. Talks HTTP only: issued Bearer +
X-Nexus-Context-Id, never X-Tenant-Id.

Requires a running NexusOne JAR (see scripts/g7/run-query-expense-travel.sh).
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from typing import Any
from urllib.parse import urlencode

PASS = 0
FAIL = 0
ROWS: list[dict[str, str]] = []

TYPES = (
    {
        "code": "expense",
        "handle": "approve",
        "expected": "APPROVED",
        "values": {
            "title": "交通费",
            "amount": 128.5,
            "category": "交通",
            "payee": "张三",
            "remark": "g7-expense-query",
        },
        "other": {
            "title": "餐费",
            "amount": 46.0,
            "category": "餐饮",
            "payee": "李四",
            "remark": "g7-expense-other",
        },
        "query": {"title": "交通费", "category": "交通", "payee": "张三", "remark": "g7-expense-query"},
        "filter_field": "category",
        "filter_value": "交通",
        "miss_value": "nosuch",
        "table_needle": "expense",
    },
    {
        "code": "travel",
        "handle": "reject",
        "expected": "REJECTED",
        "values": {
            "title": "客户拜访",
            "destination": "上海",
            "days": 3,
            "purpose": "g7-travel-query",
            "applicant": "alice",
        },
        "other": {
            "title": "内部培训",
            "destination": "杭州",
            "days": 1,
            "purpose": "g7-travel-other",
            "applicant": "bob",
        },
        "query": {"title": "客户拜访", "destination": "上海", "purpose": "g7-travel-query"},
        "filter_field": "destination",
        "filter_value": "上海",
        "miss_value": "nosuch",
        "table_needle": "travel",
    },
)


def pick(data: Any, *names: str, default=None):
    if data is None:
        return default
    if isinstance(data, list):
        return data
    for name in names:
        if isinstance(data, dict) and name in data and data[name] is not None:
            return data[name]
    return default


def stub_token(sub: str, email: str, username: str, name: str) -> str:
    payload = (
        '{"sub":"%s","email":"%s","preferred_username":"%s","name":"%s"}'
        % (sub, email, username, name)
    )
    encoded = base64.urlsafe_b64encode(payload.encode("utf-8")).decode("ascii").rstrip("=")
    return "stub." + encoded


def record(item: str, status: str, detail: str) -> None:
    global PASS, FAIL
    ROWS.append({"item": item, "status": status, "detail": detail})
    if status == "PASS":
        PASS += 1
    else:
        FAIL += 1
    print(f"[{status}] {item}: {detail}")


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        headers: dict[str, str] | None = None,
        expected: int | tuple | list | None = None,
    ) -> tuple[int, Any]:
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(
            self.base + path,
            data=data,
            method=method,
            headers={"Accept": "application/json", **(headers or {})},
        )
        if body is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8")
                status = resp.status
        except urllib.error.HTTPError as err:
            raw = err.read().decode("utf-8")
            status = err.code
        parsed: Any = None
        if raw:
            try:
                parsed = json.loads(raw)
            except json.JSONDecodeError:
                parsed = {"raw": raw}
        if expected is not None:
            allowed = expected if isinstance(expected, (list, tuple, set)) else (expected,)
            if status not in allowed:
                raise AssertionError(f"{method} {path} expected {allowed} got {status}: {parsed}")
        return status, parsed


def auth_headers(token: str, context_id: str | None = None, trace: str | None = None) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if context_id:
        headers["X-Nexus-Context-Id"] = context_id
    if trace:
        headers["X-Trace-Id"] = trace
    return headers


def platform_headers(actor: str, reason: str, trace: str) -> dict[str, str]:
    return {
        "X-Nexus-Demo-Actor-Id": actor,
        "X-Nexus-Demo-Reason": reason,
        "X-Nexus-Trace-Id": trace,
    }


def require(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def find_item(items: Any, item_id: str) -> dict[str, Any]:
    for item in items or []:
        if pick(item, "id") == item_id:
            return item
    raise AssertionError(f"missing {item_id} in {items}")


def assert_fields(row: dict[str, Any], fields: dict[str, str], prefix: str) -> None:
    for field, expected in fields.items():
        actual = pick(row, field)
        require(str(actual) == expected, f"{prefix} {field}={actual} expected {expected}")


def walk_type(
    api: Api,
    spec: dict[str, Any],
    suffix: str,
    admin_token: str,
    admin_ctx: str,
    reviewer_token: str,
    reviewer_ctx: str,
    reviewer_person_id: str,
) -> dict[str, Any]:
    code = spec["code"]
    trace = str(uuid.uuid4())
    iam = auth_headers(admin_token, admin_ctx, trace)
    provisioned = api.request(
        "POST",
        f"/api/v1/p3/business-types/{code}/provision",
        {"publish": True, "suffix": f"{code}{suffix}"},
        headers=iam,
        expected=(200, 201),
    )[1]
    form_id = pick(provisioned, "form_id", "formId")
    ledger_id = pick(provisioned, "ledger_id", "ledgerId")
    physical = pick(provisioned, "physical_table", "physicalTable")
    require(form_id and ledger_id, provisioned)
    require(physical and spec["table_needle"] in physical, provisioned)
    record(f"{code} catalog provision", "PASS", f"{physical} {ledger_id}")

    ledgers = api.request("GET", "/api/v1/p3/ledgers", headers=iam, expected=200)[1]
    require(any(pick(row, "id") == ledger_id for row in ledgers), ledgers)
    ledger = api.request("GET", f"/api/v1/p3/ledgers/{ledger_id}", headers=iam, expected=200)[1]
    require(pick(ledger, "id") == ledger_id, ledger)
    record(f"{code} ledger list/detail", "PASS", pick(ledger, "ledger_code", "ledgerCode"))

    def start(values: dict[str, Any]) -> dict[str, Any]:
        started = api.request(
            "POST",
            "/api/v1/p3/path/submit-start",
            {
                "form_id": form_id,
                "process_key": "nexusSequentialApproval",
                "assignee_type": "PERSON",
                "assignee_ref": str(reviewer_person_id),
                "values": values,
            },
            headers=auth_headers(admin_token, admin_ctx, trace),
            expected=200,
        )[1]
        require(pick(started, "approval_status", "approvalStatus") == "IN_APPROVAL", started)
        return started

    primary = start(spec["values"])
    other = start(spec["other"])
    record_id = pick(primary, "record_id", "recordId")
    other_id = pick(other, "record_id", "recordId")
    instance_id = pick(pick(primary, "process"), "id")
    todo_id = pick(pick(primary, "todo"), "id")
    other_todo = pick(pick(other, "todo"), "id")
    record(f"{code} submit-start x2", "PASS", f"primary={record_id} other={other_id}")

    listed = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records",
        headers=iam,
        expected=200,
    )[1]
    require(pick(listed, "total") == 2, listed)
    listed_primary = find_item(pick(listed, "items"), record_id)
    require(pick(listed_primary, "approval_status", "approvalStatus") == "IN_APPROVAL", listed_primary)
    assert_fields(listed_primary, spec["query"], f"{code} list")
    record(f"{code} record list", "PASS", "total=2 type fields on primary")

    query = urlencode({**spec["query"], spec["filter_field"]: spec["filter_value"]})
    filtered = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records?{query}",
        headers=iam,
        expected=200,
    )[1]
    require(pick(filtered, "total") == 1, filtered)
    require(pick(pick(filtered, "items")[0], "id") == record_id, filtered)
    assert_fields(pick(filtered, "items")[0], spec["query"], f"{code} query")
    record(f"{code} field-equals query", "PASS", f"{spec['filter_field']}={spec['filter_value']}")

    missed = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records?{spec['filter_field']}={spec['miss_value']}",
        headers=iam,
        expected=200,
    )[1]
    require(pick(missed, "total") == 0, missed)
    record(f"{code} miss query empty", "PASS", f"{spec['filter_field']}={spec['miss_value']}")

    row = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records/{record_id}",
        headers=iam,
        expected=200,
    )[1]
    require(pick(row, "approval_status", "approvalStatus") == "IN_APPROVAL", row)
    assert_fields(row, spec["query"], f"{code} detail")
    record(f"{code} record detail", "PASS", str(record_id))

    rev = auth_headers(reviewer_token, reviewer_ctx, trace)
    pending = api.request("GET", "/api/v1/todos/pending", headers=rev, expected=200)[1]
    require(pick(find_item(pending, todo_id), "status") == "PENDING", pending)
    alias = api.request("GET", "/api/v1/p3/todos", headers=rev, expected=200)[1]
    require(pick(find_item(alias, todo_id), "instance_id", "instanceId") == instance_id, alias)
    todo = api.request("GET", f"/api/v1/todos/{todo_id}", headers=rev, expected=200)[1]
    require(pick(todo, "status") == "PENDING", todo)
    require(pick(todo, "instance_id", "instanceId") == instance_id, todo)
    initiated = api.request("GET", "/api/v1/todos/initiated", headers=iam, expected=200)[1]
    require(pick(find_item(initiated, instance_id), "status") == "RUNNING", initiated)
    record(f"{code} todo pending/alias/detail/initiated", "PASS", str(todo_id))

    handled = api.request(
        "POST",
        f"/api/v1/p3/path/tasks/{todo_id}/{spec['handle']}",
        {},
        headers=rev,
        expected=200,
    )[1]
    require(pick(handled, "approval_status", "approvalStatus") == spec["expected"], handled)
    after_list = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records",
        headers=iam,
        expected=200,
    )[1]
    after_primary = find_item(pick(after_list, "items"), record_id)
    require(pick(after_primary, "approval_status", "approvalStatus") == spec["expected"], after_primary)
    assert_fields(after_primary, spec["query"], f"{code} list after handle")
    after_detail = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records/{record_id}",
        headers=iam,
        expected=200,
    )[1]
    require(pick(after_detail, "approval_status", "approvalStatus") == spec["expected"], after_detail)
    done = api.request("GET", "/api/v1/todos/done", headers=rev, expected=200)[1]
    require(pick(find_item(done, todo_id), "status") == "DONE", done)
    still_pending = api.request("GET", "/api/v1/todos/pending", headers=rev, expected=200)[1]
    require(any(pick(item, "id") == other_todo for item in still_pending), still_pending)
    require(all(pick(item, "id") != todo_id for item in still_pending), still_pending)
    record(f"{code} query after {spec['handle']}", "PASS", spec["expected"])

    return {
        "type": code,
        "form_id": form_id,
        "ledger_id": ledger_id,
        "physical_table": physical,
        "record_id": record_id,
        "other_record_id": other_id,
        "instance_id": instance_id,
        "todo_id": todo_id,
        "trace": trace,
        "after_status": spec["expected"],
    }


def run(base: str, bootstrap_token: str, actor: str) -> dict[str, Any]:
    api = Api(base)
    suffix = time.strftime("%H%M%S")
    setup_trace = str(uuid.uuid4())

    status, health = api.request("GET", "/actuator/health")
    require(status == 200 and pick(health, "status") == "UP", f"health {health}")
    record("actuator health", "PASS", "UP")

    p1h = platform_headers(actor, "g7-expense-travel-query", setup_trace)
    tenant = api.request(
        "POST",
        "/api/v1/p1/tenants",
        {
            "name": f"G7 Expense Travel Query {suffix}",
            "code": f"g7q{suffix}",
            "legal_name": f"G7 Expense Travel Query {suffix} Ltd",
            "admin": {"display_name": f"Admin g7q{suffix}", "email": f"g7q{suffix}@example.com"},
            "config": {"locale": "zh-CN", "timezone": "Asia/Shanghai", "currency": "CNY"},
        },
        headers=p1h,
        expected=(200, 201),
    )[1]
    tenant_id = pick(pick(tenant, "tenant"), "id") or pick(tenant, "id")
    tenant_code = pick(pick(tenant, "tenant"), "code") or pick(tenant, "code")
    require(tenant_id, tenant)
    record("P1 tenant", "PASS", f"{tenant_code} {tenant_id}")

    require(bootstrap_token, "NEXUS_PLATFORM_BOOTSTRAP_TOKEN unset")
    admin = api.request(
        "POST",
        f"/api/v1/internal/tenants/{tenant_id}/admin-users",
        {
            "display_name": "G7 Expense Travel Query Admin",
            "email": f"g7-q-admin-{suffix}@example.com",
            "username": f"g7-q-admin-{suffix}@example.com",
        },
        headers={"X-Nexus-Bootstrap-Token": bootstrap_token},
        expected=200,
    )[1]
    admin_email = f"g7-q-admin-{suffix}@example.com"
    admin_token = stub_token(pick(admin, "keycloak_user_id"), admin_email, admin_email, "G7 Expense Travel Query Admin")
    admin_sess = api.request("POST", "/api/v1/auth/login", {"access_token": admin_token}, expected=200)[1]
    admin_ctx = pick(admin_sess, "context_id", "contextId")
    require(admin_ctx, admin_sess)
    record("stub login + issued context", "PASS", str(admin_ctx))

    reviewer_email = f"g7-q-rev-{suffix}@example.com"
    reviewer_token = stub_token(f"g7-q-rev-{suffix}", reviewer_email, reviewer_email, "G7 Expense Travel Query Reviewer")
    reviewer_login = api.request(
        "POST", "/api/v1/auth/login", {"access_token": reviewer_token}, expected=200
    )[1]
    reviewer_account = pick(reviewer_login, "account_id", "accountId")
    api.request(
        "POST",
        "/api/v1/memberships",
        {"account_id": reviewer_account},
        headers=auth_headers(admin_token, admin_ctx),
        expected=200,
    )
    reviewer_person = api.request(
        "POST",
        "/api/v1/persons",
        {
            "display_name": "G7 Expense Travel Query Reviewer",
            "email": reviewer_email,
            "account_id": reviewer_account,
        },
        headers=auth_headers(admin_token, admin_ctx),
        expected=200,
    )[1]
    reviewer_person_id = pick(reviewer_person, "id")
    api.request(
        "POST",
        "/api/v1/auth/tenant-switch",
        {"tenant_id": tenant_id},
        headers=auth_headers(reviewer_token),
        expected=200,
    )
    reviewer_sess = api.request("POST", "/api/v1/auth/login", {"access_token": reviewer_token}, expected=200)[1]
    reviewer_ctx = pick(reviewer_sess, "context_id", "contextId")
    if not reviewer_ctx:
        switched = api.request(
            "POST",
            "/api/v1/auth/tenant-switch",
            {"tenant_id": tenant_id},
            headers=auth_headers(reviewer_token),
            expected=200,
        )[1]
        reviewer_ctx = pick(switched, "context_id", "contextId")
    require(reviewer_ctx, reviewer_sess)
    record("reviewer membership", "PASS", str(reviewer_person_id))

    walks = [
        walk_type(
            api,
            spec,
            suffix,
            admin_token,
            admin_ctx,
            reviewer_token,
            reviewer_ctx,
            str(reviewer_person_id),
        )
        for spec in TYPES
    ]
    return {
        "tenant_id": tenant_id,
        "tenant_code": tenant_code,
        "walks": walks,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="G7 expense + travel list/detail/todo query live HTTP")
    parser.add_argument("--base", default=os.environ.get("NEXUS_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument(
        "--bootstrap-token",
        default=os.environ.get("NEXUS_PLATFORM_BOOTSTRAP_TOKEN", ""),
    )
    parser.add_argument(
        "--actor",
        default=os.environ.get("NEXUS_P1_DEMO_ACTOR_ID", "01900000-0000-7000-8000-0000000000ac"),
    )
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()
    evidence: dict[str, Any] = {}
    try:
        evidence = run(args.base, args.bootstrap_token, args.actor)
    except Exception as ex:  # noqa: BLE001
        record("script aborted", "FAIL", str(ex))
    print("\n## G7 expense + travel query/list (live HTTP)")
    print("| Item | Status | Detail |")
    print("|---|---|---|")
    for row in ROWS:
        print(f"| {row['item']} | {row['status']} | {row['detail'].replace('|', '/')} |")
    print(f"\nPASS={PASS} FAIL={FAIL}")
    payload = {
        "pass": PASS,
        "fail": FAIL,
        "rows": ROWS,
        "evidence": evidence,
        "base": args.base,
        "business": ["expense", "travel"],
    }
    if args.json_out:
        os.makedirs(os.path.dirname(args.json_out) or ".", exist_ok=True)
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
            fh.write("\n")
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
