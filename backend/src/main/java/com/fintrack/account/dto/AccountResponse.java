package com.fintrack.account.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fintrack.account.Account;

public class AccountResponse {

    private UUID id;
    private String name;
    private String accountType;
    private String currency;
    private BigDecimal openingBalance;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private BigDecimal currentBalance;

    public AccountResponse(Account account) {
        this.id = account.getId();
        this.name = account.getName();
        this.accountType = account.getAccountType();
        this.currency = account.getCurrency();
        this.openingBalance = account.getOpeningBalance();
        this.active = account.isActive();
        this.createdAt = account.getCreatedAt();
        this.updatedAt = account.getUpdatedAt();
    }

	public AccountResponse(Account account, BigDecimal currentBalance) {
	    this(account);
	    this.currentBalance = currentBalance;
	}


    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }
}
