
package com.fintrack.account.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateAccountRequest {

    @NotBlank(message = "Account name is required.")
    @Size(max = 100, message = "Account name cannot exceed 100 characters.")
    private String name;

    @NotBlank(message = "Account type is required.")
    @Pattern(
        regexp = "CASH|SAVINGS|CURRENT|CREDIT_CARD|INVESTMENT|OTHER",
        message = "Invalid account type."
    )
    private String accountType;

    @NotBlank(message = "Currency is required.")
    @Pattern(
        regexp = "[A-Za-z]{3}",
        message = "Currency must be a three-letter code, such as INR."
    )
    private String currency = "INR";

    @DecimalMin(value = "0.0", message = "Opening balance cannot be negative.")
    @Digits(
        integer = 15,
        fraction = 4,
        message = "Opening balance supports up to 15 integer digits and 4 decimal places."
    )
    private BigDecimal openingBalance = BigDecimal.ZERO;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }
}
