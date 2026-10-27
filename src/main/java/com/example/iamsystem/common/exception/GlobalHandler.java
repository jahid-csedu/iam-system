package com.example.iamsystem.common.exception;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.ACCOUNT_DISABLED;
import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_PARAMETERS;
import static com.example.iamsystem.common.exception.ErrorMessage.PASSWORD_EXPIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.UNKNOWN_ERROR;
import static com.example.iamsystem.common.exception.ErrorMessage.UNREADABLE_REQUEST_BODY;
import static com.example.iamsystem.common.exception.ErrorMessage.VALIDATION_FAILED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

/**
 * Turns every exception that leaves a controller (or a security filter, through the {@code HandlerExceptionResolver})
 * into an RFC 9457 problem ({@code application/problem+json}: {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance}). Our own failures are {@link IamException}s and carry their status; Spring MVC's own
 * (unknown path, unsupported method or media type, ...) come from {@link ResponseEntityExceptionHandler}, and invalid
 * input adds {@value #ERRORS}, one {@link FieldProblem} per field or parameter. Anything else is a bug: logged, and
 * answered 500 without its message.
 */
@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalHandler extends ResponseEntityExceptionHandler {

    /**
     * The problem property listing the invalid fields or parameters.
     */
    public static final String ERRORS = "errors";

    /**
     * One invalid request field (a body property such as {@code rootUser.email}) or parameter (such as {@code page}).
     */
    public record FieldProblem(String field, String message) {
    }

    @ExceptionHandler(IamException.class)
    public ResponseEntity<ProblemDetail> handleIamException(IamException exception) {
        return answer(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(AuthorizationDeniedException exception) {
        return answer(FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        return answer(UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ProblemDetail> handleLocked(LockedException exception) {
        log.warn("Account locked: {}", exception.getMessage());
        return answer(FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ProblemDetail> handleDisabled(DisabledException exception) {
        return answer(FORBIDDEN, ACCOUNT_DISABLED);
    }

    @ExceptionHandler(AccountExpiredException.class)
    public ResponseEntity<ProblemDetail> handleAccountExpired(AccountExpiredException exception) {
        log.warn("Password is expired: {}", exception.getMessage());
        return answer(FORBIDDEN, PASSWORD_EXPIRED);
    }

    /**
     * The authentication provider wraps some failures; the wrapped lock and bad-credentials failures answer as usual.
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ProblemDetail> handleInternalAuthenticationService(InternalAuthenticationServiceException exception) {
        return switch (exception.getCause()) {
            case LockedException locked -> handleLocked(locked);
            case BadCredentialsException badCredentials -> handleBadCredentials(badCredentials);
            case null, default -> handleUnexpected(exception);
        };
    }

    /**
     * Spring's own errors that {@link ResponseEntityExceptionHandler} doesn't list keep their status and problem.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode()).body(errorResponse.getBody());
        }
        log.error("Unknown error occurred", exception);
        return answer(INTERNAL_SERVER_ERROR, UNKNOWN_ERROR);
    }

    /**
     * An invalid request body: 422 with every invalid field.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldProblem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage()))
                .toList();
        return withErrors(UNPROCESSABLE_CONTENT, VALIDATION_FAILED, errors);
    }

    /**
     * Invalid request parameters (such as a negative {@code page}): 400 with every invalid parameter.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldProblem> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldProblem(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return withErrors(BAD_REQUEST, INVALID_PARAMETERS, errors);
    }

    /**
     * A missing or malformed request body; the parser's own message would show internals.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.status(BAD_REQUEST).body(ProblemDetail.forStatusAndDetail(BAD_REQUEST, UNREADABLE_REQUEST_BODY));
    }

    private static ResponseEntity<Object> withErrors(HttpStatus status, String detail, List<FieldProblem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(ERRORS, errors);
        return ResponseEntity.status(status).body(problem);
    }

    private static ResponseEntity<ProblemDetail> answer(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
