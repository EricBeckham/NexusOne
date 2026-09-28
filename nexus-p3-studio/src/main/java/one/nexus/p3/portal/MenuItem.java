package one.nexus.p3.portal;

import java.util.UUID;

/** One row of the tenant portal menu. {@code entryKey} is empty for a group heading. */
public record MenuItem(
        UUID id,
        UUID parentId,
        String label,
        String entryKey,
        String section,
        int sort,
        boolean hidden
) {}
