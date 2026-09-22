/**
 * Append-only audit capture, hash-chain API, trace correlation helpers,
 * export capture factories, a 90-day hot-retention scan stub, and recovery
 * dry-run ports. Business modules emit events through {@code AuditPort};
 * this module does not invent a second log stream. The retention Job does
 * not delete chain rows and does not implement the 365-day archive.
 */
package one.nexus.audit;
