package com.fintrack.transaction;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, UUID> {
	@Query("""
		    SELECT COALESCE(SUM(le.amount), 0)
		    FROM LedgerEntry le
		    WHERE le.account.id = :accountId
		      AND le.entrySide = :entrySide
		    """)
		BigDecimal sumAmountByAccountAndEntrySide(
		        @Param("accountId") UUID accountId,
		        @Param("entrySide") String entrySide);
	
    List<LedgerEntry> findAllByTransaction_Id(UUID transactionId);
}
