package com.fintrack.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateTransactionRequest {

    @NotBlank(message = "Transaction type is required.")
    @Pattern(
        regexp = "INCOME|EXPENSE|TRANSFER",
        message = "Transaction type must be INCOME, EXPENSE, or TRANSFER."
    )
    private String transactionType;

    @NotBlank(message = "Description is required.")
    @Size(max = 255, message = "Description cannot exceed 255 characters.")
    private String description;

    @NotBlank(message = "Category is required.")
    @Size(max = 80, message = "Category cannot exceed 80 characters.")
    private String category;

    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.0", inclusive = false,
        message = "Amount must be greater than zero.")
    @Digits(integer = 15, fraction = 4,
        message = "Amount supports up to 15 integer digits and 4 decimal places.")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required.")
    @Pattern(regexp = "[A-Za-z]{3}",
        message = "Currency must be a three-letter code, such as INR.")
    private String currency = "INR";

    @NotNull(message = "Transaction date is required.")
    private LocalDate transactionDate;

    // Required for EXPENSE and TRANSFER.
    // Must be null for INCOME.
    private UUID sourceAccountId;

    // Required for INCOME and TRANSFER.
    // Must be null for EXPENSE.
    private UUID destinationAccountId;

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(UUID sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(UUID destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }
}
