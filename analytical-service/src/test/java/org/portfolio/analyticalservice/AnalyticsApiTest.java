package org.portfolio.analyticalservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.portfolio.analyticalservice.controller.AnalyticsController;
import org.portfolio.analyticalservice.exception.GlobalExceptionHandler;
import org.portfolio.analyticalservice.repository.DeletedUserRepository;
import org.portfolio.analyticalservice.repository.RawTransactionRepository;
import org.portfolio.analyticalservice.service.AnalyticsService;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AnalyticsApiTest {
    private RawTransactionRepository transactions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        transactions = mock(RawTransactionRepository.class);
        var service = new AnalyticsService(transactions, mock(DeletedUserRepository.class));
        mvc = standaloneSetup(new AnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binder) {
                        return Jwt.withTokenValue("test").header("alg", "none").claim("userId", 7L).build();
                    }
                }).build();
    }

    @Test
    void summaryAlwaysUsesJwtOwnerAndDefaultsToUtc() throws Exception {
        mvc.perform(get("/analytics/summary").param("accountId", "3").param("period", "MONTH")
                        .param("date", "2026-11-17").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.totalIncome").value(0))
                .andExpect(jsonPath("$.categories").isEmpty());
        verify(transactions).findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                7L, 3L, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void customRangeUsesRequestedTimezone() throws Exception {
        mvc.perform(get("/analytics/summary/range").param("accountId", "3")
                        .param("from", "2026-11-01").param("to", "2026-11-17").param("timezone", "Europe/Minsk"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timezone").value("Europe/Minsk"));
        verify(transactions).findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                7L, 3L, Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-11-17T21:00:00Z"));
    }

    @Test
    void malformedAndMissingParametersReturnBadRequest() throws Exception {
        mvc.perform(get("/analytics/summary").param("period", "DAY").param("date", "2026-11-17"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/analytics/summary").param("accountId", "3").param("period", "INVALID")
                        .param("date", "2026-11-17"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.period").exists());
        mvc.perform(get("/analytics/summary").param("accountId", "3").param("period", "DAY")
                        .param("date", "2026-02-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/analytics/summary").param("accountId", "0").param("period", "DAY")
                        .param("date", "2026-11-17"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/analytics/summary").param("accountId", "3").param("period", "DAY")
                        .param("date", "2026-11-17").param("timezone", "Invalid/Zone"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/analytics/summary/range").param("accountId", "3")
                        .param("from", "2026-11-17").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactions);
    }

    @Test
    void preservesStandardHttpErrorsAndHidesInternalDetails() throws Exception {
        mvc.perform(post("/analytics/summary")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/analytics/unknown")).andExpect(status().isNotFound());
        when(transactions.findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any())).thenThrow(new IllegalStateException("mongodb://internal-secret"));
        mvc.perform(get("/analytics/summary").param("accountId", "3").param("period", "DAY")
                        .param("date", "2026-11-17"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
