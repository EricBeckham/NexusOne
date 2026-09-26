package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import one.nexus.p4.market.CommercialEntitlementService.CommercialEntitlement;
import one.nexus.p4.market.CommercialEntitlementService.EntitlementSummary;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** XB14: the entitlement projection read model's shape — what the subscription table (XB11) reads. */
class CommercialEntitlementSummaryContractTest {
    private static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void summaryShapeIsStable() {
        assertEquals(List.of("enabledCount", "totalCount", "projected", "projectionVersion", "projectedAt"),
                components(EntitlementSummary.class));
        assertEquals(Boolean.class, EntitlementSummary.class.getRecordComponents()[2].getType(),
                "projected must be nullable (unknown), not a primitive that defaults to false");
        List<String> view = components(CommercialEntitlement.class);
        assertEquals("subscriptionId", view.getFirst());
        assertEquals("summary", view.getLast());
    }

    @Test
    void noMoneyFieldsAnywhereInTheReadModel() {
        for (Class<?> record : List.of(CommercialEntitlement.class, EntitlementSummary.class, CommercialEntitlementService.EntitlementLine.class)) {
            for (String name : components(record)) {
                String lower = name.toLowerCase(Locale.ROOT);
                for (String forbidden : List.of("price", "amount", "fee", "invoice", "coupon", "billing", "payment")) {
                    assertFalse(lower.contains(forbidden), record.getSimpleName() + "." + name);
                }
            }
        }
    }

    @Test
    void listAndGetPathsAreTheOnesTheWebClientCalls() throws NoSuchMethodException {
        assertEquals(List.of("/api/v1/p4/commercial-entitlements"),
                List.of(CommercialEntitlementController.class.getAnnotation(RequestMapping.class).value()));
        assertEquals(List.of(), List.of(CommercialEntitlementController.class.getMethod("list").getAnnotation(GetMapping.class).value()));
        assertEquals(List.of("/{id}"),
                List.of(CommercialEntitlementController.class.getMethod("get", java.util.UUID.class).getAnnotation(GetMapping.class).value()));
    }
}