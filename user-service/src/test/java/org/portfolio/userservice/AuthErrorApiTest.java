package org.portfolio.userservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.portfolio.userservice.controller.AuthController;
import org.portfolio.userservice.controller.UserController;
import org.portfolio.userservice.exception.GlobalExceptionHandler;
import org.portfolio.userservice.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthErrorApiTest {
    private UserService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(UserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service), new UserController(service), new DeniedController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void wrongCredentialsReturnUnauthorized() throws Exception {
        when(service.signIn(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRefreshReturnsUnauthorized() throws Exception {
        when(service.refreshToken(any())).thenThrow(new BadCredentialsException("Invalid refresh token"));
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputsDoNotReachService() throws Exception {
        mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(service.createUser(any())).thenThrow(new DataIntegrityViolationException("uk_user_email"));
        mvc.perform(post("/users/registration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"surname\":\"User\",\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void springAccessDeniedReturnsForbidden() throws Exception {
        mvc.perform(delete("/test/denied")).andExpect(status().isForbidden());
    }

    @org.springframework.web.bind.annotation.RestController
    static class DeniedController {
        @org.springframework.web.bind.annotation.DeleteMapping("/test/denied")
        void deny() {
            throw new AccessDeniedException("Forbidden");
        }
    }
}
