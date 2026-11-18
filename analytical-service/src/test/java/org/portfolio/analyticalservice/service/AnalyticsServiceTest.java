package org.portfolio.analyticalservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.portfolio.analyticalservice.document.RawTransaction;
import org.portfolio.analyticalservice.dto.AnalyticsPeriod;
import org.portfolio.analyticalservice.repository.DeletedUserRepository;
import org.portfolio.analyticalservice.repository.RawTransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {
    private RawTransactionRepository transactions;
    private DeletedUserRepository deletedUsers;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        transactions = mock(RawTransactionRepository.class);
        deletedUsers = mock(DeletedUserRepository.class);
        service = new AnalyticsService(transactions, deletedUsers);
    }

    @Test
    void calculatesExactTotalsCountsAveragesAndCategories() {
        when(transactions.findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(7L), eq(3L), any(), any())).thenReturn(List.of(
                transaction(1L, "INCOME", "100.10"), transaction(1L, "INCOME", "50.00"),
                transaction(2L, "EXPENSE", "20.01"), transaction(2L, "EXPENSE", "10.00"),
                transaction(3L, "EXPENSE", "80.00")));

        var result = service.getSummary(7L, 3L, AnalyticsPeriod.DAY, LocalDate.of(2026, 11, 17), "UTC");

        assertEquals(new BigDecimal("150.10"), result.getTotalIncome());
        assertEquals(new BigDecimal("110.01"), result.getTotalExpense());
        assertEquals(new BigDecimal("40.09"), result.getNetAmount());
        assertEquals(2, result.getIncomeCount());
        assertEquals(3, result.getExpenseCount());
        assertEquals(5, result.getTransactionCount());
        assertEquals(new BigDecimal("75.05"), result.getAverageIncome());
        assertEquals(new BigDecimal("36.67"), result.getAverageExpense());
        assertEquals(List.of(1L, 2L, 3L), result.getCategories().stream().map(c -> c.getCategoryId()).toList());
        assertEquals(new BigDecimal("30.01"), result.getCategories().get(1).getTotalAmount());
        assertEquals(2, result.getCategories().get(1).getTransactionCount());
    }

    @ParameterizedTest
    @CsvSource({
            "DAY,2024-02-29,2024-02-29,2024-02-29",
            "WEEK,2021-01-01,2020-12-28,2021-01-03",
            "WEEK,2026-11-22,2026-11-16,2026-11-22",
            "MONTH,2024-02-15,2024-02-01,2024-02-29",
            "YEAR,2024-06-15,2024-01-01,2024-12-31"
    })
    void usesCalendarBoundaries(AnalyticsPeriod period, LocalDate date, LocalDate from, LocalDate to) {
        var result = service.getSummary(7L, 3L, period, date, "UTC");
        assertEquals(from, result.getFrom());
        assertEquals(to, result.getTo());
        verify(transactions).findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                7L, 3L, from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-03-29,2026-03-28T23:00:00Z,2026-03-29T22:00:00Z",
            "2026-10-25,2026-10-24T22:00:00Z,2026-10-25T23:00:00Z"
    })
    void respectsShortAndLongDays(LocalDate date, Instant from, Instant to) {
        service.getSummary(7L, 3L, AnalyticsPeriod.DAY, date, "Europe/Berlin");
        verify(transactions).findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                7L, 3L, from, to);
    }

    @Test
    void emptyPeriodReturnsZerosAndInclusiveDateRange() {
        var result = service.getSummary(7L, 3L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 17), "Europe/Minsk");
        assertEquals(new BigDecimal("0.00"), result.getNetAmount());
        assertEquals(new BigDecimal("0.00"), result.getAverageIncome());
        assertEquals(new BigDecimal("0.00"), result.getAverageExpense());
        assertEquals(0, result.getTransactionCount());
        assertTrue(result.getCategories().isEmpty());
        verify(transactions).findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                7L, 3L, Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-11-17T21:00:00Z"));
    }

    @Test
    void expensesCanExceedIncome() {
        when(transactions.findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any())).thenReturn(List.of(transaction(2L, "EXPENSE", "10.00")));
        var result = service.getSummary(7L, 3L, AnalyticsPeriod.DAY, LocalDate.of(2026, 11, 17), "UTC");
        assertEquals(new BigDecimal("-10.00"), result.getNetAmount());
        assertEquals(new BigDecimal("0.00"), result.getAverageIncome());
    }

    @Test
    void deletedUserNeverReadsTransactions() {
        when(deletedUsers.existsById(7L)).thenReturn(true);
        var result = service.getSummary(7L, 3L, AnalyticsPeriod.DAY, LocalDate.of(2026, 11, 17), "UTC");
        assertEquals(0, result.getTransactionCount());
        verifyNoInteractions(transactions);
    }

    @Test
    void deletionDuringReadDiscardsCalculatedData() {
        when(deletedUsers.existsById(7L)).thenReturn(false, true);
        when(transactions.findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any())).thenReturn(List.of(transaction(1L, "INCOME", "100.00")));
        var result = service.getSummary(7L, 3L, AnalyticsPeriod.DAY, LocalDate.of(2026, 11, 17), "UTC");
        assertEquals(new BigDecimal("0.00"), result.getTotalIncome());
        assertEquals(0, result.getTransactionCount());
        assertTrue(result.getCategories().isEmpty());
    }

    @Test
    void invalidInputIsRejectedBeforeQuerying() {
        LocalDate date = LocalDate.of(2026, 11, 17);
        assertThrows(IllegalArgumentException.class, () -> service.getSummary(7L, 3L, date, date.minusDays(1), "UTC"));
        assertThrows(IllegalArgumentException.class, () -> service.getSummary(7L, 3L, date, date, "Invalid/Zone"));
        assertThrows(IllegalArgumentException.class, () -> service.getSummary(7L, 0L, date, date, "UTC"));
        assertThrows(IllegalArgumentException.class, () -> service.getSummary(null, 3L, date, date, "UTC"));
        assertThrows(IllegalArgumentException.class, () -> service.getSummary(7L, 3L, date, LocalDate.MAX, "UTC"));
        verifyNoInteractions(transactions, deletedUsers);
    }

    private RawTransaction transaction(Long categoryId, String type, String amount) {
        return RawTransaction.builder().categoryId(categoryId).categoryType(type).amount(new BigDecimal(amount)).build();
    }
}
