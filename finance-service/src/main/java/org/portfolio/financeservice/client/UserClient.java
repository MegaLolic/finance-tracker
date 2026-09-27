package org.portfolio.financeservice.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.portfolio.financeservice.dto.UserDto;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final RestClient.Builder restClientBuilder;
    private final HttpServletRequest request;

    @Value("${user.service.url}")
    private String baseUrl;

    public UserDto getUserById(Long userId) {

        String authorization = request.getHeader("Authorization");

        RestClient restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        return restClient.get()
                .uri("/users/{id}", userId)
                .header("Authorization", authorization)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        (req, response) -> {
                            throw new ResourceNotFoundException(
                                    "User",
                                    userId
                            );
                        }
                )
                .body(UserDto.class);
    }
}