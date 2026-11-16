package org.portfolio.financeservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.portfolio.financeservice.dto.OnUpdate;
import org.portfolio.financeservice.dto.CategoryDto;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.dto.TransactionFilterDto;
import org.portfolio.financeservice.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/finance/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionDto> createTransaction(@Valid @RequestBody TransactionDto transactionDto, @AuthenticationPrincipal Jwt jwt){
        Long userId=jwt.getClaim("userId");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(userId, transactionDto));
    }

    @GetMapping("/search")
    public ResponseEntity<List<TransactionDto>> getTransactions(
            @Valid @ModelAttribute TransactionFilterDto filter,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        filter.setUserId(userId);

        return ResponseEntity.ok(transactionService.getTransactions(filter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDto> getTransactionById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        return ResponseEntity.ok(
                transactionService.getTransactionById(id, userId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDto> updateTransaction(
            @PathVariable Long id,
            @Validated({Default.class, OnUpdate.class}) @RequestBody TransactionDto transactionDto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        return ResponseEntity.ok(transactionService.updateTransaction(id, userId, transactionDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id,
            @RequestParam Long version,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        transactionService.deleteTransaction(id, userId, version);

        return ResponseEntity.noContent().build();
    }
    @GetMapping
    public ResponseEntity<List<TransactionDto>> getTransactionsByUserId(@AuthenticationPrincipal Jwt jwt) {
        Long userId=jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(userId));
    }
}
