package org.portfolio.financeservice.dto;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "Name cannot be blank")
    @Size(max = 50,message = "Name must contain at most 50 characters")
    private String name;

    private BigDecimal balance;
    @NotNull(message = "Currency is required")
    private CurrencyEnum currency;
}
