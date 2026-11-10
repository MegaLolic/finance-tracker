package org.portfolio.financeservice.controller;

import jakarta.validation.Valid;
import org.portfolio.financeservice.dto.AccountDto;
import org.portfolio.financeservice.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/finance/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody AccountDto accountDto, @AuthenticationPrincipal Jwt jwt){
        Long userId=jwt.getClaim("userId");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(accountService.createAccount(userId, accountDto));
    }
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAccountsByUserId(@AuthenticationPrincipal Jwt jwt) {
        Long userId=jwt.getClaim("userId");
        return ResponseEntity.ok(accountService.getAccountsByUserId(userId));
    }
    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> getAccountById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(accountService.getAccountById(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountDto accountDto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        accountService.updateAccount(id, userId, accountDto);

        return ResponseEntity.ok("Account updated successfully");
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        accountService.deleteAccount(id, userId);

        return ResponseEntity.noContent().build();
    }
}
