package org.portfolio.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 50,message = "Name must contain at most 50 characters")
    private String name;
    @NotBlank(message = "Surname cannot be blank")
    @Size(max = 50,message = "Surname must contain at most 50 characters")
    private String surname;
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email must be valid")
    private String email;


}
