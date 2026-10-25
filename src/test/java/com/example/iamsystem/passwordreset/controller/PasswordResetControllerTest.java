package com.example.iamsystem.passwordreset.controller;

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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.RESET_REQUEST_ACCEPTED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private JavaMailSender mailSender;

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
    @DisplayName("Reset by username: the OTP and then the new password are emailed, and the new password logs in")
    void resetByUsername_emailsOtpThenAWorkingPassword() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedSecret();

        postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp)).andExpect(status().isOk());
        String newPassword = lastEmailedSecret();

        postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", newPassword)).andExpect(status().isOk());
        postJson("/api/auth/authenticate", new UserLoginDto(clientId, "acme-root", "Password1!")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reset by email works the same way")
    void resetByEmail_emailsTheOtp() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "root@acme.com")).andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertArrayEquals(new String[]{"root@acme.com"}, mail.getValue().getTo());
    }

    @Test
    @DisplayName("An OTP used with another user's identifier is rejected")
    void reset_withWrongIdentifier_isRejected() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root")).andExpect(status().isOk());
        String otp = lastEmailedSecret();
        clearInvocations(mailSender);

        postJson("/api/password/reset", new PasswordResetDto(clientId, "someone-else", otp)).andExpect(status().isBadRequest());
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("A request for an unknown user gets the same answer as one for a real user, and sends nothing")
    void resetRequest_unknownUser_looksLikeAKnownOne() throws Exception {
        String known = postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        clearInvocations(mailSender);

        String unknown = postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "ghost@acme.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(known, unknown);
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("A failed email gets the same answer too")
    void resetRequest_mailFailure_looksTheSame() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root"))
                .andExpect(status().isOk())
                .andExpect(content().string(RESET_REQUEST_ACCEPTED));
    }

    @Test
    @DisplayName("A request without a username or email fails validation and sends nothing")
    void resetRequest_withoutIdentifier_failsValidation() throws Exception {
        postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, " ")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("A request without a JSON body is a bad request")
    void resetRequest_withoutBody_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/password/reset-request")).andExpect(status().isBadRequest());
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("The OTP and the new password never appear in a response")
    void responses_neverContainSecrets() throws Exception {
        String requestResponse = postJson("/api/password/reset-request", new PasswordResetRequestDto(clientId, "acme-root"))
                .andReturn().getResponse().getContentAsString();
        String otp = lastEmailedSecret();
        String resetResponse = postJson("/api/password/reset", new PasswordResetDto(clientId, "acme-root", otp))
                .andReturn().getResponse().getContentAsString();

        assertFalse(requestResponse.contains(otp));
        assertFalse(resetResponse.contains(lastEmailedSecret()));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    /**
     * The OTP or password at the end of the most recent email.
     */
    private String lastEmailedSecret() {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mail.capture());
        List<SimpleMailMessage> sent = mail.getAllValues();
        String text = sent.getLast().getText();
        return text.substring(text.lastIndexOf(' ') + 1);
    }
}
