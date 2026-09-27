package one.nexus.p2.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class AuthLogoutApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void logoutAnswersWithAnEndSessionUrlFieldEvenWhenTheIdpHasNone() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"stub-code\",\"redirectUri\":\"http://127.0.0.1:8080/api/v1/auth/callback\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = one.nexus.p2.idp.StubIdentityProvider.field(login.getResponse().getContentAsString(), "accessToken");

        // The stub IdP has no end_session endpoint: the app session is still cleared, the URL is empty.
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_session_url").value(""));
    }

    @Test
    void postLogoutRedirectIsTheConfiguredOriginPlusLoginOnly() {
        assertEquals("https://nexus.example.test/login",
                AuthController.postLogoutRedirectUri("https://nexus.example.test/callback"));
        assertEquals("http://127.0.0.1:5173/login",
                AuthController.postLogoutRedirectUri("http://127.0.0.1:5173/callback?x=1"));
        assertEquals("https://nexus.example.test/login",
                AuthController.postLogoutRedirectUri("HTTPS://nexus.example.test/deep/path"));
        assertNull(AuthController.postLogoutRedirectUri(null));
        assertNull(AuthController.postLogoutRedirectUri(" "));
        assertNull(AuthController.postLogoutRedirectUri("javascript:alert(1)"));
        assertNull(AuthController.postLogoutRedirectUri("/callback"));
        assertNull(AuthController.postLogoutRedirectUri("http://bad host/callback"));
    }
}
