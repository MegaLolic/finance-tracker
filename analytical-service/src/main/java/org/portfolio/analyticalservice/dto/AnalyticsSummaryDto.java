package org.portfolio.analyticalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDto {
    private Long accountId;
    private LocalDate from;
    private LocalDate to;
    private String timezone;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netAmount;
    private long incomeCount;
    private long expenseCount;
    private long transactionCount;
    private BigDecimal averageIncome;
    private BigDecimal averageExpense;
    private List<CategorySummaryDto> categories;
}
