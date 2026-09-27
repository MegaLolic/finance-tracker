package org.portfolio.financeservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.time.Instant;

@Data
public class TransactionFilterDto {

    @NotNull(message = "User ID is required")
    private Long userId;

    @Positive(message = "Account ID must be a positive number")
    private Long accountId;

    @Positive(message = "Category ID must be a positive number")
    private Long categoryId;

    @PastOrPresent(message = "Start date cannot be in the future")
    private Instant from;

    @PastOrPresent(message = "End date cannot be in the future")
    private Instant to;
}