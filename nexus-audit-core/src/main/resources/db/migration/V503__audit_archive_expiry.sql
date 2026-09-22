-- G4 remainder: 365-day archive expiry. DELETE is allowed on the copy table
-- only (GRANT to the migrating role). Hot platform.audit_record stays
-- append-only (V501 REVOKE DELETE). No object-store cold path. Application
-- code deletes archive rows by occurred_at older than ARCHIVE_RETENTION.

GRANT DELETE ON TABLE platform.audit_record_archive TO CURRENT_USER;
