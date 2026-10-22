package com.fintrack.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.fintrack.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "transaction_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "ledger_entries_transaction_id_fkey")
    )
    private FinancialTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "account_id",
        foreignKey = @ForeignKey(name = "ledger_entries_account_id_fkey")
    )
    private Account account;

    @Column(name = "system_account_code", length = 30)
    private String systemAccountCode;

    @Column(name = "entry_side", nullable = false, length = 10)
    private String entrySide;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {
        // Required by JPA.
    }

    public LedgerEntry(
            FinancialTransaction transaction,
            Account account,
            String systemAccountCode,
            String entrySide,
            BigDecimal amount) {

        this.transaction = transaction;
        this.account = account;
        this.systemAccountCode = systemAccountCode;
        this.entrySide = entrySide;
        this.amount = amount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public FinancialTransaction getTransaction() {
        return transaction;
    }

    public Account getAccount() {
        return account;
    }

    public String getSystemAccountCode() {
        return systemAccountCode;
    }

    public String getEntrySide() {
        return entrySide;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
