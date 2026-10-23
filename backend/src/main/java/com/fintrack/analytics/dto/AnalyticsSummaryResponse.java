package com.fintrack.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsSummaryResponse(
        LocalDate from,
        LocalDate to,
        List<CurrencyAnalyticsResponse> currencies
) {
}