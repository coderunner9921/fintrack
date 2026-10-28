package com.fintrack.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fintrack.transaction.FinancialTransaction;

public class TransactionResponse {

    private UUID id;
    private UUID reversalOfTransactionId;
    private String transactionType;
    private String description;
    private String category;
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private OffsetDateTime createdAt;

    public TransactionResponse(FinancialTransaction transaction) {
        this.id = transaction.getId();
        this.transactionType = transaction.getTransactionType();
        this.description = transaction.getDescription();
        this.category = transaction.getCategory();
        this.amount = transaction.getAmount();
        this.currency = transaction.getCurrency();
        this.transactionDate = transaction.getTransactionDate();
        this.createdAt = transaction.getCreatedAt();
        this.reversalOfTransactionId =
                transaction.getReversalOfTransaction() == null
                        ? null
                        : transaction.getReversalOfTransaction().getId();
        this.sourceAccountId = transaction.getSourceAccount() == null
                ? null : transaction.getSourceAccount().getId();

        this.destinationAccountId =
                transaction.getDestinationAccount() == null
                ? null : transaction.getDestinationAccount().getId();
    }

    public UUID getReversalOfTransactionId() {
        return reversalOfTransactionId;
    }

    public UUID getId() {
        return id;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
