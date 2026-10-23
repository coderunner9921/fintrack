package com.fintrack.analytics.dto;

import java.math.BigDecimal;

public record CategoryExpenseResponse(
        String categoryName,
        BigDecimal total
) {
}