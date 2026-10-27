package com.example.iamsystem.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalHandlerTest {

    private final GlobalHandler handler = new GlobalHandler();

    @Test
    @DisplayName("Each of our exceptions answers with its own status and its message")
    void iamExceptions_answerWithTheirStatus() {
        Map<IamException, HttpStatus> expected = Map.of(
                new AlreadyExistsException("taken"), HttpStatus.BAD_REQUEST,
                new DataNotFoundException("missing"), HttpStatus.NOT_FOUND,
                new EmailNotSentException("mail down"), HttpStatus.SERVICE_UNAVAILABLE,
                new InvalidClientException("unknown client"), HttpStatus.UNAUTHORIZED,
                new InvalidOtpException("wrong code"), HttpStatus.BAD_REQUEST,
                new InvalidPasswordException("wrong password"), HttpStatus.BAD_REQUEST,
                new InvalidTokenException("bad token"), HttpStatus.UNAUTHORIZED,
                new NoAccessException("not yours"), HttpStatus.FORBIDDEN,
                new TooManyRequestsException("slow down"), HttpStatus.TOO_MANY_REQUESTS,
                new UserInputNotValidException("not like this"), HttpStatus.BAD_REQUEST);

        expected.forEach((exception, status) -> {
            ResponseEntity<ExceptionResponse> answer = handler.handleIamException(exception);
            assertEquals(status, answer.getStatusCode());
            assertEquals(status.value(), answer.getBody().getStatus());
            assertEquals(exception.getMessage(), answer.getBody().getMessage());
        });
    }

    @Test
    @DisplayName("An unexpected exception is a 500 that doesn't show its message")
    void unexpectedException_hidesItsMessage() {
        ResponseEntity<ExceptionResponse> answer = handler.handleUnexpected(new IllegalArgumentException("internal detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, answer.getStatusCode());
        assertEquals("Unknown error occurred", answer.getBody().getMessage());
    }

    @Test
    @DisplayName("Wrapped login failures answer like unwrapped ones; other wrapped failures are a 500")
    void wrappedAuthenticationFailures_answerAsUnwrapped() {
        assertEquals(HttpStatus.FORBIDDEN, handler.handleInternalAuthenticationService(
                new InternalAuthenticationServiceException("wrapped", new LockedException("locked"))).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, handler.handleInternalAuthenticationService(
                new InternalAuthenticationServiceException("wrapped", new BadCredentialsException("bad"))).getStatusCode());
        ResponseEntity<ExceptionResponse> other = handler.handleInternalAuthenticationService(
                new InternalAuthenticationServiceException("database down", new IllegalStateException("internal detail")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, other.getStatusCode());
        assertEquals("Unknown error occurred", other.getBody().getMessage());
    }
}
