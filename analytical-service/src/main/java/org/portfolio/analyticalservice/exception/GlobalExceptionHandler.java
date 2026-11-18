package org.portfolio.analyticalservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponse> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ExceptionResponse(400, exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ExceptionResponse(400,
                "Invalid request parameter", Map.of(exception.getName(), "Value has an invalid format")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exception) {
        if (exception instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(
                    new ExceptionResponse(error.getStatusCode().value(),
                            status == null ? "Request failed" : status.getReasonPhrase(), Map.of()));
        }
        return ResponseEntity.internalServerError()
                .body(new ExceptionResponse(500, "Internal server error", Map.of()));
    }
}
