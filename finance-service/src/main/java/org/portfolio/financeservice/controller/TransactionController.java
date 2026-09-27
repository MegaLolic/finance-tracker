package org.portfolio.financeservice.controller;

import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/transactions")
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
    public List<TransactionDto> getTransactions(
            @ModelAttribute TransactionFilterDto filter
    ) {
        return transactionService.getTransactions(filter);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDto> getTransactionById(@PathVariable Long id) {
        TransactionDto transactionDto = transactionService.getTransactionById(id);
        if (transactionDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(transactionDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDto> updateTransaction(@PathVariable Long id,@Valid @RequestBody TransactionDto transactionDto) {
            transactionService.updateTransaction(id, transactionDto);
            return ResponseEntity.ok(transactionDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
            transactionService.deleteTransaction(id);
            return ResponseEntity.noContent().build();
    }
    @GetMapping
    public ResponseEntity<List<TransactionDto>> getTransactionsByUserId(@AuthenticationPrincipal Jwt jwt) {
        Long userId=jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(userId));
    }
}