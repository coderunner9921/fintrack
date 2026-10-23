
package com.fintrack.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fintrack.transaction.FinancialTransaction;

public interface AnalyticsRepository
        extends JpaRepository<FinancialTransaction, UUID> {

    @Query(value = """
        SELECT
            t.currency AS "currency",

            COALESCE(SUM(CASE
                WHEN t.transaction_type = 'INCOME'
                 AND NOT EXISTS (
                    SELECT 1
                    FROM transactions r
                    WHERE r.reversal_of_transaction_id = t.id
                 )
                THEN t.amount
                ELSE 0
            END), 0) AS "incomeTotal",

            COALESCE(SUM(CASE
                WHEN t.transaction_type = 'EXPENSE'
                 AND NOT EXISTS (
                    SELECT 1
                    FROM transactions r
                    WHERE r.reversal_of_transaction_id = t.id
                 )
                THEN t.amount
                ELSE 0
            END), 0) AS "expenseTotal",

            COALESCE(SUM(CASE
                WHEN t.transaction_type = 'INCOME'
                 AND NOT EXISTS (
                    SELECT 1
                    FROM transactions r
                    WHERE r.reversal_of_transaction_id = t.id
                 )
                THEN 1
                ELSE 0
            END), 0) AS "incomeCount",

            COALESCE(SUM(CASE
                WHEN t.transaction_type = 'EXPENSE'
                 AND NOT EXISTS (
                    SELECT 1
                    FROM transactions r
                    WHERE r.reversal_of_transaction_id = t.id
                 )
                THEN 1
                ELSE 0
            END), 0) AS "expenseCount"

        FROM transactions t
        WHERE t.user_id = :userId
          AND t.transaction_date BETWEEN :fromDate AND :toDate
          AND t.transaction_type IN ('INCOME', 'EXPENSE')
        GROUP BY t.currency
        ORDER BY t.currency
        """, nativeQuery = true)
    List<CurrencyAnalyticsRow> summarizeByCurrency(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(value = """
        SELECT
            t.currency AS "currency",
            t.category AS "categoryName",
            COALESCE(SUM(t.amount), 0) AS "total"

        FROM transactions t
        WHERE t.user_id = :userId
          AND t.transaction_date BETWEEN :fromDate AND :toDate
          AND t.transaction_type = 'EXPENSE'
          AND NOT EXISTS (
              SELECT 1
              FROM transactions r
              WHERE r.reversal_of_transaction_id = t.id
          )
        GROUP BY t.currency, t.category
        ORDER BY t.currency, "total" DESC, t.category
        """, nativeQuery = true)
    List<CategoryExpenseRow> summarizeExpensesByCategory(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
