package com.example.iamsystem.client.controller;

import com.example.iamsystem.client.dto.ClientRequestDto;
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

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        refresh(shopClientId, refreshToken).andExpect(status().isPreconditionFailed());
        refresh(consoleClientId, refreshToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Managing clients needs the clients permissions")
    void clients_withoutPermission_areForbidden() throws Exception {
        send(post("/api/users"), acmeRoot, new UserCreationDto("bob", "Bob12345!", "Bob", "bob@acme.com", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        User bob = findByUsername(userRepository, "bob").orElseThrow();

        mockMvc.perform(get("/api/clients").with(actingAs(bob))).andExpect(status().isForbidden());
        send(post("/api/clients"), bob, new ClientRequestDto("Shop")).andExpect(status().isForbidden());
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

    private ResultActions send(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        return mockMvc.perform(request
                .with(actingAs(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
