"""P0-02 permission semantics PoC. No database or SQL renderer dependency."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import IntEnum
from typing import Callable


@dataclass(frozen=True)
class Predicate:
    """Abstract row predicate; P0-01 must translate this tree to its Query AST."""

    op: str
    args: tuple[object, ...] = ()

    @staticmethod
    def atom(column: str, operator: str, value: object) -> Predicate:
        return Predicate("atom", (column, operator, value))

    @staticmethod
    def any_of(*items: Predicate) -> Predicate:
        items = tuple(item for item in items if item.op != "false")
        if any(item.op == "true" for item in items):
            return Predicate("true")
        return Predicate("or", items) if len(items) > 1 else items[0] if items else Predicate("false")

    @staticmethod
    def all_of(*items: Predicate) -> Predicate:
        items = tuple(item for item in items if item.op != "true")
        if any(item.op == "false" for item in items):
            return Predicate("false")
        return Predicate("and", items) if len(items) > 1 else items[0] if items else Predicate("true")


class Mask(IntEnum):
    NONE = 0
    PARTIAL = 1
    FULL = 2


@dataclass(frozen=True)
class FieldRule:
    visible: bool = False
    editable: bool = False
    mask: Mask = Mask.NONE
    second_authorization: bool = False


@dataclass(frozen=True)
class Subject:
    tenant_id: str
    user_id: str
    roles: frozenset[str] = frozenset()
    positions: frozenset[str] = frozenset()
    departments: frozenset[str] = frozenset()


@dataclass(frozen=True)
class Rule:
    # Principal type is user, role, position, or department.
    principal_type: str
    principal_id: str
    resource: str
    action: str
    scope: Predicate = Predicate("true")
    fields: dict[str, FieldRule] = field(default_factory=dict)


@dataclass(frozen=True)
class PolicySnapshot:
    tenant_id: str
    version: int
    grants: tuple[Rule, ...] = ()
    denies: tuple[Rule, ...] = ()
    # child role -> parent roles
    role_parents: dict[str, frozenset[str]] = field(default_factory=dict)


@dataclass(frozen=True)
class Decision:
    allowed: bool
    reason: str
    scope: Predicate = Predicate("false")
    fields: dict[str, FieldRule] = field(default_factory=dict)
    policy_version: int | None = None


def _principals(subject: Subject, snapshot: PolicySnapshot) -> set[tuple[str, str]]:
    roles = set(subject.roles)
    pending = list(roles)
    while pending:
        for parent in snapshot.role_parents.get(pending.pop(), frozenset()):
            if parent not in roles:
                roles.add(parent)
                pending.append(parent)
    return ({("user", subject.user_id)}
            | {("role", role) for role in roles}
            | {("position", position) for position in subject.positions}
            | {("department", department) for department in subject.departments})


def evaluate(
    subject: Subject,
    resource_tenant_id: str,
    resource: str,
    action: str,
    snapshot: PolicySnapshot,
    *,
    record_shares: tuple[Predicate, ...] = (),
    context: Predicate = Predicate("true"),
) -> Decision:
    """Evaluate permission and return an abstract row filter, never raw SQL."""
    if subject.tenant_id != resource_tenant_id or snapshot.tenant_id != subject.tenant_id:
        return Decision(False, "tenant_boundary")
    principals = _principals(subject, snapshot)

    def matches(rule: Rule) -> bool:
        return (rule.resource == resource and rule.action == action
                and (rule.principal_type, rule.principal_id) in principals)

    if any(matches(rule) for rule in snapshot.denies):
        return Decision(False, "explicit_deny", policy_version=snapshot.version)
    grants = [rule for rule in snapshot.grants if matches(rule)]
    if not grants:
        return Decision(False, "no_grant", policy_version=snapshot.version)
    scope = Predicate.all_of(
        Predicate.any_of(*(rule.scope for rule in grants), *record_shares), context
    )
    if scope.op == "false":
        return Decision(False, "empty_scope", policy_version=snapshot.version)
    field_names = set().union(*(rule.fields.keys() for rule in grants))
    fields = {}
    for name in field_names:
        rules = [rule.fields[name] for rule in grants if name in rule.fields]
        visible = any(rule.visible for rule in rules)
        fields[name] = FieldRule(
            visible=visible,
            editable=visible and any(rule.editable for rule in rules),
            mask=max(rule.mask for rule in rules),
            second_authorization=any(rule.second_authorization for rule in rules),
        )
    return Decision(True, "allowed", scope, fields, snapshot.version)


class SnapshotUnavailable(RuntimeError):
    pass


class SnapshotManager:
    """DB version is authoritative. Notification only asks for an early poll."""

    def __init__(self, version_reader: Callable[[str], int],
                 snapshot_loader: Callable[[str], PolicySnapshot]):
        self.version_reader = version_reader
        self.snapshot_loader = snapshot_loader
        self._cache: dict[str, PolicySnapshot] = {}
        self._invalidated: set[str] = set()

    def notify(self, tenant_id: str) -> None:
        self._invalidated.add(tenant_id)

    def get(self, tenant_id: str, *, high_risk_write: bool = False) -> PolicySnapshot:
        try:
            version = self.version_reader(tenant_id)
        except Exception as exc:
            # The PoC does not establish a bounded-staleness clock, so fail closed.
            raise SnapshotUnavailable("policy version unavailable") from exc
        cached = self._cache.get(tenant_id)
        if cached is None or cached.version < version or tenant_id in self._invalidated:
            try:
                loaded = self.snapshot_loader(tenant_id)
            except Exception as exc:
                raise SnapshotUnavailable("policy snapshot unavailable") from exc
            if loaded.tenant_id != tenant_id or loaded.version < version:
                raise SnapshotUnavailable("policy snapshot is stale")
            self._cache[tenant_id] = loaded
            self._invalidated.discard(tenant_id)
            cached = loaded
        if cached.version > version:
            raise SnapshotUnavailable("policy version regressed")
        return cached
