package one.nexus.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Eligibility for the 90-day hot-prefix purge. A row may leave the hot table
 * only when it is older than {@link AuditRetentionPolicy#HOT_RETENTION} and a
 * durable copy still exists:
 *
 * <ul>
 *   <li>object-store cold archive key exists, or
 *   <li>same-PG archive row exists <em>and</em> {@code occurred_at} is still
 *       inside {@link AuditRetentionPolicy#ARCHIVE_RETENTION} so a later
 *       365-day archive expiry cannot orphan the event.
 * </ul>
 *
 * Only a contiguous prefix from the lowest remaining {@code chain_index} is
 * deleted. A younger (or uncopied) row stops the walk so the live suffix stays
 * gapless.
 */
public final class AuditHotPurgePolicy {
    private AuditHotPurgePolicy() {}

    public static boolean eligible(
            ChainedAuditEvent linked, Instant now, Set<UUID> archivedIds, AuditObjectStore cold) {
        Objects.requireNonNull(linked, "linked");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(archivedIds, "archivedIds");
        Instant occurred = linked.event().occurredAt();
        if (!occurred.isBefore(AuditRetentionPolicy.hotCutoff(now))) {
            return false;
        }
        if (cold != null && cold.exists(AuditColdObjectKeys.objectKey(linked))) {
            return true;
        }
        UUID id = linked.event().eventId();
        return archivedIds.contains(id) && !occurred.isBefore(AuditRetentionPolicy.archiveExpiry(now));
    }

    public static List<ChainedAuditEvent> eligiblePrefix(
            List<ChainedAuditEvent> hot, Instant now, Set<UUID> archivedIds, AuditObjectStore cold) {
        Objects.requireNonNull(hot, "hot");
        if (hot.isEmpty()) {
            return List.of();
        }
        List<ChainedAuditEvent> prefix = new ArrayList<>();
        long expected = hot.getFirst().chainIndex();
        for (ChainedAuditEvent linked : hot) {
            if (linked.chainIndex() != expected) {
                break;
            }
            if (!eligible(linked, now, archivedIds, cold)) {
                break;
            }
            prefix.add(linked);
            expected++;
        }
        return List.copyOf(prefix);
    }

    public static int countAged(List<ChainedAuditEvent> hot, Instant now) {
        Objects.requireNonNull(hot, "hot");
        Objects.requireNonNull(now, "now");
        Instant cutoff = AuditRetentionPolicy.hotCutoff(now);
        int aged = 0;
        for (ChainedAuditEvent linked : hot) {
            if (linked.event().occurredAt().isBefore(cutoff)) {
                aged++;
            }
        }
        return aged;
    }
}
