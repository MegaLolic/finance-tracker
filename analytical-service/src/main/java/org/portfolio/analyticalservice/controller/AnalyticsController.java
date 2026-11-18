package org.portfolio.analyticalservice.controller;

import org.portfolio.analyticalservice.dto.AnalyticsPeriod;
import org.portfolio.analyticalservice.dto.AnalyticsSummaryDto;
import org.portfolio.analyticalservice.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary(
            @RequestParam Long accountId,
            @RequestParam AnalyticsPeriod period,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "UTC") String timezone,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        return ResponseEntity.ok(analyticsService.getSummary(userId, accountId, period, date, timezone));
    }

    @GetMapping("/summary/range")
    public ResponseEntity<AnalyticsSummaryDto> getSummaryForRange(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "UTC") String timezone,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        return ResponseEntity.ok(analyticsService.getSummary(userId, accountId, from, to, timezone));
    }
}
