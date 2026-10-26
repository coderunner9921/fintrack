package com.fintrack.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import com.fintrack.transaction.FinancialTransaction;
import com.fintrack.transaction.TransactionService;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.web.server.ResponseStatusException;
import com.fintrack.user.UserRepository;
import com.fintrack.account.AccountRepository;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.reconciliation.dto.ReconciliationImportResponse;
import com.fintrack.reconciliation.dto.StatementRowResponse;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReconciliationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () ->
                "0123456789012345678901234567890123456789012345678901234567890123");
    }

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                new User(
                        "Reconciliation Integration Test",
                        "reconciliation-test-" + UUID.randomUUID()
                                + "@example.com",
                        "test-password-hash"));

        account = accountRepository.saveAndFlush(
                new Account(
                        user,
                        "Reconciliation Savings",
                        "SAVINGS",
                        "INR",
                        new BigDecimal("1000.00")));
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
    }

    private long countImports() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_imports WHERE user_id = ?",
                Long.class,
                user.getId());
    }

    private long countStatementRows(UUID importId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statement_rows WHERE import_id = ?",
                Long.class,
                importId);
    }

    private long countTransactions() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                Long.class,
                user.getId());
    }

    private long countLedgerEntries() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ledger_entries le
                JOIN transactions t ON t.id = le.transaction_id
                WHERE t.user_id = ?
                """,
                Long.class,
                user.getId());
    }

    private BigDecimal accountBalance(UUID accountId) {
        return jdbcTemplate.queryForObject("""
                SELECT a.opening_balance + COALESCE(
                    SUM(
                        CASE
                            WHEN le.entry_side = 'DEBIT' THEN le.amount
                            WHEN le.entry_side = 'CREDIT' THEN -le.amount
                            ELSE 0
                        END
                    ), 0
                )
                FROM accounts a
                LEFT JOIN ledger_entries le ON le.account_id = a.id
                WHERE a.id = ?
                GROUP BY a.id, a.opening_balance
                """,
                BigDecimal.class,
                accountId);
    }

    @Test
    void persistsImportAndRowsAndDoesNotChangeFinancialRecords()
            throws Exception {

        String content = """
                date,description,amount,currency,reference
                2026-10-20,Grocery Store,-450.00,INR,UPI123
                2026-10-20,Grocery Store,-450.00,INR,UPI123
                2026-10-21,International Purchase,-25.00,USD,USD123
                2026-10-22,Unrecognized Purchase,-75.00,INR,UPI999
                """;

        long importsBefore = countImports();
        long transactionsBefore = countTransactions();
        long ledgerEntriesBefore = countLedgerEntries();
        BigDecimal balanceBefore = accountBalance(account.getId());

        ReconciliationImportResponse response =
                reconciliationService.importStatement(
                        user.getId(),
                        account.getId(),
                        "integration-statement.csv",
                        csv(content));

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.status())
        	.isEqualTo("COMPLETED");
        assertThat(response.totalRows()).isEqualTo(4);
        assertThat(response.matchedRows()).isZero();
        assertThat(response.unmatchedRows()).isEqualTo(2);
        assertThat(response.duplicateRows()).isEqualTo(1);
        assertThat(response.mismatchedRows()).isEqualTo(1);

        assertThat(countImports()).isEqualTo(importsBefore + 1);
        assertThat(countStatementRows(response.id())).isEqualTo(4);

        List<StatementRowResponse> rows =
                reconciliationService.getRows(user.getId(), response.id());

        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).status()).isEqualTo("UNMATCHED");
        assertThat(rows.get(1).status()).isEqualTo("DUPLICATE");
        assertThat(rows.get(2).status()).isEqualTo("MISMATCHED");
        assertThat(rows.get(3).status()).isEqualTo("UNMATCHED");

        ReconciliationImportResponse fetched =
                reconciliationService.getImport(
                        user.getId(), response.id());

        assertThat(fetched.id()).isEqualTo(response.id());
        assertThat(fetched.totalRows()).isEqualTo(4);
        assertThat(fetched.status()).isEqualTo("COMPLETED");

        List<ReconciliationImportResponse> imports =
                reconciliationService.getImports(user.getId());

        assertThat(imports)
                .anySatisfy(item ->
                        assertThat(item.id()).isEqualTo(response.id()));

        // Reconciliation must never create or alter financial records.
        assertThat(countTransactions()).isEqualTo(transactionsBefore);
        assertThat(countLedgerEntries()).isEqualTo(ledgerEntriesBefore);
        assertThat(accountBalance(account.getId()))
                .isEqualByComparingTo(balanceBefore);
    }

    @Test
    void matchesStatementRowToPersistedExpenseTransaction()
            throws Exception {

        // Arrange: create a real expense in PostgreSQL.
        LocalDate transactionDate = LocalDate.of(2026, 10, 20);

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTransactionType("EXPENSE");
        request.setDescription("Integration test expense");
        request.setCategory("Food");
        request.setAmount(new BigDecimal("450.00"));
        request.setCurrency("INR");
        request.setTransactionDate(transactionDate);
        request.setSourceAccountId(account.getId());
        request.setDestinationAccountId(null);

        FinancialTransaction transaction =
                transactionService.createTransaction(user.getId(), request);

        // Record the financial state before reconciliation.
        long transactionsBefore = countTransactions();
        long ledgerEntriesBefore = countLedgerEntries();
        BigDecimal balanceBefore = accountBalance(account.getId());

        String content = """
                date,description,amount,currency,reference
                2026-10-20,Integration test expense,-450.00,INR,UPI-MATCH-001
                """;

        // Act: import a statement row matching the persisted expense.
        ReconciliationImportResponse response =
                reconciliationService.importStatement(
                        user.getId(),
                        account.getId(),
                        "matching-statement.csv",
                        csv(content));

        // Assert: the import and statement row were persisted.
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.matchedRows()).isEqualTo(1);
        assertThat(response.unmatchedRows()).isZero();
        assertThat(response.duplicateRows()).isZero();
        assertThat(response.mismatchedRows()).isZero();

        List<StatementRowResponse> rows =
                reconciliationService.getRows(user.getId(), response.id());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("MATCHED");

        List<Map<String, Object>> persistedRows = jdbcTemplate.queryForList(
                """
                SELECT id, import_id, row_number, status, matched_transaction_id
                FROM statement_rows
                WHERE import_id = ?
                """,
                response.id()
        );

        System.out.println("Reconciliation import ID: " + response.id());
        System.out.println("Persisted statement rows: " + persistedRows);

        assertThat(persistedRows).hasSize(1);
        assertThat(persistedRows.get(0).get("status").toString())
                .isEqualTo("MATCHED");

        UUID matchedTransactionId = (UUID) persistedRows.get(0)
                .get("matched_transaction_id");

        assertThat(matchedTransactionId).isEqualTo(transaction.getId());

        assertThat(matchedTransactionId).isEqualTo(transaction.getId());

        // Reconciliation must not create additional financial records.
        assertThat(countTransactions()).isEqualTo(transactionsBefore);
        assertThat(countLedgerEntries()).isEqualTo(ledgerEntriesBefore);
        assertThat(accountBalance(account.getId()))
                .isEqualByComparingTo(balanceBefore);
    }

    @Test
    void marksStatementRowMismatchedWhenAmountDiffersFromTransaction()
            throws Exception {

        // Arrange: create a real expense transaction for ₹450.
        LocalDate transactionDate = LocalDate.of(2026, 10, 20);

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTransactionType("EXPENSE");
        request.setDescription("Integration test expense");
        request.setCategory("Food");
        request.setAmount(new BigDecimal("450.00"));
        request.setCurrency("INR");
        request.setTransactionDate(transactionDate);
        request.setSourceAccountId(account.getId());
        request.setDestinationAccountId(null);

        transactionService.createTransaction(user.getId(), request);

        // Record financial state before reconciliation.
        long transactionsBefore = countTransactions();
        long ledgerEntriesBefore = countLedgerEntries();
        BigDecimal balanceBefore = accountBalance(account.getId());

        String content = """
                date,description,amount,currency,reference
                2026-10-20,Integration test expense,-500.00,INR,UPI-MISMATCH-001
                """;

        // Act: import a statement amount that differs from the transaction.
        ReconciliationImportResponse response =
                reconciliationService.importStatement(
                        user.getId(),
                        account.getId(),
                        "mismatched-amount-statement.csv",
                        csv(content));

        // Assert: the row is classified as mismatched.
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.matchedRows()).isZero();
        assertThat(response.unmatchedRows()).isZero();
        assertThat(response.duplicateRows()).isZero();
        assertThat(response.mismatchedRows()).isEqualTo(1);

        List<StatementRowResponse> rows =
                reconciliationService.getRows(user.getId(), response.id());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("MISMATCHED");

        // Verify that no transaction was incorrectly linked.
        List<Map<String, Object>> persistedRows = jdbcTemplate.queryForList(
                """
                SELECT matched_transaction_id
                FROM statement_rows
                WHERE import_id = ?
                """,
                response.id());

        assertThat(persistedRows).hasSize(1);
        assertThat(persistedRows.get(0).get("matched_transaction_id"))
                .isNull();

        // Reconciliation must not change financial records or balances.
        assertThat(countTransactions()).isEqualTo(transactionsBefore);
        assertThat(countLedgerEntries()).isEqualTo(ledgerEntriesBefore);
        assertThat(accountBalance(account.getId()))
                .isEqualByComparingTo(balanceBefore);
    }

    @Test
    void rejectsImportIntoAnotherUsersAccount() throws Exception {
        User anotherUser = userRepository.saveAndFlush(new User(
                "Another User",
                "reconciliation-other-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        Account anotherAccount = accountRepository.saveAndFlush(new Account(
                anotherUser,
                "Other Savings",
                "SAVINGS",
                "INR",
                new BigDecimal("1000.00")));

        long importsBefore = countImports();
        long rowsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statement_rows", Long.class);

        String content = """
                date,description,amount,currency,reference
                2026-10-20,Test Purchase,-50.00,INR,OWNERSHIP-001
                """;

        assertThatThrownBy(() ->
                reconciliationService.importStatement(
                        user.getId(),
                        anotherAccount.getId(),
                        "ownership-test.csv",
                        csv(content)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        assertThat(countImports()).isEqualTo(importsBefore);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statement_rows", Long.class))
                .isEqualTo(rowsBefore);
    }

    @Test
    void preventsAnotherUserFromReadingImportAndRows() throws Exception {
        String content = """
                date,description,amount,currency,reference
                2026-10-20,Unrecognized Purchase,-75.00,INR,READ-OWNERSHIP-001
                """;

        ReconciliationImportResponse response =
                reconciliationService.importStatement(
                        user.getId(),
                        account.getId(),
                        "read-ownership-test.csv",
                        csv(content));

        User anotherUser = userRepository.saveAndFlush(new User(
                "Another Reader",
                "reconciliation-reader-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        assertThatThrownBy(() ->
                reconciliationService.getImport(
                        anotherUser.getId(), response.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        assertThatThrownBy(() ->
                reconciliationService.getRows(
                        anotherUser.getId(), response.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

	@Test
	void invalidCsvDoesNotPersistImportOrStatementRows() {
	    long importsBefore = countImports();
	    long rowsBefore = jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM statement_rows", Long.class);
	
	    String content = """
	            date,description,amount,currency,reference
	            NOT-A-DATE,Invalid Purchase,-50.00,INR,INVALID-001
	            """;
	
	    assertThatThrownBy(() ->
	            reconciliationService.importStatement(
	                    user.getId(),
	                    account.getId(),
	                    "invalid-date-test.csv",
	                    csv(content)))
	            .isInstanceOf(CsvImportValidationException.class)
	            .hasMessageContaining("Invalid date");
	
	    assertThat(countImports()).isEqualTo(importsBefore);
	
	    assertThat(jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM statement_rows", Long.class))
	            .isEqualTo(rowsBefore);
	}

}
