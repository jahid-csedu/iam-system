package com.example.iamsystem.passwordreset.controller;

import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.passwordreset.dto.PasswordResetDto;
import com.example.iamsystem.passwordreset.dto.PasswordResetRequestDto;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.RESET_REQUEST_ACCEPTED;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole self-service reset against MySQL: OTP by email, then a new password by email that works for login.
 * Runs without a test transaction, like production requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PasswordResetControllerTest {

    private static final String NEW_PASSWORD = "Chosen-Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private EmailService emailService;

    private String clientId;

    @BeforeEach
    void setUp() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com", null));
        String response = postJson("/api/tenants/signup", signup)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        clientId = jsonMapper.readTree(response).get("managementConsole").get("client_id").asString();
    }

    @Test
    @DisplayName("Reset by username: the OTP is emailed, the user sets a new password with it, and only that password logs in")
    void resetByUsername_setsTheChosenPassword() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedOtp();
        clearInvocations(emailService);

        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp, NEW_PASSWORD)).andExpect(status().isOk());

        verifyNoInteractions(emailService);
        postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", NEW_PASSWORD)).andExpect(status().isOk());
        postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", "Password1!")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A new password violating the policy is rejected and the OTP stays usable")
    void reset_withWeakPassword_keepsTheOtp() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedOtp();

        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp, "weak")).andExpect(status().isBadRequest());
        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp, NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A reset ends a login lockout")
    void reset_endsALoginLockout() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", "Wrong-password1!"));
        }
        postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", "Password1!"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("locked")));
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());

        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", lastEmailedOtp(), NEW_PASSWORD))
                .andExpect(status().isOk());

        postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reset by email works the same way")
    void resetByEmail_emailsTheOtp() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "root@acme.com")).andExpect(status().isOk());

        ArgumentCaptor<OutgoingEmail> mail = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailService).sendAfterCommit(mail.capture());
        assertEquals("root@acme.com", mail.getValue().to());
    }

    @Test
    @DisplayName("An OTP used with another user's identifier is rejected")
    void reset_withWrongIdentifier_isRejected() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedOtp();
        clearInvocations(emailService);

        postJson("/api/password/reset", new PasswordResetDto(clientId, "someone-else", otp, NEW_PASSWORD)).andExpect(status().isBadRequest());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("A request for an unknown user gets the same answer as one for a real user, and sends nothing")
    void resetRequest_unknownUser_looksLikeAKnownOne() throws Exception {
        String known = postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        clearInvocations(emailService);

        String unknown = postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "ghost@acme.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(known, unknown);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("The request only queues the email: it never waits for the mail server, whose failures it can't see")
    void resetRequest_onlyQueuesTheEmail() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root"))
                .andExpect(status().isOk())
                .andExpect(content().string(RESET_REQUEST_ACCEPTED));

        verify(emailService).sendAfterCommit(any(OutgoingEmail.class));
        verify(emailService, never()).send(any(OutgoingEmail.class));
    }

    @Test
    @DisplayName("After five wrong guesses the OTP is gone: even the right one is refused, with the same error")
    void reset_afterTooManyWrongGuesses_refusesTheRightOtp() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedOtp();
        String wrongOtp = otp.equals("000000") ? "111111" : "000000";
        clearInvocations(emailService);

        for (int attempt = 0; attempt < 5; attempt++) {
            postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", wrongOtp, NEW_PASSWORD))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
        }
        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Wrong guesses below the limit leave the OTP usable")
    void reset_afterAFewWrongGuesses_stillAcceptsTheRightOtp() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedOtp();
        String wrongOtp = otp.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < 4; attempt++) {
            postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", wrongOtp, NEW_PASSWORD)).andExpect(status().isBadRequest());
        }
        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp, NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A user gets at most three OTPs per hour; a fourth request gets the same answer and sends nothing")
    void resetRequest_overTheHourlyLimit_sendsNothing() throws Exception {
        for (int request = 0; request < 3; request++) {
            postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        }
        verify(emailService, times(3)).sendAfterCommit(any(OutgoingEmail.class));
        clearInvocations(emailService);

        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "root@acme.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(RESET_REQUEST_ACCEPTED));
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("A request without a username or email fails validation and sends nothing")
    void resetRequest_withoutIdentifier_failsValidation() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, " ")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("A request without a JSON body is a bad request")
    void resetRequest_withoutBody_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/password/reset-request")).andExpect(status().isBadRequest());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("The OTP and the new password never appear in a response")
    void responses_neverContainSecrets() throws Exception {
        String requestResponse = postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root"))
                .andReturn().getResponse().getContentAsString();
        String otp = lastEmailedOtp();
        String resetResponse = postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp, NEW_PASSWORD))
                .andReturn().getResponse().getContentAsString();

        assertFalse(requestResponse.contains(otp));
        assertFalse(resetResponse.contains(otp));
        assertFalse(resetResponse.contains(NEW_PASSWORD));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    /**
     * The OTP of the most recent email.
     */
    private String lastEmailedOtp() {
        ArgumentCaptor<OutgoingEmail> mail = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailService, atLeastOnce()).sendAfterCommit(mail.capture());
        List<OutgoingEmail> sent = mail.getAllValues();
        return (String) sent.getLast().values().get("otp");
    }
}
