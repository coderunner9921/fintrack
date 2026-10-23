package com.fintrack.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record CurrencyAnalyticsResponse(
        String currency,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netCashFlow,
        long incomeTransactionCount,
        long expenseTransactionCount,
        List<CategoryExpenseResponse> expensesByCategory
) {
}