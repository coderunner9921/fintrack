package com.fintrack.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedStatementRow(
        int rowNumber,
        LocalDate date,
        String description,
        BigDecimal amount,
        String currency,
        String reference) {
}
