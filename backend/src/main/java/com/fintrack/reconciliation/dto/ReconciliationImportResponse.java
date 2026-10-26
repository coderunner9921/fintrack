package com.fintrack.reconciliation.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fintrack.reconciliation.ReconciliationImport;

public record ReconciliationImportResponse(
        UUID id,
        UUID accountId,
        String originalFilename,
        String status,
        int totalRows,
        int matchedRows,
        int unmatchedRows,
        int duplicateRows,
        int mismatchedRows,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {

    public static ReconciliationImportResponse from(
            ReconciliationImport reconciliationImport) {

        return new ReconciliationImportResponse(
                reconciliationImport.getId(),
                reconciliationImport.getAccount().getId(),
                reconciliationImport.getOriginalFilename(),
                reconciliationImport.getStatus().name(),
                reconciliationImport.getTotalRows(),
                reconciliationImport.getMatchedRows(),
                reconciliationImport.getUnmatchedRows(),
                reconciliationImport.getDuplicateRows(),
                reconciliationImport.getMismatchedRows(),
                reconciliationImport.getCreatedAt(),
                reconciliationImport.getCompletedAt());
    }
}
