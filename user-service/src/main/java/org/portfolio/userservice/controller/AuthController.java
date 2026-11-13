package org.portfolio.userservice.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.portfolio.userservice.dto.JwtAuthenticationDto;
import org.portfolio.userservice.dto.RefreshTokenDto;
import org.portfolio.userservice.dto.UserCredentialsDto;
import org.portfolio.userservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/sign-in")
    @SecurityRequirements
    public ResponseEntity<JwtAuthenticationDto> signIn(@Valid @RequestBody UserCredentialsDto userCredentialsDto){
        return ResponseEntity.ok(userService.signIn(userCredentialsDto));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<JwtAuthenticationDto> refresh(@Valid @RequestBody RefreshTokenDto refreshTokenDto){
        return ResponseEntity.ok(userService.refreshToken(refreshTokenDto));
    }
}
