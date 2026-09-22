package one.nexus.p3.studio.meta;

import java.util.UUID;

/**
 * Logical ledger from M0-06. {@code ledgerCode} is the bind target for forms.
 * Physical table names ({@code l_*} / {@code a_*}) are intentionally absent.
 */
public record LedgerRef(UUID tenantId, String appCode, String ledgerCode, String name, boolean usable) {}
