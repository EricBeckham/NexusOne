package one.nexus.p3.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProductApplicationValuesValidatorTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final UUID tenant=UUID.randomUUID(), person=UUID.randomUUID(), file=UUID.randomUUID();
    private final ProductApplicationValuesValidator.TenantValueOwnership ownership=new ProductApplicationValuesValidator.TenantValueOwnership() {
        @Override public boolean personBelongsToTenant(UUID tenantId,UUID personId) { return tenant.equals(tenantId)&&person.equals(personId); }
        @Override public boolean fileBelongsToTenant(UUID tenantId,UUID fileId) { return tenant.equals(tenantId)&&file.equals(fileId); }
    };

    @Test void acceptsRequiredTypedValuesAndTenantOwnedReferences() throws Exception {
        List<ProductApplicationValuesValidator.Field> schema=List.of(
                new ProductApplicationValuesValidator.Field("title","TEXT",true,Set.of()),
                new ProductApplicationValuesValidator.Field("count","NUMBER",true,Set.of()),
                new ProductApplicationValuesValidator.Field("date","DATE",true,Set.of()),
                new ProductApplicationValuesValidator.Field("kind","SELECT",true,Set.of("annual","sick")),
                new ProductApplicationValuesValidator.Field("approver","PERSON",true,Set.of()),
                new ProductApplicationValuesValidator.Field("evidence","ATTACHMENT",false,Set.of()));
        JsonNode values=mapper.readTree("""
                {"title":"Leave","count":2,"date":"2026-09-25","kind":"annual",
                 "approver":"%s","evidence":["%s"]}
                """.formatted(person,file));
        assertThat(ProductApplicationValuesValidator.validate(tenant,schema,values,ownership)).isEmpty();
    }

    @Test void reportsRequiredTypeAndSelectErrorsByField() throws Exception {
        List<ProductApplicationValuesValidator.Field> schema=List.of(
                new ProductApplicationValuesValidator.Field("title","TEXT",true,Set.of()),
                new ProductApplicationValuesValidator.Field("amount","MONEY",true,Set.of()),
                new ProductApplicationValuesValidator.Field("kind","SELECT",true,Set.of("annual")));
        JsonNode values=mapper.readTree("{\"title\":\" \",\"amount\":\"12\",\"kind\":\"sick\"}");
        var errors=ProductApplicationValuesValidator.validate(tenant,schema,values,ownership);
        assertThat(errors).extracting(ProductApplicationValuesValidator.FieldError::fieldKey)
                .containsExactlyInAnyOrder("title","amount","kind");
        assertThat(errors).extracting(ProductApplicationValuesValidator.FieldError::code)
                .contains("field.required","field.value.invalid");
    }

    @Test void rejectsForeignTenantPersonAndAttachmentIds() throws Exception {
        List<ProductApplicationValuesValidator.Field> schema=List.of(
                new ProductApplicationValuesValidator.Field("approver","PERSON",true,Set.of()),
                new ProductApplicationValuesValidator.Field("files","ATTACHMENT",true,Set.of()));
        UUID foreignPerson=UUID.randomUUID(), foreignFile=UUID.randomUUID(), otherTenant=UUID.randomUUID();
        JsonNode values=mapper.readTree("{\"approver\":\""+foreignPerson+"\",\"files\":\""+foreignFile+"\"}");
        ProductApplicationValuesValidator.TenantValueOwnership crossTenant=new ProductApplicationValuesValidator.TenantValueOwnership() {
            @Override public boolean personBelongsToTenant(UUID tenantId,UUID personId) { return tenantId.equals(otherTenant)&&personId.equals(foreignPerson); }
            @Override public boolean fileBelongsToTenant(UUID tenantId,UUID fileId) { return tenantId.equals(otherTenant)&&fileId.equals(foreignFile); }
        };
        var errors=ProductApplicationValuesValidator.validate(tenant,schema,values,crossTenant);
        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(ProductApplicationValuesValidator.FieldError::fieldKey)
                .containsExactlyInAnyOrder("approver","files");
    }

    @Test void rejectsValuesNotPresentInPinnedTemplate() throws Exception {
        var schema=List.of(new ProductApplicationValuesValidator.Field("title","TEXT",false,Set.of()));
        JsonNode values=mapper.readTree("{\"title\":\"x\",\"unpublishedField\":\"attack\"}");
        assertThat(ProductApplicationValuesValidator.validate(tenant,schema,values,ownership))
                .extracting(ProductApplicationValuesValidator.FieldError::code).contains("field.unknown");
    }
}
