package org.portfolio.financeservice.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.portfolio.financeservice.dto.UserDto;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final WebClient.Builder webClientBuilder;
    private final HttpServletRequest request;

    @Value("${user.service.url}")
    private String baseUrl;

    public UserDto getUserById(Long userId) {
        String authorization = request.getHeader("Authorization");

        return webClientBuilder
                .baseUrl(baseUrl)
                .build()
                .get()
                .uri("/users/{id}", userId)
                .header("Authorization", authorization)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> response.bodyToMono(String.class)
                                .map(body -> new ResourceNotFoundException("User", userId))
                )
                .bodyToMono(UserDto.class)
                .block();
    }
}