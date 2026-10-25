package com.example.iamsystem.client.controller;

import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.client.dto.TokenSettingsDto;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Set;

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clients of two tenants, against MySQL. Runs without a test transaction, like production requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;

    private JsonNode acmeConsole;
    private User acmeRoot;
    private User globexRoot;

    @BeforeEach
    void setUp() throws Exception {
        acmeConsole = signup("Acme", "acme-root", "root@acme.com").get("managementConsole");
        signup("Globex", "globex-root", "root@globex.com");
        acmeRoot = findByUsername(userRepository, "acme-root").orElseThrow();
        globexRoot = findByUsername(userRepository, "globex-root").orElseThrow();
    }

    @Test
    @DisplayName("A registered application gets a new 24-character client_id")
    void createClient_generatesAClientId() throws Exception {
        JsonNode client = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("APPLICATION")));

        assertEquals(24, client.get("client_id").asString().length());
        assertNotEquals(acmeConsole.get("client_id").asString(), client.get("client_id").asString());
    }

    @Test
    @DisplayName("Client names are unique per tenant")
    void createClient_withNameTakenInTheTenant_isBadRequest() throws Exception {
        send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop")).andExpect(status().isCreated());

        send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop")).andExpect(status().isBadRequest());
        send(post("/api/clients"), globexRoot, new ClientRequestDto("Shop")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Each tenant sees only its own clients, starting with its management console")
    void getClients_listsOnlyTheTenantsClients() throws Exception {
        send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/clients").with(actingAs(acmeRoot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Management Console"));
        mockMvc.perform(get("/api/clients/{id}", acmeConsole.get("id").asLong()).with(actingAs(globexRoot)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A client can be renamed; its client_id stays")
    void renameClient_keepsTheClientId() throws Exception {
        long consoleId = acmeConsole.get("id").asLong();

        send(put("/api/clients/{id}", consoleId), acmeRoot, new ClientRequestDto("Acme Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Admin"))
                .andExpect(jsonPath("$.client_id").value(acmeConsole.get("client_id").asString()));
    }

    @Test
    @DisplayName("The management console can't be deleted; application clients can")
    void deleteClient_neverTheConsole() throws Exception {
        long shopId = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("id").asLong();

        mockMvc.perform(delete("/api/clients/{id}", acmeConsole.get("id").asLong()).with(actingAs(acmeRoot)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/clients/{id}", shopId).with(actingAs(acmeRoot))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/clients/{id}", shopId).with(actingAs(acmeRoot))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A regular user logs in through an application client but not through the management console")
    void regularUser_logsInOnlyThroughApplications() throws Exception {
        String shopClientId = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("client_id").asString();
        send(post("/api/users"), acmeRoot, new UserCreationDto("carol", "Carol123!", "Carol", "carol@acme.com", UserType.REGULAR))
                .andExpect(status().isCreated());

        login(shopClientId, "carol", "Carol123!").andExpect(status().isOk());
        login(acmeConsole.get("client_id").asString(), "carol", "Carol123!").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A refresh token works only through the client it was issued for")
    void refresh_onlyThroughTheIssuingClient() throws Exception {
        String consoleClientId = acmeConsole.get("client_id").asString();
        String shopClientId = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("client_id").asString();
        String refreshToken = body(login(consoleClientId, "acme-root", "Password1!")).get("refresh_token").asString();

        refresh(shopClientId, refreshToken).andExpect(status().isUnauthorized());
        refresh(consoleClientId, refreshToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("New clients start with 5-minute access tokens, 24-hour refresh tokens and the username claim")
    void newClients_haveTheDefaultTokenSettings() throws Exception {
        send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))
                .andExpect(jsonPath("$.tokenSettings.accessTokenTtlSeconds").value(300))
                .andExpect(jsonPath("$.tokenSettings.refreshTokenEnabled").value(true))
                .andExpect(jsonPath("$.tokenSettings.refreshTokenTtlSeconds").value(86400))
                .andExpect(jsonPath("$.tokenSettings.claims[0]").value("USERNAME"));
        assertEquals(300, acmeConsole.get("tokenSettings").get("accessTokenTtlSeconds").asInt());
    }

    @Test
    @DisplayName("Tokens issued after a settings change follow the client's new lifetime and claims")
    void tokens_followTheClientsSettings() throws Exception {
        JsonNode shop = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop")));
        TokenSettingsDto settings = new TokenSettingsDto(600, true, 7200, Set.of(TokenClaim.EMAIL, TokenClaim.USER_TYPE));

        send(put("/api/clients/{id}/token-settings", shop.get("id").asLong()), acmeRoot, settings)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenSettings.accessTokenTtlSeconds").value(600));

        JsonNode tokens = body(login(shop.get("client_id").asString(), "acme-root", "Password1!").andExpect(status().isOk()));
        JsonNode access = payload(tokens.get("access_token").asString());
        assertEquals(600, access.get("exp").asLong() - access.get("iat").asLong());
        assertEquals("root@acme.com", access.get("email").asString());
        assertEquals("MANAGEMENT", access.get("user_type").asString());
        assertFalse(access.has("username"));
    }

    @Test
    @DisplayName("A client without refresh tokens gives none at login and refuses refreshes")
    void refreshTokensDisabled_noRefreshToken() throws Exception {
        String consoleClientId = acmeConsole.get("client_id").asString();
        String earlierRefreshToken = body(login(consoleClientId, "acme-root", "Password1!")).get("refresh_token").asString();

        send(put("/api/clients/{id}/token-settings", acmeConsole.get("id").asLong()), acmeRoot,
                new TokenSettingsDto(300, false, 86400, Set.of(TokenClaim.USERNAME))).andExpect(status().isOk());

        login(consoleClientId, "acme-root", "Password1!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
        refresh(consoleClientId, earlierRefreshToken).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Lifetimes outside the allowed ranges, or a refresh token not outliving the access token, fail validation")
    void tokenSettings_outOfRange_failValidation() throws Exception {
        long consoleId = acmeConsole.get("id").asLong();

        send(put("/api/clients/{id}/token-settings", consoleId), acmeRoot, new TokenSettingsDto(30, true, 86400, Set.of()))
                .andExpect(status().isUnprocessableEntity());
        send(put("/api/clients/{id}/token-settings", consoleId), acmeRoot, new TokenSettingsDto(300, true, 40 * 86400, Set.of()))
                .andExpect(status().isUnprocessableEntity());
        send(put("/api/clients/{id}/token-settings", consoleId), acmeRoot, new TokenSettingsDto(3600, true, 3600, Set.of()))
                .andExpect(status().isUnprocessableEntity());
        send(put("/api/clients/{id}/token-settings", consoleId), acmeRoot, new TokenSettingsDto(300, true, 3600, null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Another tenant's client settings can't be changed")
    void tokenSettings_ofAnotherTenant_areNotFound() throws Exception {
        send(put("/api/clients/{id}/token-settings", acmeConsole.get("id").asLong()), globexRoot,
                new TokenSettingsDto(300, true, 86400, Set.of())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A suspended client refuses logins, refreshes and its tokens; activating it brings them back")
    void suspendAndActivate_controlTheClient() throws Exception {
        JsonNode shop = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop")));
        String shopClientId = shop.get("client_id").asString();
        JsonNode tokens = body(login(shopClientId, "acme-root", "Password1!").andExpect(status().isOk()));
        String accessToken = tokens.get("access_token").asString();

        send(put("/api/clients/{id}/suspend", shop.get("id").asLong()), acmeRoot, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        login(shopClientId, "acme-root", "Password1!")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Client is suspended"));
        refresh(shopClientId, tokens.get("refresh_token").asString()).andExpect(status().isForbidden());
        getClients(accessToken).andExpect(status().isUnauthorized());
        login(acmeConsole.get("client_id").asString(), "acme-root", "Password1!").andExpect(status().isOk());

        send(put("/api/clients/{id}/activate", shop.get("id").asLong()), acmeRoot, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        getClients(accessToken).andExpect(status().isOk());
        login(shopClientId, "acme-root", "Password1!").andExpect(status().isOk());
    }

    @Test
    @DisplayName("The management console can't be suspended, and another tenant's client is not found")
    void suspend_neverTheConsoleNorAnotherTenantsClient() throws Exception {
        send(put("/api/clients/{id}/suspend", acmeConsole.get("id").asLong()), acmeRoot, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The management console client can't be suspended"));
        long shopId = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("id").asLong();
        send(put("/api/clients/{id}/suspend", shopId), globexRoot, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("New clients are active")
    void newClients_areActive() throws Exception {
        send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals("ACTIVE", acmeConsole.get("status").asString());
    }

    @Test
    @DisplayName("Managing clients needs the clients permissions")
    void clients_withoutPermission_areForbidden() throws Exception {
        send(post("/api/users"), acmeRoot, new UserCreationDto("bob", "Bob12345!", "Bob", "bob@acme.com", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        User bob = findByUsername(userRepository, "bob").orElseThrow();

        mockMvc.perform(get("/api/clients").with(actingAs(bob))).andExpect(status().isForbidden());
        send(post("/api/clients"), bob, new ClientRequestDto("Shop")).andExpect(status().isForbidden());
        send(put("/api/clients/{id}/suspend", acmeConsole.get("id").asLong()), bob, null).andExpect(status().isForbidden());
        send(put("/api/clients/{id}/activate", acmeConsole.get("id").asLong()), bob, null).andExpect(status().isForbidden());
    }

    private JsonNode signup(String tenantName, String username, String email) throws Exception {
        TenantSignupDto request = new TenantSignupDto(tenantName, new UserCreationDto(username, "Password1!", "Root", email, null));
        return body(mockMvc.perform(post("/api/tenants/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()));
    }

    private ResultActions login(String clientId, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UserLoginDto(clientId, username, password))));
    }

    private ResultActions refresh(String clientId, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"client_id\":\"" + clientId + "\",\"refresh_token\":\"" + refreshToken + "\"}"));
    }

    private ResultActions getClients(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        return mockMvc.perform(request
                .with(actingAs(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private JsonNode payload(String jwt) {
        return jsonMapper.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
