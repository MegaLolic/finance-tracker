package org.portfolio.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleUserNotFound(UserNotFoundException exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        ExceptionResponse response = new ExceptionResponse(status.value(), exception.getMessage(), Map.of());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        HttpStatus status = HttpStatus.BAD_REQUEST;

        ExceptionResponse response = new ExceptionResponse(status.value(), "Validation failed", errors);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exception) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ExceptionResponse response = new ExceptionResponse(status.value(), "Internal server error", Map.of());

        return ResponseEntity.status(status).body(response);
    }
}
