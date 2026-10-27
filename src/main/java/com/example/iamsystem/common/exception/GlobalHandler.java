package com.example.iamsystem.common.exception;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.example.iamsystem.common.exception.ErrorMessage.ACCOUNT_DISABLED;
import static com.example.iamsystem.common.exception.ErrorMessage.PASSWORD_EXPIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.UNREADABLE_REQUEST_BODY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Turns every exception that leaves a controller (or the JWT filter) into an error answer. Our own failures are
 * {@link IamException}s and carry their status; the rest are Spring's (validation, unreadable bodies, login and
 * authorization failures). Anything else is a bug: logged, and answered 500 without its message.
 */
@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalHandler {

    @ExceptionHandler(IamException.class)
    public ResponseEntity<ExceptionResponse> handleIamException(IamException exception) {
        return answer(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleInvalidArgument(MethodArgumentNotValidException exception) {
        ExceptionResponse response = new ExceptionResponse(UNPROCESSABLE_ENTITY.value(), "Validation Fails");
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            response.addValidationError(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.unprocessableEntity().body(response);
    }

    /**
     * A missing or malformed request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return answer(BAD_REQUEST, UNREADABLE_REQUEST_BODY);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ExceptionResponse> handleAuthorizationDenied(AuthorizationDeniedException exception) {
        return answer(FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleBadCredentials(BadCredentialsException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        return answer(UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ExceptionResponse> handleLocked(LockedException exception) {
        log.warn("Account locked: {}", exception.getMessage());
        return answer(FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ExceptionResponse> handleDisabled(DisabledException exception) {
        return answer(FORBIDDEN, ACCOUNT_DISABLED);
    }

    @ExceptionHandler(AccountExpiredException.class)
    public ResponseEntity<ExceptionResponse> handleAccountExpired(AccountExpiredException exception) {
        log.warn("Password is expired: {}", exception.getMessage());
        return answer(FORBIDDEN, PASSWORD_EXPIRED);
    }

    /**
     * The authentication provider wraps some failures; the wrapped lock and bad-credentials failures answer as usual.
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ExceptionResponse> handleInternalAuthenticationService(InternalAuthenticationServiceException exception) {
        return switch (exception.getCause()) {
            case LockedException locked -> handleLocked(locked);
            case BadCredentialsException badCredentials -> handleBadCredentials(badCredentials);
            case null, default -> handleUnexpected(exception);
        };
    }

    /**
     * Spring MVC's own errors (unsupported method or media type, unknown path, ...) keep their status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status).body(new ExceptionResponse(status.value(), exception.getMessage()));
        }
        log.error("Unknown error occurred", exception);
        return answer(INTERNAL_SERVER_ERROR, "Unknown error occurred");
    }

    private ResponseEntity<ExceptionResponse> answer(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ExceptionResponse(status.value(), message));
    }
}
