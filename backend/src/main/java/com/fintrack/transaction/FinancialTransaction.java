package com.fintrack.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.fintrack.account.Account;
import com.fintrack.user.User;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.CheckConstraint;

@Entity
@Table(
    name = "transactions",
    check = {
    	@CheckConstraint(
    		name = "chk_transaction_type",
    	    constraint = "transaction_type IN "
    			      + "('INCOME', 'EXPENSE', 'TRANSFER', 'REVERSAL')"
    			),
        @CheckConstraint(
            name = "chk_transaction_amount",
            constraint = "amount > 0"
        ),
        @CheckConstraint(
            name = "chk_transaction_currency",
            constraint = "currency ~ '^[A-Z]{3}$'"
        )
    }
)
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reversal_of_transaction_id",
        foreignKey = @ForeignKey(
            name = "fk_transactions_reversal_original"
        )
    )
    private FinancialTransaction reversalOfTransaction;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "transactions_user_id_fkey")
    )
    private User user;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "category", nullable = false, length = 80)
    private String category;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
	    name = "category_id",
	    nullable = false,
	    foreignKey = @ForeignKey(name = "fk_transactions_category")	
)	
private com.fintrack.category.Category categoryEntity;


    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "source_account_id",
        foreignKey = @ForeignKey(name = "transactions_source_account_id_fkey")
    )
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "destination_account_id",
        foreignKey = @ForeignKey(name = "transactions_destination_account_id_fkey")
    )
    private Account destinationAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FinancialTransaction() {
        // Required by JPA.
    }

    public FinancialTransaction(
            User user,
            String transactionType,
            String description,
            String category,
            BigDecimal amount,
            String currency,
            LocalDate transactionDate,
            Account sourceAccount,
            Account destinationAccount,
            String idempotencyKey) {

        this.user = user;
        this.transactionType = transactionType;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.transactionDate = transactionDate;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getDescription() {
        return description;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
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

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void markAsReversalOf(FinancialTransaction original) {
        if (original == null) {
            throw new IllegalArgumentException(
                    "Original transaction is required.");
        }

        if ("REVERSAL".equals(original.getTransactionType())) {
            throw new IllegalArgumentException(
                    "A reversal transaction cannot be reversed.");
        }

        if (original.getId() == null) {
            throw new IllegalArgumentException(
                    "Original transaction must already be persisted.");
        }

        this.reversalOfTransaction = original;
    }
    
    public FinancialTransaction getReversalOfTransaction() {
        return reversalOfTransaction;
    }

	public com.fintrack.category.Category getCategoryEntity() {
	    return categoryEntity;
	}
	
	public void setCategoryEntity(
	        com.fintrack.category.Category categoryEntity) {
	    if (categoryEntity == null) {
	        throw new IllegalArgumentException("Category is required.");
	    }
	
	    this.categoryEntity = categoryEntity;
	}	
}
