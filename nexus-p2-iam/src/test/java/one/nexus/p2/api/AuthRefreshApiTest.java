package one.nexus.p2.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void refreshIsReachableWithAnExpiredTokenButNeedsAServerHeldSession() throws Exception {
        // No bearer at all: the endpoint itself refuses (the identity filter lets it through as public).
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("P2.IAM.REFRESH_UNAVAILABLE"));

        // A token the server never saw at login (e.g. expired, garbage) is not validated here, just unknown.
        mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer not-a-known-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("P2.IAM.REFRESH_UNAVAILABLE"));
    }

    @Test
    void anIdpWithoutRefreshSupportAnswersSignInAgainWithoutLeakingTokens() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = one.nexus.p2.idp.StubIdentityProvider.field(login.getResponse().getContentAsString(), "accessToken");

        mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("P2.IAM.REFRESH_FAILED"))
                .andExpect(content().string(not(containsString("stub-refresh"))))
                .andExpect(content().string(not(containsString(token))));
    }
}
