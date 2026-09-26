package one.nexus.p1.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantRegistrationApplicationTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void pendingRejectAndApproveTransitions() {
        TenantRegistrationApplication pending = TenantRegistrationApplication.pending(
                UUID.randomUUID(), "示例科技", "张三", "zhang@example.com", " 请尽快开通 ", NOW);
        assertThat(pending.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(pending.contactNote()).isEqualTo("请尽快开通");
        assertThat(pending.adminEmail()).isEqualTo("zhang@example.com");

        UUID actor = UUID.randomUUID();
        TenantRegistrationApplication rejected = pending.reject(NOW.plusSeconds(1), actor, "材料不全");
        assertThat(rejected.status()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(rejected.reviewNote()).isEqualTo("材料不全");
        assertThatThrownBy(() -> rejected.reject(NOW, actor, "again"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not pending");

        TenantRegistrationApplication again = TenantRegistrationApplication.pending(
                UUID.randomUUID(), "示例科技", "李四", "li@example.com", null, NOW);
        UUID tenantId = UUID.randomUUID();
        TenantRegistrationApplication approved = again.approve(tenantId, NOW.plusSeconds(2), actor, "ok");
        assertThat(approved.status()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(approved.resultingTenantId()).isEqualTo(tenantId);
    }

    @Test
    void validationRejectsBlankOrgOrBadEmail() {
        assertThatThrownBy(() -> TenantRegistrationApplication.pending(
                        UUID.randomUUID(), " ", "张三", "a@b.com", null, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TenantRegistrationApplication.pending(
                        UUID.randomUUID(), "Org", "张三", "not-an-email", null, NOW))
                .isInstanceOf(DomainException.class);
    }
}