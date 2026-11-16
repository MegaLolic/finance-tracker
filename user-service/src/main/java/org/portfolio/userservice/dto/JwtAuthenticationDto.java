package org.portfolio.userservice.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class JwtAuthenticationDto {
    @ToString.Exclude
    private String token;
    @ToString.Exclude
    private String refreshToken;
}
