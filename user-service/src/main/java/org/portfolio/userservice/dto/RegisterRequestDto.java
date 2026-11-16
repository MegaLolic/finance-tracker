package org.portfolio.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.portfolio.userservice.validation.PasswordByteLength;

@Data
public class RegisterRequestDto {
    @NotBlank(message = "Name must not be blank")
    private String name;

    @NotBlank(message = "Surname must not be blank")
    private String surname;

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must contain at least 8 characters")
    @PasswordByteLength
    private String password;
}
