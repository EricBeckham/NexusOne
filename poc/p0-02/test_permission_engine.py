import unittest

from permission_engine import (
    FieldRule, Mask, PolicySnapshot, Predicate, Rule, SnapshotManager,
    SnapshotUnavailable, Subject, evaluate,
)


SELF = Predicate.atom("owner_id", "=", "u1")
DEPT = Predicate.atom("department_id", "=", "d1")
SHARE = Predicate.atom("id", "IN", ("r1",))
CONTEXT = Predicate.atom("status", "=", "active")


class PermissionEngineTests(unittest.TestCase):
    def setUp(self):
        self.subject = Subject("t1", "u1", frozenset({"employee"}),
                               frozenset({"manager"}), frozenset({"d1"}))

    def rule(self, kind="role", principal="employee", scope=SELF, fields=None):
        return Rule(kind, principal, "ledger", "read", scope, fields or {})

    def test_tenant_boundary_before_deny_and_grant(self):
        snapshot = PolicySnapshot("t1", 3, (self.rule(scope=Predicate("true")),),
                                  (self.rule(),))
        result = evaluate(self.subject, "t2", "ledger", "read", snapshot)
        self.assertEqual("tenant_boundary", result.reason)
        self.assertFalse(result.allowed)

    def test_explicit_deny_overrides_all_sources(self):
        snapshot = PolicySnapshot("t1", 3, (self.rule(),),
                                  (self.rule("department", "d1"),))
        self.assertEqual("explicit_deny", evaluate(
            self.subject, "t1", "ledger", "read", snapshot).reason)

    def test_user_role_position_department_and_inherited_role_union(self):
        grants = (
            self.rule("user", "u1", SELF),
            self.rule("role", "ancestor", DEPT),
            self.rule("position", "manager", SHARE),
            self.rule("department", "d1", Predicate.atom("id", "=", "r2")),
        )
        snapshot = PolicySnapshot("t1", 1, grants, role_parents={
            "employee": frozenset({"ancestor"}), "ancestor": frozenset({"employee"})
        })
        result = evaluate(self.subject, "t1", "ledger", "read", snapshot)
        self.assertTrue(result.allowed)
        self.assertEqual("or", result.scope.op)
        self.assertEqual(4, len(result.scope.args))

    def test_scope_or_share_then_intersect_context(self):
        snapshot = PolicySnapshot("t1", 1, (self.rule(scope=SELF),
                                              self.rule("position", "manager", DEPT)))
        result = evaluate(self.subject, "t1", "ledger", "read", snapshot,
                          record_shares=(SHARE,), context=CONTEXT)
        self.assertEqual(Predicate.all_of(Predicate.any_of(SELF, DEPT, SHARE), CONTEXT),
                         result.scope)

    def test_no_grant_denies_even_with_share(self):
        result = evaluate(self.subject, "t1", "ledger", "read",
                          PolicySnapshot("t1", 1), record_shares=(SHARE,))
        self.assertEqual("no_grant", result.reason)

    def test_field_union_and_strictest_mask_and_secondary_authorization(self):
        grants = (
            self.rule(fields={"phone": FieldRule(True, False, Mask.FULL, True),
                              "hidden": FieldRule(False, True)}),
            self.rule("position", "manager", fields={
                "phone": FieldRule(True, True, Mask.PARTIAL)}),
        )
        result = evaluate(self.subject, "t1", "ledger", "read",
                          PolicySnapshot("t1", 1, grants))
        self.assertEqual(FieldRule(True, True, Mask.FULL, True), result.fields["phone"])
        self.assertEqual(FieldRule(False, False), result.fields["hidden"])

    def test_empty_context_is_denied(self):
        result = evaluate(self.subject, "t1", "ledger", "read",
                          PolicySnapshot("t1", 1, (self.rule(),)),
                          context=Predicate("false"))
        self.assertEqual("empty_scope", result.reason)

    def test_snapshot_poll_detects_version_change_without_notification(self):
        versions = {"t1": 1}
        loaded = []

        def loader(tenant):
            loaded.append(versions[tenant])
            return PolicySnapshot(tenant, versions[tenant])

        manager = SnapshotManager(lambda tenant: versions[tenant], loader)
        self.assertEqual(1, manager.get("t1").version)
        versions["t1"] = 2
        self.assertEqual(2, manager.get("t1").version)
        manager.notify("t1")
        self.assertEqual(2, manager.get("t1").version)
        self.assertEqual([1, 2, 2], loaded)

    def test_stale_snapshot_or_missing_db_version_fails_closed(self):
        manager = SnapshotManager(lambda _: 2, lambda _: PolicySnapshot("t1", 1))
        with self.assertRaises(SnapshotUnavailable):
            manager.get("t1", high_risk_write=True)
        manager = SnapshotManager(lambda _: 1 / 0, lambda _: PolicySnapshot("t1", 1))
        with self.assertRaises(SnapshotUnavailable):
            manager.get("t1")


if __name__ == "__main__":
    unittest.main()
