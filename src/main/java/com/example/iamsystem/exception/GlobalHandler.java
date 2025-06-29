package com.example.iamsystem.exception;

import com.example.iamsystem.constant.ErrorMessage;
import io.jsonwebtoken.security.SignatureException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalHandler {
    @ExceptionHandler({UserAlreadyExistsException.class, PermissionAlreadyExistsException.class})
    @ResponseStatus(BAD_REQUEST)
    public ResponseEntity<ExceptionResponse> handleUserAndPermissionAlreadyExistsException(RuntimeException exception) {
        return buildExceptionResponse(exception, BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(UNPROCESSABLE_ENTITY)
    public ResponseEntity<ExceptionResponse> validationExceptionHandler(MethodArgumentNotValidException exception) {
        ExceptionResponse response = new ExceptionResponse(UNPROCESSABLE_ENTITY.value(), "Validation Fails");
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            response.addValidationError(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.unprocessableEntity().body(response);
    }

    @ExceptionHandler(UserInputNotValidException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseEntity<ExceptionResponse> invalidUserInputHandler(UserInputNotValidException exception) {
        return buildExceptionResponse(exception, BAD_REQUEST);
    }

    @ExceptionHandler(DataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ExceptionResponse> dataNotFoundExceptionHandler(DataNotFoundException exception) {
        return buildExceptionResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoAccessException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ExceptionResponse> noAccessExceptionHandler(NoAccessException exception) {
        return buildExceptionResponse(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseEntity<ExceptionResponse> illegalArgumentExceptionHandler(IllegalArgumentException exception) {
        return buildExceptionResponse(exception, BAD_REQUEST);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ExceptionResponse> authorizationDeniedExceptionHandler(AuthorizationDeniedException exception) {
        return buildExceptionResponse(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(UNAUTHORIZED)
    public ResponseEntity<ExceptionResponse> handleBadCredentialsException(BadCredentialsException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        return buildExceptionResponse(exception, UNAUTHORIZED);
    }

    @ExceptionHandler(LockedException.class)
    @ResponseStatus(FORBIDDEN)
    public ResponseEntity<ExceptionResponse> handleLockedException(LockedException exception) {
        log.warn("Account locked: {}", exception.getMessage());
        return buildExceptionResponse(exception, FORBIDDEN);
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    public ResponseEntity<ExceptionResponse> handleInternalAuthenticationServiceException(InternalAuthenticationServiceException exception) {
        Throwable cause = exception.getCause();
        switch (cause) {
            case LockedException lockedException -> {
                log.warn("Account locked (wrapped): {}", cause.getMessage());
                return buildExceptionResponse(lockedException, FORBIDDEN);
            }
            case BadCredentialsException badCredentialsException -> {
                log.warn("Authentication failed (wrapped): {}", cause.getMessage());
                return buildExceptionResponse(badCredentialsException, UNAUTHORIZED);
            }
            default -> {
                log.error("Internal authentication service error occurred", exception);
                return buildExceptionResponse(exception, INTERNAL_SERVER_ERROR);
            }
        }
    }

    @ExceptionHandler({SignatureException.class, JwtException.class})
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public ResponseEntity<ExceptionResponse> handleJwtAndSignatureExceptions(RuntimeException exception) {
        ExceptionResponse response = new ExceptionResponse(PRECONDITION_FAILED.value(), ErrorMessage.INVALID_TOKEN);
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(response);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    public ResponseEntity<ExceptionResponse> exceptionHandler(Exception exception) {
        log.error("Unknown error occurred", exception);
        ExceptionResponse response = new ExceptionResponse(INTERNAL_SERVER_ERROR.value(), "Unknown error occurred");
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(response);
    }

    private ResponseEntity<ExceptionResponse> buildExceptionResponse(Exception e, HttpStatus httpStatus) {
        ExceptionResponse response = new ExceptionResponse(httpStatus.value(), e.getMessage());
        return ResponseEntity.status(httpStatus).body(response);
    }
}
