
package com.fintrack.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fintrack.reconciliation.StatementRow;

public record StatementRowResponse(
        UUID id,
        int rowNumber,
        LocalDate statementDate,
        String description,
        BigDecimal amount,
        String currency,
        String reference,
        String status,
        UUID matchedTransactionId,
        UUID duplicateOfRowId) {

    public static StatementRowResponse from(StatementRow row) {
        return new StatementRowResponse(
                row.getId(),
                row.getRowNumber(),
                row.getStatementDate(),
                row.getDescription(),
                row.getAmount(),
                row.getCurrency(),
                row.getReference(),
                row.getStatus().name(),
                row.getMatchedTransaction() == null
                        ? null : row.getMatchedTransaction().getId(),
                row.getDuplicateOfRow() == null
                        ? null : row.getDuplicateOfRow().getId());
    }
}
