package org.portfolio.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.portfolio.userservice.dto.JwtAuthenticationDto;
import org.portfolio.userservice.dto.RefreshTokenDto;
import org.portfolio.userservice.dto.UserCredentialsDto;
import org.portfolio.userservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.naming.AuthenticationException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/sign-in")
    public ResponseEntity<JwtAuthenticationDto> signIn(@RequestBody UserCredentialsDto userCredentialsDto){
        try {
            JwtAuthenticationDto jwtAuthenticationDto=userService.signIn(userCredentialsDto);
            return ResponseEntity.ok(jwtAuthenticationDto);
        } catch (AuthenticationException e) {
            throw new RuntimeException("Authentication failed"+ e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthenticationDto> refresh(@RequestBody RefreshTokenDto refreshTokenDto) throws Exception{
        return ResponseEntity.ok(userService.refreshToken(refreshTokenDto));
    }
}