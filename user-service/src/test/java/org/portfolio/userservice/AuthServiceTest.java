package org.portfolio.userservice;

import org.junit.jupiter.api.Test;
import org.portfolio.userservice.dto.RefreshTokenDto;
import org.portfolio.userservice.dto.UserCredentialsDto;
import org.portfolio.userservice.entity.User;
import org.portfolio.userservice.repository.UserRepository;
import org.portfolio.userservice.security.jwt.JwtService;
import org.portfolio.userservice.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwt = new JwtService("a".repeat(64));
    private final UserService service = new UserService(users, mock(PasswordEncoder.class), jwt);

    @Test
    void unknownUserThrowsSpringAuthenticationException() {
        UserCredentialsDto credentials = new UserCredentialsDto();
        credentials.setEmail("missing@example.com");
        credentials.setPassword("wrong");
        assertThrows(BadCredentialsException.class, () -> service.signIn(credentials));
    }

    @Test
    void malformedAndAccessTokensCannotBeRefreshed() {
        RefreshTokenDto request = new RefreshTokenDto();
        request.setRefreshToken("invalid");
        assertThrows(BadCredentialsException.class, () -> service.refreshToken(request));
        request.setRefreshToken(jwt.generateAuthToken(1L, "user@example.com").getToken());
        assertThrows(BadCredentialsException.class, () -> service.refreshToken(request));
        verifyNoInteractions(users);
    }

    @Test
    void deletedUserCannotRefreshToken() {
        RefreshTokenDto request = new RefreshTokenDto();
        request.setRefreshToken(jwt.generateAuthToken(1L, "missing@example.com").getRefreshToken());
        assertThrows(BadCredentialsException.class, () -> service.refreshToken(request));
        verify(users).findById(1L);
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void refreshAfterEmailChangeUsesCurrentEmailAndPreservesRefreshToken() {
        RefreshTokenDto request = new RefreshTokenDto();
        request.setRefreshToken(jwt.generateAuthToken(1L, "old@example.com").getRefreshToken());
        when(users.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).email("new@example.com").build()));

        var result = service.refreshToken(request);

        assertTrue(jwt.validateAccessToken(result.getToken()));
        assertEquals(1L, jwt.getUserIdFromToken(result.getToken()));
        assertEquals("new@example.com", jwt.getEmailFromToken(result.getToken()));
        assertEquals(request.getRefreshToken(), result.getRefreshToken());
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void reusingDeletedUsersEmailDoesNotMakeOldRefreshTokenValid() {
        RefreshTokenDto request = new RefreshTokenDto();
        request.setRefreshToken(jwt.generateAuthToken(1L, "reused@example.com").getRefreshToken());
        when(users.findById(1L)).thenReturn(Optional.empty());
        when(users.findByEmail("reused@example.com")).thenReturn(Optional.of(
                User.builder().id(2L).email("reused@example.com").build()));

        assertThrows(BadCredentialsException.class, () -> service.refreshToken(request));

        verify(users).findById(1L);
        verify(users, never()).findByEmail(anyString());
    }
}
