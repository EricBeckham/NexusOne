package one.nexus.p1.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantLifecycleDomainTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-00000000000a");

    @Test
    void businessTenantCanStopAndEnable() {
        Tenant tenant = business();
        Tenant stopped = tenant.disable(NOW, ACTOR);
        assertThat(stopped.status()).isEqualTo(TenantStatus.DISABLED);
        assertThat(stopped.enable(NOW.plusSeconds(1), ACTOR).status()).isEqualTo(TenantStatus.ENABLED);
    }

    @Test
    void platformTenantCannotBeDisabled() {
        Tenant platform = new Tenant(
                UUID.fromString("01900000-0000-7000-8000-000000000001"),
                "platform",
                "NexusOne Platform",
                TenantKind.PLATFORM,
                TenantStatus.ENABLED,
                "NexusOne Platform",
                NOW, ACTOR, NOW, ACTOR, null, 1);
        assertThatThrownBy(() -> platform.disable(NOW, ACTOR))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.errorCode()).isEqualTo(ErrorCode.TENANT_PLATFORM_PROTECTED);
                    assertThat(de.details()).containsEntry("kind", "PLATFORM");
                });
        assertThat(platform.status()).isEqualTo(TenantStatus.ENABLED);
    }

    private static Tenant business() {
        return new Tenant(
                UUID.fromString("01900000-0000-7000-8000-0000000000aa"),
                "acme",
                "Acme",
                TenantKind.BUSINESS,
                TenantStatus.ENABLED,
                "Acme Ltd",
                NOW, ACTOR, NOW, ACTOR, null, 1);
    }
}
