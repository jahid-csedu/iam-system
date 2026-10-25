package com.example.iamsystem.auth.controller;

import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class JwksControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("The JWKS is public and lists RSA signature keys")
    void jwks_isPublic() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));
    }

    @Test
    @DisplayName("An access token from login can be verified with nothing but the published JWKS")
    void accessToken_verifiesWithThePublishedKey() throws Exception {
        String accessToken = loginAsNewRoot();
        JsonNode jwk = readJson(mockMvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString())
                .get("keys").get(0);

        Claims claims = Jwts.parser().verifyWith(publicKey(jwk)).build().parseSignedClaims(accessToken).getPayload();

        assertEquals("acme-root", claims.get("username"));
    }

    private String loginAsNewRoot() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com", null));
        String consoleClientId = readJson(mockMvc.perform(post("/api/tenants/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .get("managementConsole").get("client_id").asString();

        return readJson(mockMvc.perform(post("/api/auth/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UserLoginDto(consoleClientId, "acme-root", "Password1!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("access_token").asString();
    }

    private JsonNode readJson(String json) {
        return jsonMapper.readTree(json);
    }

    private static PublicKey publicKey(JsonNode jwk) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                unsigned(jwk.get("n").asString()), unsigned(jwk.get("e").asString())));
    }

    private static BigInteger unsigned(String base64Url) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64Url));
    }
}
