package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService;
import org.junit.jupiter.api.Test;

class FormTemplateControllerTest {
    @Test
    void tenantOrNonPlatformContextCannotReadOrWriteTemplateDrafts() {
        IsolationGuard isolation = mock(IsolationGuard.class);
        FormTemplateDraftService service = mock(FormTemplateDraftService.class);
        when(isolation.requirePlatformContext("form-template.list", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new DomainException(ErrorCode.CONTEXT_PLATFORM_REQUIRED, "platform context required"));
        when(isolation.requirePlatformContext("form-template.create", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new DomainException(ErrorCode.DUTY_DENIED, "platform duty required"));
        when(isolation.requirePlatformContext("form-template.get", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new DomainException(ErrorCode.CONTEXT_PLATFORM_REQUIRED, "platform context required"));
        when(isolation.requirePlatformContext("form-template.update", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new DomainException(ErrorCode.DUTY_DENIED, "platform duty required"));
        when(isolation.requirePlatformContext("form-template.validate", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new DomainException(ErrorCode.DUTY_DENIED, "platform duty required"));
        when(isolation.requirePlatformContext("form-template.publish", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new DomainException(ErrorCode.CONTEXT_PLATFORM_REQUIRED, "platform context required"));
        FormTemplateController controller = new FormTemplateController(isolation, service);

        assertEquals(ErrorCode.CONTEXT_PLATFORM_REQUIRED, assertThrows(DomainException.class, controller::list).errorCode());
        var write = new FormTemplateDraftService.TemplateWrite("travel", "Travel", null, List.of());
        assertEquals(ErrorCode.DUTY_DENIED, assertThrows(DomainException.class, () -> controller.create(write)).errorCode());
        assertEquals(ErrorCode.CONTEXT_PLATFORM_REQUIRED,
                assertThrows(DomainException.class, () -> controller.get(UUID.randomUUID())).errorCode());
        assertEquals(ErrorCode.DUTY_DENIED,
                assertThrows(DomainException.class, () -> controller.update(UUID.randomUUID(), write)).errorCode());
        assertEquals(ErrorCode.DUTY_DENIED,
                assertThrows(DomainException.class, () -> controller.validate(UUID.randomUUID())).errorCode());
        assertEquals(ErrorCode.CONTEXT_PLATFORM_REQUIRED,
                assertThrows(DomainException.class, () -> controller.publish(UUID.randomUUID())).errorCode());
        verifyNoInteractions(service);
    }
}
