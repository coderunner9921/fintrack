package com.fintrack.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, UUID> {

    List<FinancialTransaction> findAllByUser_IdOrderByTransactionDateDesc(
            UUID userId);

    Optional<FinancialTransaction> findByIdAndUser_Id(
            UUID transactionId,
            UUID userId);
    
    Optional<FinancialTransaction> findByUser_IdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM FinancialTransaction t
        WHERE t.id = :transactionId
          AND t.user.id = :userId
        """)
    Optional<FinancialTransaction> findByIdAndUser_IdForUpdate(
            @Param("transactionId") UUID transactionId,
            @Param("userId") UUID userId);

    boolean existsByReversalOfTransaction_Id(UUID transactionId);
    
    boolean existsByCategoryEntity_Id(UUID categoryId);
}
