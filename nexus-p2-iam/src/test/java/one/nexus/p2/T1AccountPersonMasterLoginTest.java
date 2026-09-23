package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import one.nexus.p2.account.AccountPersonMaster;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T1-L1 acceptance: a loginable person/account master exists and can be
 * associated with an auth subject (stub IdP). Org lifecycle corners and
 * personal-center scope are out of this lane.
 */
@SpringBootTest
@AutoConfigureMockMvc
class T1AccountPersonMasterLoginTest {
    private static final UUID TENANT = UUID.fromString("018f0000-0000-7000-8000-00000000b111");

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityModule module;

    @Test
    void ensureCreatesLoginablePersonBoundToAuthSubject() {
        AccountPersonMaster.Result created = module.accountPersonMaster().ensure(new AccountPersonMaster.Command(
                TENANT, "T1 Login User", "t1-l1-user@example.com", "t1-l1-user", "E-T1L1"));

        assertTrue(created.createdAccount());
        assertTrue(created.createdMembership());
        assertTrue(created.createdPerson());
        assertNotNull(created.idpSubject());

        Model.GlobalAccount account = module.repository().findAccount(created.accountId()).orElseThrow();
        assertEquals(Model.AccountStatus.ACTIVE, account.status());
        assertEquals(created.idpSubject(), account.keycloakUserId());
        assertEquals("t1-l1-user@example.com", account.email());

        Model.Person person = module.repository().findPersonByAccount(TENANT, account.id()).orElseThrow();
        assertEquals(Model.PersonStatus.ACTIVE, person.status());
        assertEquals(account.id(), person.accountId());
        assertEquals(TENANT, person.tenantId());

        Model.TenantMembership membership = module.repository().findMembership(TENANT, account.id()).orElseThrow();
        assertEquals(Model.MembershipStatus.ACTIVE, membership.status());

        // Non-admin: password-only stub token is enough (admin MFA is T1-L2/G18 concern).
        String token = StubIdentityProvider.tokenWithoutMfa(
                created.idpSubject(), account.email(), account.username(), account.displayName());
        SessionService.SessionView session = module.sessions().loginWithAccessToken(token);
        assertEquals(account.id(), session.accountId());
        assertEquals(TENANT, session.tenantId());
        assertEquals(person.id(), session.personId());
        assertNotNull(session.contextId());

        Model.IssuedContext issued = module.repository().findContext(session.contextId()).orElseThrow();
        assertEquals(created.idpSubject(), issued.idpSubject());
        assertEquals(person.id(), issued.personId());
        assertEquals(account.id(), issued.accountId());

        AccountPersonMaster.Result again = module.accountPersonMaster().ensure(new AccountPersonMaster.Command(
                TENANT, "T1 Login User", "t1-l1-user@example.com", "t1-l1-user", "E-T1L1"));
        assertFalse(again.createdAccount());
        assertFalse(again.createdMembership());
        assertFalse(again.createdPerson());
        assertEquals(created.accountId(), again.accountId());
        assertEquals(created.personId(), again.personId());
    }

    @Test
    void bootstrapApiEnsuresLoginablePerson() throws Exception {
        UUID tenant = UUID.fromString("018f0000-0000-7000-8000-00000000a1a1");

        mvc.perform(post("/api/v1/internal/tenants/" + tenant + "/loginable-persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"API User\",\"email\":\"t1-l1-api@example.com\",\"username\":\"t1-l1-api\",\"employeeNo\":\"E-API\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/internal/tenants/" + tenant + "/loginable-persons")
                        .header("X-Nexus-Bootstrap-Token", "test-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"API User\",\"email\":\"t1-l1-api@example.com\",\"username\":\"t1-l1-api\",\"employeeNo\":\"E-API\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant.toString()))
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.personId").exists())
                .andExpect(jsonPath("$.membershipId").exists())
                .andExpect(jsonPath("$.idpSubject").exists())
                .andExpect(jsonPath("$.createdAccount").value(true))
                .andExpect(jsonPath("$.createdPerson").value(true));

        var account = module.repository().findAccountByEmail("t1-l1-api@example.com").orElseThrow();
        String token = StubIdentityProvider.tokenWithoutMfa(
                account.keycloakUserId(), account.email(), account.username(), account.displayName());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant.toString()))
                .andExpect(jsonPath("$.accountId").value(account.id().toString()))
                .andExpect(jsonPath("$.personId").exists())
                .andExpect(jsonPath("$.contextId").exists());
    }
}
