package one.nexus.p3.portal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p3.instance.ProcessStartGate;
import org.springframework.stereotype.Service;

/** PLX1 C1: visible entries are installed, well-formed, and marked readable. */
@Service
public class PortalEntryService {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");

    private final InstalledEntrySource installed;
    private final EntryReadFilter readable;
    private final ProcessStartGate starts;

    public PortalEntryService(InstalledEntrySource installed, EntryReadFilter readable, ProcessStartGate starts) {
        this.installed = installed;
        this.readable = readable;
        this.starts = starts;
    }

    public List<PortalEntry> visible(UUID tenantId) {
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
                .map(entry -> present(tenantId, entry))
                .toList();
    }

    private PortalEntry present(UUID tenantId, FunctionEntry entry) {
        String processKey = blank(entry.processKey()) ? null : entry.processKey();
        Optional<String> blocked = entry.kind() == EntryKind.FILL && processKey != null
                ? starts.blockReason(tenantId, processKey)
                : Optional.empty();
        return new PortalEntry(entry.key(), entry.name(), entry.icon(), entry.kind(), entry.defaultGroup(),
                entry.sort(), processKey, blocked.isEmpty(), blocked.orElse(null));
    }

    static boolean wellFormed(FunctionEntry entry) {
        if (entry == null || entry.kind() == null || entry.name() == null || entry.name().isBlank()) {
            return false;
        }
        if (entry.appCode() == null || entry.formCode() == null || entry.ledgerCode() == null || entry.ledgerCode().isBlank()) {
            return false;
        }
        if (entry.key() == null || !entry.key().equals("fn:" + entry.appCode() + ":" + entry.formCode())) {
            return false;
        }
        String processKey = entry.processKey();
        if (entry.kind() == EntryKind.FILL) {
            return processKey == null || processKey.isBlank() || CODE.matcher(processKey).matches();
        }
        return processKey == null || processKey.isBlank();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
