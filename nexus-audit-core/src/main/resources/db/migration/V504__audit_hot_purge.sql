-- G4 leftover: 90-day hot-row purge. GRANT DELETE to the migrating / app role
-- so AuditHotPurge can remove a copied prefix from platform.audit_record.
-- UPDATE stays revoked. PUBLIC still has no DELETE (V501). Do NOT DROP the
-- hot table. Application code deletes only eligible prefix rows (older than
-- HOT_RETENTION and already cold-archived, or still inside the 365-day
-- same-PG archive window).

GRANT DELETE ON TABLE platform.audit_record TO CURRENT_USER;
