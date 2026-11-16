package org.portfolio.userservice.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;

@Data
public class RefreshTokenDto {
    @NotBlank(message = "Refresh token must not be blank")
    @ToString.Exclude
    private String refreshToken;
}
