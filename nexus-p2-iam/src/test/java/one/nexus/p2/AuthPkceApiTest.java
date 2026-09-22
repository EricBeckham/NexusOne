package one.nexus.p2;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AuthPkceApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void defaultAuthorizeUrlHasNoPkceAndStubCodePlusVerifierStillLogsIn() throws Exception {
        mvc.perform(get("/api/v1/auth/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(containsString("code=stub-code")))
                .andExpect(jsonPath("$.url").value(not(containsString("code_challenge"))))
                .andExpect(jsonPath("$.code_challenge").doesNotExist())
                .andExpect(jsonPath("$.code_verifier").doesNotExist());

        mvc.perform(get("/api/v1/auth/authorize-url").param("pkce", "plain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("P2.IAM.PKCE"));

        MvcResult pkce = mvc.perform(get("/api/v1/auth/authorize-url").param("pkce", "s256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(containsString("code=stub-code")))
                .andExpect(jsonPath("$.code_challenge").exists())
                .andExpect(jsonPath("$.code_verifier").exists())
                .andExpect(jsonPath("$.code_challenge_method").value("S256"))
                .andReturn();
        String verifier = one.nexus.p2.idp.StubIdentityProvider.field(
                pkce.getResponse().getContentAsString(), "code_verifier");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"codeVerifier\":\"" + verifier
                                + "\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").exists());
    }
}
