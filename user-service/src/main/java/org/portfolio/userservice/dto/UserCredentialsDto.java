package org.portfolio.userservice.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class UserCredentialsDto {
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must contain at most 255 characters")
    private String email;
    @NotBlank(message = "Password must not be blank")
    @ToString.Exclude
    private String password;
}
