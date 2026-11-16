package org.portfolio.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.access.AccessDeniedException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ExceptionResponse(400,
                "Invalid request parameter", Map.of(exception.getName(), "Value has an invalid format")));
    }

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

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ExceptionResponse> handleAuthException(AuthenticationException exception) {

        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ExceptionResponse response = new ExceptionResponse(status.value(),
                exception.getMessage(), Map.of());

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ExceptionResponse(409, "User data conflicts with existing data", Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleInvalidBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(
                new ExceptionResponse(400, "Invalid request body", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(
                    new ExceptionResponse(error.getStatusCode().value(),
                            status == null ? "Request failed" : status.getReasonPhrase(), Map.of()));
        }
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ExceptionResponse response = new ExceptionResponse(status.value(), "Internal server error", Map.of());

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> handleAccessDeniedException(AccessDeniedException exception) {
        HttpStatus status = HttpStatus.FORBIDDEN;

        ExceptionResponse response = new ExceptionResponse(
                status.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(status).body(response);
    }
}
