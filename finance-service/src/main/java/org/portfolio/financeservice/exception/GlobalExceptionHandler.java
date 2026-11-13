package org.portfolio.financeservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ExceptionResponse(409,
                        "Operation conflicts with existing or related data", Map.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ExceptionResponse> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ExceptionResponse(409,
                        "Data has changed. Reload it and retry the operation.", Map.of()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleUserNotFound(ResourceNotFoundException exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        ExceptionResponse response = new ExceptionResponse(status.value(), exception.getMessage(), Map.of());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ExceptionResponse> handleBusinessException(BusinessException exception) {
        HttpStatus status = HttpStatus.CONFLICT;

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
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleMessageNotReadable(HttpMessageNotReadableException exception) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        ExceptionResponse response = new ExceptionResponse(status.value(),"Invalid request body",Map.of());

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ExceptionResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(new ExceptionResponse(400,
                "Missing required parameter: " + exception.getParameterName(), Map.of()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exception) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ExceptionResponse response = new ExceptionResponse(status.value(), "Internal server error", Map.of());

        return ResponseEntity.status(status).body(response);
    }
}
