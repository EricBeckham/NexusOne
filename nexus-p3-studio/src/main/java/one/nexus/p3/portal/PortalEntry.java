package one.nexus.p3.portal;

/** One visible portal row. {@code submittable} is false when the FILL process still needs a position mapping. */
public record PortalEntry(
        String key,
        String name,
        String icon,
        EntryKind kind,
        String defaultGroup,
        int sort,
        String processKey,
        boolean submittable,
        String blockedReason
) {}
