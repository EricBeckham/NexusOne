/**
 * Append-only audit capture, hash-chain API, trace correlation helpers, and
 * recovery dry-run ports. Business modules emit events through {@code AuditPort};
 * this module does not invent a second log stream.
 */
package one.nexus.audit;
