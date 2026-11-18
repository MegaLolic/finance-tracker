package org.portfolio.analyticalservice.service;

import org.portfolio.analyticalservice.document.RawTransaction;
import org.portfolio.analyticalservice.dto.AnalyticsPeriod;
import org.portfolio.analyticalservice.dto.AnalyticsSummaryDto;
import org.portfolio.analyticalservice.dto.CategorySummaryDto;
import org.portfolio.analyticalservice.repository.DeletedUserRepository;
import org.portfolio.analyticalservice.repository.RawTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final RawTransactionRepository transactionRepository;
    private final DeletedUserRepository deletedUserRepository;

    public AnalyticsService(RawTransactionRepository transactionRepository, DeletedUserRepository deletedUserRepository) {
        this.transactionRepository = transactionRepository;
        this.deletedUserRepository = deletedUserRepository;
    }

    public AnalyticsSummaryDto getSummary(Long userId, Long accountId, AnalyticsPeriod period,
                                          LocalDate date, String timezone) {
        if (period == null || date == null) {
            throw new IllegalArgumentException("Period and date are required");
        }
        try {
            LocalDate from = switch (period) {
                case DAY -> date;
                case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> date.withDayOfMonth(1);
                case YEAR -> date.withDayOfYear(1);
            };
            LocalDate to = switch (period) {
                case DAY -> from;
                case WEEK -> from.plusDays(6);
                case MONTH -> from.with(TemporalAdjusters.lastDayOfMonth());
                case YEAR -> from.with(TemporalAdjusters.lastDayOfYear());
            };
            return getSummary(userId, accountId, from, to, timezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Date is outside the supported range");
        }
    }

    public AnalyticsSummaryDto getSummary(Long userId, Long accountId, LocalDate from,
                                          LocalDate to, String timezone) {
        if (userId == null || userId <= 0 || accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Positive user and account IDs are required");
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("From and to are required; from must not be after to");
        }
        ZoneId zone;
        Instant start;
        Instant end;
        try {
            if (timezone == null) {
                throw new IllegalArgumentException("Timezone is required");
            }
            zone = ZoneId.of(timezone);
            start = from.atStartOfDay(zone).toInstant();
            end = to.plusDays(1).atStartOfDay(zone).toInstant();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid timezone or date range");
        }

        AnalyticsSummaryDto summary = AnalyticsSummaryDto.builder()
                .accountId(accountId).from(from).to(to).timezone(zone.getId())
                .totalIncome(new BigDecimal("0.00")).totalExpense(new BigDecimal("0.00"))
                .build();
        Map<Long, CategorySummaryDto> categories = new HashMap<>();

        if (!deletedUserRepository.existsById(userId)) {
            List<RawTransaction> transactions = transactionRepository
                    .findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            userId, accountId, start, end);
            for (RawTransaction transaction : transactions) {
                BigDecimal amount = transaction.getAmount();
                switch (transaction.getCategoryType()) {
                    case "INCOME" -> {
                        summary.setTotalIncome(summary.getTotalIncome().add(amount));
                        summary.setIncomeCount(summary.getIncomeCount() + 1);
                    }
                    case "EXPENSE" -> {
                        summary.setTotalExpense(summary.getTotalExpense().add(amount));
                        summary.setExpenseCount(summary.getExpenseCount() + 1);
                    }
                    default -> throw new IllegalStateException("Unknown transaction category type");
                }
                CategorySummaryDto category = categories.computeIfAbsent(transaction.getCategoryId(), id ->
                        CategorySummaryDto.builder().categoryId(id).categoryType(transaction.getCategoryType())
                                .totalAmount(new BigDecimal("0.00")).build());
                category.setTotalAmount(category.getTotalAmount().add(amount));
                category.setTransactionCount(category.getTransactionCount() + 1);
            }
            // A deletion marker may appear while the projection is being read.
            if (deletedUserRepository.existsById(userId)) {
                summary.setTotalIncome(new BigDecimal("0.00"));
                summary.setTotalExpense(new BigDecimal("0.00"));
                summary.setIncomeCount(0);
                summary.setExpenseCount(0);
                categories.clear();
            }
        }

        summary.setNetAmount(summary.getTotalIncome().subtract(summary.getTotalExpense()));
        summary.setTransactionCount(summary.getIncomeCount() + summary.getExpenseCount());
        summary.setAverageIncome(average(summary.getTotalIncome(), summary.getIncomeCount()));
        summary.setAverageExpense(average(summary.getTotalExpense(), summary.getExpenseCount()));
        summary.setCategories(categories.values().stream()
                .sorted(Comparator.comparing(CategorySummaryDto::getCategoryId)).toList());
        return summary;
    }

    private BigDecimal average(BigDecimal total, long count) {
        return count == 0 ? new BigDecimal("0.00")
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
