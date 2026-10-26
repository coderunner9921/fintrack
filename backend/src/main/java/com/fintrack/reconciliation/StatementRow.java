package com.fintrack.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fintrack.transaction.FinancialTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "statement_rows",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_statement_rows_import_row",
        columnNames = {"import_id", "row_number"}
    )
)
public class StatementRow {

    public enum RowStatus {
        PENDING,
        MATCHED,
        UNMATCHED,
        DUPLICATE,
        MISMATCHED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private ReconciliationImport reconciliationImport;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RowStatus status = RowStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_transaction_id")
    private FinancialTransaction matchedTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_row_id")
    private StatementRow duplicateOfRow;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected StatementRow() {
    }

    public StatementRow(ReconciliationImport reconciliationImport,
                        int rowNumber,
                        LocalDate statementDate,
                        String description,
                        BigDecimal amount,
                        String currency,
                        String reference) {
        this.reconciliationImport = reconciliationImport;
        this.rowNumber = rowNumber;
        this.statementDate = statementDate;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = RowStatus.PENDING;
        }
    }

    public void markMatched(FinancialTransaction transaction) {
        this.matchedTransaction = transaction;
        this.duplicateOfRow = null;
        this.status = RowStatus.MATCHED;
    }

    public void markUnmatched() {
        this.matchedTransaction = null;
        this.duplicateOfRow = null;
        this.status = RowStatus.UNMATCHED;
    }

    public void markDuplicate(StatementRow originalRow) {
        this.matchedTransaction = null;
        this.duplicateOfRow = originalRow;
        this.status = RowStatus.DUPLICATE;
    }

    public void markMismatched() {
        this.matchedTransaction = null;
        this.duplicateOfRow = null;
        this.status = RowStatus.MISMATCHED;
    }

    public UUID getId() {
        return id;
    }

    public ReconciliationImport getReconciliationImport() {
        return reconciliationImport;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReference() {
        return reference;
    }

    public RowStatus getStatus() {
        return status;
    }

    public FinancialTransaction getMatchedTransaction() {
        return matchedTransaction;
    }

    public StatementRow getDuplicateOfRow() {
        return duplicateOfRow;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
