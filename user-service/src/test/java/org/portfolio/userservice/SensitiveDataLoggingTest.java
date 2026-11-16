package org.portfolio.userservice;

import org.junit.jupiter.api.Test;
import org.portfolio.userservice.dto.*;
import org.portfolio.userservice.entity.User;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataLoggingTest {
    @Test
    void serviceArgumentsAndResultsDoNotExposePasswordsOrTokens() {
        var registration = new RegisterRequestDto();
        registration.setName("Visible name");
        registration.setPassword("registration-secret");
        var credentials = new UserCredentialsDto();
        credentials.setPassword("login-secret");
        var refresh = new RefreshTokenDto();
        refresh.setRefreshToken("refresh-secret");
        var tokens = new JwtAuthenticationDto();
        tokens.setToken("access-secret");
        tokens.setRefreshToken("refresh-secret");
        var user = User.builder().id(1L).password("password-hash-secret").build();

        assertTrue(registration.toString().contains("Visible name"));
        assertFalse(registration.toString().contains("registration-secret"));
        assertFalse(credentials.toString().contains("login-secret"));
        assertFalse(refresh.toString().contains("refresh-secret"));
        assertFalse(tokens.toString().contains("access-secret"));
        assertFalse(tokens.toString().contains("refresh-secret"));
        assertFalse(user.toString().contains("password-hash-secret"));
        // Excluding values from logs must not change the data used for authentication.
        assertEquals("registration-secret", registration.getPassword());
        assertEquals("access-secret", tokens.getToken());
    }

    @Test
    void registrationAndProfileUseTheSameDatabaseLengthLimit() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var registration = new RegisterRequestDto();
            registration.setName("a".repeat(100));
            registration.setSurname("b".repeat(100));
            registration.setEmail("user@example.com");
            registration.setPassword("password123");
            var profile = UserDto.builder().name(registration.getName()).surname(registration.getSurname())
                    .email(registration.getEmail()).build();
            assertTrue(validator.validate(registration).isEmpty());
            assertTrue(validator.validate(profile).isEmpty());
            registration.setSurname("b".repeat(101));
            profile.setSurname(registration.getSurname());
            assertFalse(validator.validate(registration).isEmpty());
            assertFalse(validator.validate(profile).isEmpty());
        }
    }
}
