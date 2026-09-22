/**
 * Append-only audit capture, hash-chain API, trace correlation helpers,
 * same-PG archive, object-store cold archive, and recovery dry-run ports.
 * Business modules emit events through {@code AuditPort}; this module does
 * not invent a second log stream. Cold archive never DELETE/UPDATE the hot chain.
 */
package one.nexus.audit;
