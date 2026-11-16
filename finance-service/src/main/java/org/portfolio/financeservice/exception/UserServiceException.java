package org.portfolio.financeservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Public error message and status for a failed user-service call. */
@Getter
public class UserServiceException extends RuntimeException {
    private final HttpStatus status;

    public UserServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
