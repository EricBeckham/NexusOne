package one.nexus.p3.portal;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p3.instance.InstanceConsumptionStore;
import one.nexus.p3.instance.InstanceReconciler;
import one.nexus.p3.instance.InstanceSnapshot;
import one.nexus.p3.instance.ProcessStartGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** PLX1 C1: visible entries are installed, well-formed, and marked readable. */
@Service
public class PortalEntryService {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");

    private final InstalledEntrySource installed;
    private final EntryReadFilter readable;
    private final ProcessStartGate starts;
    private final InstanceReconciler reconciler;
    private final InstanceConsumptionStore pins;

    public PortalEntryService(InstalledEntrySource installed, EntryReadFilter readable, ProcessStartGate starts) {
        this(installed, readable, starts, null, null);
    }

    @Autowired
    public PortalEntryService(InstalledEntrySource installed, EntryReadFilter readable, ProcessStartGate starts,
            InstanceReconciler reconciler, InstanceConsumptionStore pins) {
        this.installed = installed;
        this.readable = readable;
        this.starts = starts;
        this.reconciler = reconciler;
        this.pins = pins;
    }

    public List<PortalEntry> visible(UUID tenantId) {
        if (reconciler != null) {
            reconciler.reconcile(tenantId);
        }
        Map<String, FunctionEntry> byKey = new LinkedHashMap<>();
        if (pins != null) {
            int sort = 0;
            for (InstanceSnapshot snapshot : pins.list(tenantId)) {
                if (snapshot == null || !snapshot.installed()) {
                    continue;
                }
                for (String key : snapshot.entries()) {
                    FunctionEntry entry = fromKey(key, sort++);
                    if (entry != null) {
                        byKey.putIfAbsent(entry.key(), entry);
                    }
                }
            }
        }
        for (FunctionEntry entry : installed.installed(tenantId)) {
            if (wellFormed(entry)) {
                byKey.put(entry.key(), entry);
            }
        }
        List<String> keys = List.copyOf(byKey.keySet());
        List<String> allowed = readable.readable(keys);
        Set<String> visible = new HashSet<>(allowed == null ? List.of() : allowed);
        return byKey.values().stream()
                .filter(entry -> visible.contains(entry.key()))
                .sorted(Comparator.comparing(FunctionEntry::defaultGroup, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(FunctionEntry::sort)
                        .thenComparing(FunctionEntry::key))
                .map(entry -> present(tenantId, entry))
                .toList();
    }

    static FunctionEntry fromKey(String key, int sort) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split(":", -1);
        if (parts.length != 3 || !"fn".equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        return new FunctionEntry(key, parts[1], parts[2], parts[1], parts[2], "grid", EntryKind.FILL, parts[1], sort, null);
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
