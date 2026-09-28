package one.nexus.p3.portal;

/**
 * PLX1 C1 display metadata. {@code read} decides whether this entry is listed.
 * Operating the form still checks {@code fn.*} on that entry's own API.
 */
public record FunctionEntry(
        String key,
        String appCode,
        String formCode,
        String ledgerCode,
        String name,
        String icon,
        EntryKind kind,
        String defaultGroup,
        int sort
) {}
