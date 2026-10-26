package com.fintrack.reconciliation;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fintrack.account.Account;
import com.fintrack.user.User;
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

@Entity
@Table(name = "reconciliation_imports")
public class ReconciliationImport {

    public enum ImportStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.PROCESSING;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "matched_rows", nullable = false)
    private int matchedRows;

    @Column(name = "unmatched_rows", nullable = false)
    private int unmatchedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "mismatched_rows", nullable = false)
    private int mismatchedRows;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected ReconciliationImport() {
    }

    public ReconciliationImport(User user, Account account,
                                String originalFilename) {
        this.user = user;
        this.account = account;
        this.originalFilename = originalFilename;
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = ImportStatus.PROCESSING;
        }
    }

    public void complete(int totalRows, int matchedRows,
                         int unmatchedRows, int duplicateRows,
                         int mismatchedRows) {
        this.totalRows = totalRows;
        this.matchedRows = matchedRows;
        this.unmatchedRows = unmatchedRows;
        this.duplicateRows = duplicateRows;
        this.mismatchedRows = mismatchedRows;
        this.status = ImportStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = ImportStatus.FAILED;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getMatchedRows() {
        return matchedRows;
    }

    public int getUnmatchedRows() {
        return unmatchedRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getMismatchedRows() {
        return mismatchedRows;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
