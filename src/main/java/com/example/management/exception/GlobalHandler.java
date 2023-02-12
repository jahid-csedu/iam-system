package com.example.management.exception;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalHandler {
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ExceptionResponse> userAlreadyExistsExceptionHandler(UserAlreadyExistsException exception) {
        ExceptionResponse response = new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
        return buildExceptionResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ExceptionResponse> validationExceptionHandler(MethodArgumentNotValidException exception) {
        ExceptionResponse response = new ExceptionResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), "Validation Fails");
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            response.addValidationError(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.unprocessableEntity().body(response);
    }

    @ExceptionHandler(UserInputNotValidException.class)
    public ResponseEntity<ExceptionResponse> invalidUserInputHandler(UserInputNotValidException exception) {
        ExceptionResponse response = new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
        return buildExceptionResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> exceptionHandler(Exception exception) {
        log.error("Unkonwn error occured", exception);
        ExceptionResponse response = new ExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getMessage());
        return buildExceptionResponse(exception, "Unknown error occured", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ExceptionResponse> buildExceptionResponse(Exception e, HttpStatus httpStatus) {
        return buildExceptionResponse(e, e.getMessage(), httpStatus);
    }

    private ResponseEntity<ExceptionResponse> buildExceptionResponse(Exception e, String message, HttpStatus httpStatus) {
        ExceptionResponse response = new ExceptionResponse(httpStatus.value(), e.getMessage());
        return ResponseEntity.status(httpStatus).body(response);
    }
}
