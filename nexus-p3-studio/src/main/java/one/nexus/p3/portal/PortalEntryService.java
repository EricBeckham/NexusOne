package one.nexus.p3.portal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** PLX1 C1: visible entries are installed, well-formed, and marked readable. */
@Service
public class PortalEntryService {
    private final InstalledEntrySource installed;
    private final EntryReadFilter readable;

    public PortalEntryService(InstalledEntrySource installed, EntryReadFilter readable) {
        this.installed = installed;
        this.readable = readable;
    }

    public List<FunctionEntry> visible(UUID tenantId) {
        List<FunctionEntry> candidates = new ArrayList<>();
        for (FunctionEntry entry : installed.installed(tenantId)) {
            if (wellFormed(entry)) {
                candidates.add(entry);
            }
        }
        List<String> keys = candidates.stream().map(FunctionEntry::key).toList();
        List<String> allowed = readable.readable(keys);
        Set<String> visible = new HashSet<>(allowed == null ? List.of() : allowed);
        return candidates.stream()
                .filter(entry -> visible.contains(entry.key()))
                .sorted(Comparator.comparing(FunctionEntry::defaultGroup, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(FunctionEntry::sort)
                        .thenComparing(FunctionEntry::key))
                .toList();
    }

    static boolean wellFormed(FunctionEntry entry) {
        if (entry == null || entry.kind() == null || entry.name() == null || entry.name().isBlank()) {
            return false;
        }
        if (entry.appCode() == null || entry.formCode() == null || entry.ledgerCode() == null || entry.ledgerCode().isBlank()) {
            return false;
        }
        return entry.key() != null && entry.key().equals("fn:" + entry.appCode() + ":" + entry.formCode());
    }
}
