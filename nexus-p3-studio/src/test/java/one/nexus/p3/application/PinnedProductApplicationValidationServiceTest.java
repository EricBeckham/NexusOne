package one.nexus.p3.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import one.nexus.file.FileObjectRepository;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p3.port.ProductApplicationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

/** P2: the service always wires; without identity/file repositories PERSON and ATTACHMENT fail closed. */
class PinnedProductApplicationValidationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID installationId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final ProductApplicationStore store = mock(ProductApplicationStore.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void tenant() {
        AccessContextHolder.set(new TenantContext(tenantId.toString(), Actor.person("member-1"), "membership-1",
                "trace-1", Instant.now()));
        when(store.findInstalledPin(tenantId, installationId)).thenReturn(Optional.of(
                new ProductApplicationStore.InstalledPin(versionId, UUID.randomUUID(), "demoSimpleApproval", 1)));
    }

    @AfterEach void clear() {
        AccessContextHolder.clear();
    }

    @Test void personAndAttachmentValuesAreRejectedWhenRepositoriesAreAbsent() throws Exception {
        when(store.formTemplateSnapshot(versionId)).thenReturn(Optional.of("""
                [{"key":"approver","type":"PERSON","required":true},
                 {"key":"proof","type":"ATTACHMENT","required":true}]"""));
        var result = service().validate(installationId, mapper.readTree(
                "{\"approver\":\"" + UUID.randomUUID() + "\",\"proof\":\"" + UUID.randomUUID() + "\"}"));
        assertFalse(result.valid());
        assertTrue(result.errors().size() >= 2, result.errors()::toString);
    }

    @Test void textOnlyTemplateStillValidatesWithoutRepositories() throws Exception {
        when(store.formTemplateSnapshot(versionId)).thenReturn(Optional.of("""
                [{"key":"title","type":"TEXT","required":true}]"""));
        assertTrue(service().validate(installationId, mapper.readTree("{\"title\":\"hello\"}")).valid());
    }

    @SuppressWarnings("unchecked")
    private PinnedProductApplicationValidationService service() {
        ObjectProvider<IdentityRepository> identities = mock(ObjectProvider.class);
        ObjectProvider<FileObjectRepository> files = mock(ObjectProvider.class);
        return new PinnedProductApplicationValidationService(store, mapper, identities, files);
    }
}
