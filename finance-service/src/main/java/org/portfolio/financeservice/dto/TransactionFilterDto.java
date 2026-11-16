package org.portfolio.financeservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.time.Instant;

@Data
public class TransactionFilterDto {

    private Long userId;

    @Positive(message = "Account ID must be a positive number")
    private Long accountId;

    @Positive(message = "Category ID must be a positive number")
    private Long categoryId;

    @PastOrPresent(message = "Start date cannot be in the future")
    private Instant from;

    @PastOrPresent(message = "End date cannot be in the future")
    private Instant to;

    @AssertTrue(message = "Start date must not be after end date")
    @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }
}
