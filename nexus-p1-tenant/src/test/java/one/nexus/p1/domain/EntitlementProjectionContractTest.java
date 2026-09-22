package one.nexus.p1.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class EntitlementProjectionContractTest {

    @Test
    void defaultPackMatchesP4RuntimeProjectionShape() {
        EntitlementPayload payload = EntitlementPayload.defaultPack(5, 10);
        assertThat(payload.schema()).isEqualTo("p4.runtime-entitlement.v1");
        assertThat(payload.entitlements()).containsOnlyKeys("apps", "custom_ledgers");
        assertThat(payload.entitlements().get("apps").enabled()).isTrue();
        assertThat(payload.entitlements().get("apps").quota()).isEqualTo(5);
        assertThat(payload.entitlements().get("custom_ledgers").quota()).isEqualTo(10);
    }
}
