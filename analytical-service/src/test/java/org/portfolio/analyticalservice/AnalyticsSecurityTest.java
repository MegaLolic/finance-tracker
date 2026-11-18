package org.portfolio.analyticalservice;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.portfolio.analyticalservice.config.SecurityConfig;
import org.portfolio.analyticalservice.controller.AnalyticsController;
import org.portfolio.analyticalservice.dto.AnalyticsPeriod;
import org.portfolio.analyticalservice.service.AnalyticsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class AnalyticsSecurityTest {
    private static final String SECRET = "analytics-test-secret-".repeat(4);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AnalyticsController.class})
    static class TestConfig {
        @Bean
        AnalyticsService analyticsService() {
            return mock(AnalyticsService.class);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("jwt.secret", SECRET)));
        context.register(TestConfig.class);
        context.refresh();
        mvc = webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void missingInvalidExpiredRefreshAndOwnerlessTokensCannotReadReports() throws Exception {
        String url = "/analytics/summary?accountId=3&period=DAY&date=2026-11-17";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer " + token("refresh", 7L, false)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer " + token("access", null, false)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer " + token("access", 7L, true)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(AnalyticsService.class));
    }

    @Test
    void signedAccessTokenPassesAuthenticatedOwnerToService() throws Exception {
        mvc.perform(get("/analytics/summary?accountId=3&period=DAY&date=2026-11-17&userId=999")
                        .header("Authorization", "Bearer " + token("access", 7L, false)))
                .andExpect(status().isOk());
        verify(context.getBean(AnalyticsService.class))
                .getSummary(7L, 3L, AnalyticsPeriod.DAY, LocalDate.of(2026, 11, 17), "UTC");
    }

    private String token(String type, Long userId, boolean expired) {
        var claims = JwtClaimsSet.builder().subject("test").claim("type", type)
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().plusSeconds(expired ? -300 : 300));
        if (userId != null) { claims.claim("userId", userId); }
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes(StandardCharsets.UTF_8)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).build(), claims.build()))
                .getTokenValue();
    }
}
