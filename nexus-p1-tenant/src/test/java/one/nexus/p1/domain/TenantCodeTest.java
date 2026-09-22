package one.nexus.p1.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class TenantCodeTest {

    @Test
    void acceptsP0IdentifierClass() {
        assertThat(TenantCode.requireValid("acme")).isEqualTo("acme");
        assertThat(TenantCode.requireValid("t_alpha_01")).isEqualTo("t_alpha_01");
    }

    @Test
    void rejectsInvalidAndReservedBusinessCodes() {
        assertThatThrownBy(() -> TenantCode.requireValid("Acme"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).errorCode())
                .isEqualTo(ErrorCode.TENANT_INVALID_CODE);
        assertThatThrownBy(() -> TenantCode.requireBusiness("platform"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TenantCode.requireValid("1abc"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void slugsNamesIntoIdentifierSpace() {
        assertThat(TenantCode.slugFromName("Acme Corp")).isEqualTo("acme_corp");
        assertThat(TenantCode.slugFromName("123 Co")).startsWith("t");
    }
}
