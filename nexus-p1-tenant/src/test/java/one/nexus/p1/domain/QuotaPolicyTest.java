package one.nexus.p1.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuotaPolicyTest {

    @Test
    void consumeWithinLimitThenReject() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        Quota quota = new Quota(
                UUID.fromString("01900000-0000-7000-8000-0000000000aa"),
                QuotaMetric.CUSTOM_LEDGER,
                2, 0, 80, Quota.POLICY_REJECT, now, 1);
        QuotaDecision first = quota.tryConsume(2, now);
        assertThat(first).isInstanceOf(QuotaDecision.Accepted.class);
        Quota full = ((QuotaDecision.Accepted) first).quota();
        QuotaDecision rejected = full.tryConsume(1, now);
        assertThat(rejected).isInstanceOf(QuotaDecision.Rejected.class);
        QuotaDecision.Rejected r = (QuotaDecision.Rejected) rejected;
        assertThat(r.quota().usedValue()).isEqualTo(2);
        assertThat(r.requested()).isEqualTo(1);
        assertThat(full.snapshot()).containsEntry("policy", "REJECT");
    }
}
