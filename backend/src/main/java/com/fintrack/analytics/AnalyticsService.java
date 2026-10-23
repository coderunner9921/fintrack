package com.fintrack.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fintrack.analytics.dto.AnalyticsSummaryResponse;
import com.fintrack.analytics.dto.CurrencyAnalyticsResponse;
import com.fintrack.analytics.dto.CategoryExpenseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(
            UUID userId,
            LocalDate from,
            LocalDate to) {

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required");
        }

        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The start date must be on or before the end date");
        }

        List<CurrencyAnalyticsRow> currencyRows =
                analyticsRepository.summarizeByCurrency(userId, from, to);

        List<CategoryExpenseRow> categoryRows =
                analyticsRepository.summarizeExpensesByCategory(
                        userId, from, to);

        Map<String, List<CategoryExpenseResponse>> categoriesByCurrency =
                new HashMap<>();

        for (CategoryExpenseRow row : categoryRows) {
            categoriesByCurrency
                    .computeIfAbsent(
                            row.getCurrency(),
                            currency -> new ArrayList<>())
                    .add(new CategoryExpenseResponse(
                            row.getCategoryName(),
                            row.getTotal() == null
                                    ? BigDecimal.ZERO
                                    : row.getTotal()));
        }

        List<CurrencyAnalyticsResponse> currencies = new ArrayList<>();

        for (CurrencyAnalyticsRow row : currencyRows) {
            BigDecimal income = row.getIncomeTotal() == null
                    ? BigDecimal.ZERO
                    : row.getIncomeTotal();

            BigDecimal expenses = row.getExpenseTotal() == null
                    ? BigDecimal.ZERO
                    : row.getExpenseTotal();

            currencies.add(new CurrencyAnalyticsResponse(
                    row.getCurrency(),
                    income,
                    expenses,
                    income.subtract(expenses),
                    row.getIncomeCount() == null
                            ? 0L
                            : row.getIncomeCount(),
                    row.getExpenseCount() == null
                            ? 0L
                            : row.getExpenseCount(),
                    categoriesByCurrency.getOrDefault(
                            row.getCurrency(),
                            List.of())
            ));
        }

        return new AnalyticsSummaryResponse(from, to, currencies);
    }
}