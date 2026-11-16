package org.portfolio.userservice.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.portfolio.userservice.dto.RegisterRequestDto;
import org.portfolio.userservice.dto.UserDto;
import org.portfolio.userservice.service.UserService;
import org.portfolio.userservice.deletion.UserDeletionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final UserDeletionService deletions;

    public UserController(UserService userService, UserDeletionService deletions) {
        this.userService = userService;
        this.deletions = deletions;
    }

    @PostMapping("/registration")
    @SecurityRequirements
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody RegisterRequestDto userDto) {
        UserDto created = userService.createUser(userDto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");

        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");

        if (!userId.equals(id)) {
            throw new AccessDeniedException("You don't have permission to access this user");
        }

        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateUser(@PathVariable Long id,
                                             @Valid @RequestBody UserDto userDto,
                                             @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");

        if (!userId.equals(id)) {
            throw new AccessDeniedException("You don't have permission to update this user");
        }

        userService.updateUser(id, userDto);
        return ResponseEntity.ok("User updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");

        if (!userId.equals(id)) {
            throw new AccessDeniedException("You don't have permission to delete this user");
        }

        deletions.request(id);
        return ResponseEntity.accepted().build();
    }
}
