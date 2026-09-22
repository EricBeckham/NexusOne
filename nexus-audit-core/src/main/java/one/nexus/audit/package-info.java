/**
 * Append-only audit capture, hash-chain API, trace correlation helpers,
 * same-PG archive, object-store cold archive, 90-day hot-prefix purge, and
 * recovery dry-run ports. Business modules emit events through {@code AuditPort};
 * this module does not invent a second log stream. {@code AuditPort.append}
 * remains INSERT-only. Hot purge is a separate platform Job and never DROP
 * {@code platform.audit_record}.
 */
package one.nexus.audit;
