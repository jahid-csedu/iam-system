package com.example.iamsystem.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "iam.cors.allowed-origins=http://console.test,http://localhost:5173")
@AutoConfigureMockMvc
class CorsTest {

    private static final String CONSOLE = "http://console.test";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A preflight from an allowed origin may send the token and X-Tenant-Id to a protected endpoint")
    void preflight_fromAnAllowedOrigin_isAccepted() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header(HttpHeaders.ORIGIN, CONSOLE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, x-tenant-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONSOLE))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("x-tenant-id")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("A request from an allowed origin gets the response with the allow-origin header")
    void request_fromAnAllowedOrigin_isAnswered() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json").header(HttpHeaders.ORIGIN, CONSOLE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONSOLE));
    }

    @Test
    @DisplayName("Requests and preflights from an origin that isn't listed are refused")
    void otherOrigin_isRefused() throws Exception {
        mockMvc.perform(options("/api/auth/authenticate")
                        .header(HttpHeaders.ORIGIN, "http://evil.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(get("/.well-known/jwks.json").header(HttpHeaders.ORIGIN, "http://evil.test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A preflight asking for a header that isn't allowed is refused")
    void preflight_withAnotherHeader_isRefused() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header(HttpHeaders.ORIGIN, CONSOLE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-custom"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Requests without an Origin header, from servers or tools, are not affected")
    void requestWithoutOrigin_isNotAffected() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
