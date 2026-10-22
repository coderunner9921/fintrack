package com.fintrack.account;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository
        extends JpaRepository<Account, UUID> {

    List<Account> findAllByUser_Id(UUID userId);

    Optional<Account> findByIdAndUser_Id(
            UUID accountId,
            UUID userId
    );
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM Account a
        WHERE a.id = :accountId
          AND a.user.id = :userId
        """)
    Optional<Account> findByIdAndUser_IdForUpdate(
            @Param("accountId") UUID accountId,
            @Param("userId") UUID userId);
}