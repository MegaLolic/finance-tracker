package org.portfolio.financeservice.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.portfolio.financeservice.entity.CurrencyEnum;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {


    private Long id;

    @NotNull(groups = OnUpdate.class, message = "Version is required for updates")
    @PositiveOrZero(message = "Version must not be negative")
    private Long version;

    @NotBlank(message = "Name cannot be blank")
    @Size(max = 50,message = "Name must contain at most 50 characters")
    private String name;

    @NotNull(message = "Balance is required")
    @Digits(integer = 17, fraction = 2, message = "Balance must have at most 17 integer digits and 2 decimal places")
    private BigDecimal balance;
    @NotNull(message = "Currency is required")
    private CurrencyEnum currency;
}
