package org.portfolio.financeservice.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.portfolio.financeservice.dto.UserDto;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.exception.UserServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.core.codec.DecodingException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final WebClient.Builder webClientBuilder;
    private final HttpServletRequest request;

    @Value("${user.service.url}")
    private String baseUrl;

    public UserDto getUserById(Long userId) {
        String authorization = request.getHeader("Authorization");

        try {
            return webClientBuilder.clone()
                .baseUrl(baseUrl)
                .build()
                .get()
                .uri("/users/{id}", userId)
                .header("Authorization", authorization)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.releaseBody()
                        .then(Mono.error(responseError(response.statusCode().value(), userId))))
                .bodyToMono(UserDto.class)
                .switchIfEmpty(Mono.error(new UserServiceException(HttpStatus.BAD_GATEWAY,
                        "User service returned an empty response")))
                .map(user -> {
                    if (!userId.equals(user.getId())) {
                        throw new UserServiceException(HttpStatus.BAD_GATEWAY,
                                "User service returned an invalid response");
                    }
                    return user;
                })
                .block();
        } catch (WebClientResponseException failure) {
            // A response can fail while reading its body, even after HTTP 200 headers arrived.
            if (isTimeout(failure)) {
                throw new UserServiceException(HttpStatus.GATEWAY_TIMEOUT, "User service request timed out");
            }
            throw new UserServiceException(HttpStatus.BAD_GATEWAY, "User service returned an invalid response");
        } catch (WebClientRequestException failure) {
            if (isTimeout(failure)) {
                throw new UserServiceException(HttpStatus.GATEWAY_TIMEOUT, "User service request timed out");
            }
            throw new UserServiceException(HttpStatus.SERVICE_UNAVAILABLE, "User service is unavailable");
        } catch (DecodingException failure) {
            throw new UserServiceException(HttpStatus.BAD_GATEWAY, "User service returned an invalid response");
        }
    }

    private RuntimeException responseError(int status, Long userId) {
        return switch (status) {
            case 401 -> new UserServiceException(HttpStatus.UNAUTHORIZED, "User authentication is no longer valid");
            case 403 -> new UserServiceException(HttpStatus.FORBIDDEN, "User access is forbidden");
            case 404 -> new ResourceNotFoundException("User", userId);
            case 503 -> new UserServiceException(HttpStatus.SERVICE_UNAVAILABLE, "User service is unavailable");
            case 504 -> new UserServiceException(HttpStatus.GATEWAY_TIMEOUT, "User service request timed out");
            default -> new UserServiceException(HttpStatus.BAD_GATEWAY, "User service returned an invalid response");
        };
    }

    private boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException
                    || cause instanceof io.netty.channel.ConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
