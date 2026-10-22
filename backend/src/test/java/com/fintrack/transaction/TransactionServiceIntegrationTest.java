package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.dao.DataIntegrityViolationException;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.transaction.dto.CreateTransactionRequest;
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
class TransactionServiceIntegrationTest {

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
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                new User(
                        "Transaction Integration Test",
                        "transaction-test-" + UUID.randomUUID()
                                + "@example.com",
                        "test-password-hash"));

        // Use a fresh account with a known opening balance for each test.
        accountRepository.saveAndFlush(
                new Account(
                        user,
                        "Integration Savings",
                        "SAVINGS",
                        "INR",
                        new BigDecimal("100.00")));
    }

    private Account createAccount(BigDecimal openingBalance) {
        return accountRepository.saveAndFlush(
                new Account(
                        user,
                        "Savings " + UUID.randomUUID(),
                        "SAVINGS",
                        "INR",
                        openingBalance));
    }

    private CreateTransactionRequest expenseRequest(
            UUID accountId, String amount) {

        CreateTransactionRequest request =
                new CreateTransactionRequest();

        request.setTransactionType("EXPENSE");
        request.setDescription("Integration test expense");
        request.setCategory("Testing");
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("INR");
        request.setTransactionDate(LocalDate.now());
        request.setSourceAccountId(accountId);
        request.setDestinationAccountId(null);

        return request;
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

    @Test
    void insufficientFundsCreatesNoTransactionOrLedgerEntries() {
        Account account = createAccount(new BigDecimal("50.00"));

        long transactionsBefore = countTransactions();
        long entriesBefore = countLedgerEntries();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(
                        user.getId(),
                        expenseRequest(account.getId(), "80.00")));

        assertThat(countTransactions()).isEqualTo(transactionsBefore);
        assertThat(countLedgerEntries()).isEqualTo(entriesBefore);
    }

    @Test
    void concurrentExpensesCannotSpendTheSameBalanceTwice()
            throws Exception {

        // Both requests will try to spend 80 from an account holding 100.
        Account account = createAccount(new BigDecimal("100.00"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start the first request.");
                }

                try {
                    transactionService.createTransaction(
                            user.getId(),
                            expenseRequest(account.getId(), "80.00"));
                    return true;
                } catch (IllegalArgumentException exception) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start the second request.");
                }

                try {
                    transactionService.createTransaction(
                            user.getId(),
                            expenseRequest(account.getId(), "80.00"));
                    return true;
                } catch (IllegalArgumentException exception) {
                    return false;
                }
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            // Release both requests as close together as possible.
            start.countDown();

            boolean firstSucceeded = first.get(30, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(30, TimeUnit.SECONDS);

            // Exactly one expense should succeed.
            assertThat((firstSucceeded ? 1 : 0)
                    + (secondSucceeded ? 1 : 0)).isEqualTo(1);

            // One successful expense should create two ledger entries.
            assertThat(countTransactions()).isEqualTo(1);
            assertThat(countLedgerEntries()).isEqualTo(2);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
    

    @Test
    void repeatingSameIdempotencyKeyAndRequestCreatesOnlyOneTransaction() {
        Account account = createAccount(new BigDecimal("100.00"));
        CreateTransactionRequest request =
                expenseRequest(account.getId(), "25.00");

        FinancialTransaction first = transactionService.createTransaction(
                user.getId(), request, "integration-retry-001");

        FinancialTransaction second = transactionService.createTransaction(
                user.getId(), request, "integration-retry-001");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(countTransactions()).isEqualTo(1);
        assertThat(countLedgerEntries()).isEqualTo(2);
    }

    @Test
    void reusingIdempotencyKeyForDifferentRequestIsRejected() {
        Account account = createAccount(new BigDecimal("100.00"));

        FinancialTransaction first = transactionService.createTransaction(
                user.getId(),
                expenseRequest(account.getId(), "25.00"),
                "integration-retry-002");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(
                        user.getId(),
                        expenseRequest(account.getId(), "35.00"),
                        "integration-retry-002"));

        assertThat(countTransactions()).isEqualTo(1);
        assertThat(countLedgerEntries()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE id = ?",
                Long.class,
                first.getId())).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyCreateOnlyOneTransaction()
            throws Exception {

        Account account = createAccount(new BigDecimal("100.00"));
        CreateTransactionRequest request =
                expenseRequest(account.getId(), "25.00");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<FinancialTransaction> first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start the first request.");
                }

                return transactionService.createTransaction(
                        user.getId(), request, "integration-retry-003");
            });

            Future<FinancialTransaction> second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start the second request.");
                }

                return transactionService.createTransaction(
                        user.getId(), request, "integration-retry-003");
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            FinancialTransaction firstResult =
                    first.get(30, TimeUnit.SECONDS);
            FinancialTransaction secondResult =
                    second.get(30, TimeUnit.SECONDS);

            assertThat(secondResult.getId()).isEqualTo(firstResult.getId());
            assertThat(countTransactions()).isEqualTo(1);
            assertThat(countLedgerEntries()).isEqualTo(2);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
    

	@Test
	void databaseRejectsTransactionUsingAnotherUsersAccount() {
	    // Create a different user who owns the account.
	    User otherUser = userRepository.saveAndFlush(
	            new User(
	                    "Other Integration User",
	                    "other-user-" + UUID.randomUUID() + "@example.com",
	                    "test-password-hash"));
	
	    Account otherUsersAccount = accountRepository.saveAndFlush(
	            new Account(
	                    otherUser,
	                    "Other User Savings",
	                    "SAVINGS",
	                    "INR",
	                    new BigDecimal("100.00")));
	
	    UUID transactionId = UUID.randomUUID();
	
	    // Attempt to create an EXPENSE for our test user,
	    // but reference an account belonging to the other user.
	    assertThrows(
	            DataIntegrityViolationException.class,
	            () -> jdbcTemplate.update("""
	                    INSERT INTO transactions (
	                        id,
	                        user_id,
	                        transaction_type,
	                        description,
	                        category,
	                        amount,
	                        currency,
	                        transaction_date,
	                        source_account_id,
	                        destination_account_id
	                    )
	                    VALUES (?, ?, 'EXPENSE', ?, ?, ?, ?, ?, ?, NULL)
	                    """,
	                    transactionId,
	                    user.getId(),
	                    "Cross-user ownership test",
	                    "Testing",
	                    new BigDecimal("10.00"),
	                    "INR",
	                    LocalDate.now(),
	                    otherUsersAccount.getId()));
	
	    // The invalid transaction must not have been stored.
	    assertThat(jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM transactions WHERE id = ?",
	            Long.class,
	            transactionId)).isZero();
	}
	
	@Test
	void successfulTransferMovesMoneyAndCreatesBalancedLedgerEntries() {
	    Account source = createAccount(new BigDecimal("1000.00"));
	    Account destination = createAccount(new BigDecimal("200.00"));

	    CreateTransactionRequest request = new CreateTransactionRequest();
	    request.setTransactionType("TRANSFER");
	    request.setDescription("Integration test transfer");
	    request.setCategory("Testing");
	    request.setAmount(new BigDecimal("300.00"));
	    request.setCurrency("INR");
	    request.setTransactionDate(LocalDate.now());
	    request.setSourceAccountId(source.getId());
	    request.setDestinationAccountId(destination.getId());

	    FinancialTransaction transaction =
	            transactionService.createTransaction(
	                    user.getId(),
	                    request,
	                    "integration-transfer-success-001");

	    // Verify the transfer created one transaction and two ledger entries.
	    assertThat(transaction).isNotNull();
	    assertThat(countTransactions()).isEqualTo(1);
	    assertThat(countLedgerEntries()).isEqualTo(2);

	    // Calculate the source account balance from its ledger.
	    BigDecimal sourceBalance = jdbcTemplate.queryForObject("""
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
	            source.getId());

	    // Calculate the destination account balance from its ledger.
	    BigDecimal destinationBalance = jdbcTemplate.queryForObject("""
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
	            destination.getId());

	    assertThat(sourceBalance).isEqualByComparingTo("700.00");
	    assertThat(destinationBalance).isEqualByComparingTo("500.00");

	    // Verify that the transfer's debit and credit totals balance.
	    BigDecimal totalDebits = jdbcTemplate.queryForObject("""
	            SELECT COALESCE(SUM(amount), 0)
	            FROM ledger_entries
	            WHERE transaction_id = ? AND entry_side = 'DEBIT'
	            """,
	            BigDecimal.class,
	            transaction.getId());

	    BigDecimal totalCredits = jdbcTemplate.queryForObject("""
	            SELECT COALESCE(SUM(amount), 0)
	            FROM ledger_entries
	            WHERE transaction_id = ? AND entry_side = 'CREDIT'
	            """,
	            BigDecimal.class,
	            transaction.getId());

	    assertThat(totalDebits).isEqualByComparingTo("300.00");
	    assertThat(totalCredits).isEqualByComparingTo("300.00");
	    assertThat(totalDebits).isEqualByComparingTo(totalCredits);
	}

	@Test
	void ledgerInsertFailureRollsBackTransactionAndLedgerEntries() {
	    Account account = createAccount(new BigDecimal("100.00"));
	
	    long transactionsBefore = countTransactions();
	    long entriesBefore = countLedgerEntries();
	
	    // Test-only PostgreSQL trigger: deliberately reject ledger inserts.
	    jdbcTemplate.execute("""
	            CREATE OR REPLACE FUNCTION test_fail_ledger_insert()
	            RETURNS trigger AS $$
	            BEGIN
	                RAISE EXCEPTION 'Forced ledger insert failure for rollback test';
	            END;
	            $$ LANGUAGE plpgsql
	            """);
	
	    jdbcTemplate.execute("""
	            CREATE TRIGGER test_fail_ledger_insert_trigger
	            BEFORE INSERT ON ledger_entries
	            FOR EACH ROW
	            EXECUTE FUNCTION test_fail_ledger_insert()
	            """);
	
	    try {
	        assertThrows(
	                RuntimeException.class,
	                () -> transactionService.createTransaction(
	                        user.getId(),
	                        expenseRequest(account.getId(), "25.00"),
	                        "rollback-test-001"));
	    } finally {
	        // Always remove the test-only database objects.
	        jdbcTemplate.execute("""
	                DROP TRIGGER IF EXISTS test_fail_ledger_insert_trigger
	                ON ledger_entries
	                """);
	
	        jdbcTemplate.execute("""
	                DROP FUNCTION IF EXISTS test_fail_ledger_insert()
	                """);
	    }
	
	    // Neither the transaction nor any ledger entries may remain.
	    assertThat(countTransactions()).isEqualTo(transactionsBefore);
	    assertThat(countLedgerEntries()).isEqualTo(entriesBefore);
	
	    // Confirm the failed transaction was not persisted.
	    assertThat(jdbcTemplate.queryForObject(
	            """
	            SELECT COUNT(*)
	            FROM transactions
	            WHERE user_id = ? AND idempotency_key = ?
	            """,
	            Long.class,
	            user.getId(),
	            "rollback-test-001")).isZero();
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
	void reversingExpenseRestoresBalanceAndPreservesOriginalLedger() {
	    Account account = createAccount(new BigDecimal("100.00"));
	
	    FinancialTransaction original = transactionService.createTransaction(
	            user.getId(),
	            expenseRequest(account.getId(), "25.00"),
	            "reversal-expense-original-001");
	
	    assertThat(accountBalance(account.getId()))
	            .isEqualByComparingTo("75.00");
	
	    long originalEntriesBefore = jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM ledger_entries WHERE transaction_id = ?",
	            Long.class,
	            original.getId());
	
	    FinancialTransaction reversal = transactionService.reverseTransaction(
	            user.getId(), original.getId());
	
	    assertThat(reversal.getId()).isNotEqualTo(original.getId());
	    assertThat(reversal.getTransactionType()).isEqualTo("REVERSAL");
	    assertThat(reversal.getReversalOfTransaction().getId())
	            .isEqualTo(original.getId());
	
	    assertThat(accountBalance(account.getId()))
	            .isEqualByComparingTo("100.00");
	
	    // The original transaction and its ledger entries remain in place.
	    assertThat(jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM transactions WHERE id = ?",
	            Long.class,
	            original.getId())).isEqualTo(1);
	
	    assertThat(jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM ledger_entries WHERE transaction_id = ?",
	            Long.class,
	            original.getId())).isEqualTo(originalEntriesBefore);
	
	    // Each transaction has two ledger entries.
	    assertThat(jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM ledger_entries WHERE transaction_id = ?",
	            Long.class,
	            reversal.getId())).isEqualTo(2);
	
	    assertThat(countTransactions()).isEqualTo(2);
	    assertThat(countLedgerEntries()).isEqualTo(4);
	}
	
	
	@Test
	void reversingSameTransactionTwiceIsRejected() {
	    Account account = createAccount(new BigDecimal("100.00"));
	
	    FinancialTransaction original = transactionService.createTransaction(
	            user.getId(),
	            expenseRequest(account.getId(), "25.00"),
	            "reversal-expense-original-002");
	
	    transactionService.reverseTransaction(user.getId(), original.getId());
	
	    long transactionsAfterFirstReversal = countTransactions();
	    long entriesAfterFirstReversal = countLedgerEntries();
	
	    assertThrows(
	            IllegalArgumentException.class,
	            () -> transactionService.reverseTransaction(
	                    user.getId(), original.getId()));
	
	    assertThat(countTransactions())
	            .isEqualTo(transactionsAfterFirstReversal);
	    assertThat(countLedgerEntries())
	            .isEqualTo(entriesAfterFirstReversal);
	}
	
	
	@Test
	void userCannotReverseAnotherUsersTransaction() {
	    User otherUser = userRepository.saveAndFlush(
	            new User(
	                    "Another Integration User",
	                    "reversal-owner-" + UUID.randomUUID() + "@example.com",
	                    "test-password-hash"));
	
	    Account otherAccount = accountRepository.saveAndFlush(
	            new Account(
	                    otherUser,
	                    "Other User Savings",
	                    "SAVINGS",
	                    "INR",
	                    new BigDecimal("100.00")));
	
	    FinancialTransaction otherUsersTransaction =
	            transactionService.createTransaction(
	                    otherUser.getId(),
	                    expenseRequest(otherAccount.getId(), "20.00"),
	                    "reversal-foreign-owner-001");
	
	    long transactionsBefore = countTransactions();
	    long entriesBefore = countLedgerEntries();
	
	    assertThrows(
	            IllegalArgumentException.class,
	            () -> transactionService.reverseTransaction(
	                    user.getId(), otherUsersTransaction.getId()));
	
	    // No transaction or ledger entry was created for the current user.
	    assertThat(countTransactions()).isEqualTo(transactionsBefore);
	    assertThat(countLedgerEntries()).isEqualTo(entriesBefore);
	
	    // The other user's original transaction is still present.
	    assertThat(jdbcTemplate.queryForObject(
	            "SELECT COUNT(*) FROM transactions WHERE id = ?",
	            Long.class,
	            otherUsersTransaction.getId())).isEqualTo(1);
	}
	
	@Test
	void concurrentReversalsCreateOnlyOneReversal() throws Exception {
	    Account account = createAccount(new BigDecimal("100.00"));

	    FinancialTransaction original = transactionService.createTransaction(
	            user.getId(),
	            expenseRequest(account.getId(), "25.00"),
	            "concurrent-reversal-original-001");

	    CountDownLatch ready = new CountDownLatch(2);
	    CountDownLatch start = new CountDownLatch(1);
	    ExecutorService executor = Executors.newFixedThreadPool(2);

	    try {
	        java.util.concurrent.Callable<Boolean> attempt = () -> {
	            ready.countDown();

	            if (!start.await(10, TimeUnit.SECONDS)) {
	                throw new IllegalStateException(
	                        "Timed out waiting to start reversal.");
	            }

	            try {
	                transactionService.reverseTransaction(
	                        user.getId(), original.getId());
	                return true;
	            } catch (IllegalArgumentException
	                    | DataIntegrityViolationException exception) {
	                return false;
	            }
	        };

	        Future<Boolean> first = executor.submit(attempt);
	        Future<Boolean> second = executor.submit(attempt);

	        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
	        start.countDown();

	        boolean firstSucceeded = first.get(30, TimeUnit.SECONDS);
	        boolean secondSucceeded = second.get(30, TimeUnit.SECONDS);

	        assertThat((firstSucceeded ? 1 : 0)
	                + (secondSucceeded ? 1 : 0)).isEqualTo(1);

	        assertThat(jdbcTemplate.queryForObject("""
	                SELECT COUNT(*)
	                FROM transactions
	                WHERE reversal_of_transaction_id = ?
	                """, Long.class, original.getId())).isEqualTo(1);

	        assertThat(jdbcTemplate.queryForObject("""
	                SELECT COUNT(*)
	                FROM ledger_entries le
	                JOIN transactions t ON t.id = le.transaction_id
	                WHERE t.reversal_of_transaction_id = ?
	                """, Long.class, original.getId())).isEqualTo(2);

	        assertThat(accountBalance(account.getId()))
	                .isEqualByComparingTo("100.00");

	    } finally {
	        start.countDown();
	        executor.shutdownNow();
	    }
	}

	@Test
	void reversingTransferWithInsufficientDestinationFundsIsRejected() {
	    Account source = createAccount(new BigDecimal("1000.00"));
	    Account destination = createAccount(new BigDecimal("100.00"));
	
	    CreateTransactionRequest transferRequest =
	            new CreateTransactionRequest();
	    transferRequest.setTransactionType("TRANSFER");
	    transferRequest.setDescription("Transfer for reversal safety test");
	    transferRequest.setCategory("Testing");
	    transferRequest.setAmount(new BigDecimal("300.00"));
	    transferRequest.setCurrency("INR");
	    transferRequest.setTransactionDate(LocalDate.now());
	    transferRequest.setSourceAccountId(source.getId());
	    transferRequest.setDestinationAccountId(destination.getId());
	
	    FinancialTransaction original = transactionService.createTransaction(
	            user.getId(),
	            transferRequest,
	            "reversal-insufficient-transfer-001");
	
	    // Spend most of the transferred money from the destination.
	    transactionService.createTransaction(
	            user.getId(),
	            expenseRequest(destination.getId(), "250.00"),
	            "reversal-destination-spend-001");
	
	    assertThat(accountBalance(destination.getId()))
	            .isEqualByComparingTo("150.00");
	
	    long transactionsBefore = countTransactions();
	    long entriesBefore = countLedgerEntries();
	
	    assertThrows(
	            IllegalArgumentException.class,
	            () -> transactionService.reverseTransaction(
	                    user.getId(), original.getId()));
	
	    // Rejected reversal must not create partial records.
	    assertThat(countTransactions()).isEqualTo(transactionsBefore);
	    assertThat(countLedgerEntries()).isEqualTo(entriesBefore);
	
	    // The original transfer and subsequent expense remain recorded.
	    assertThat(accountBalance(destination.getId()))
	            .isEqualByComparingTo("150.00");
	
	    assertThat(jdbcTemplate.queryForObject("""
	            SELECT COUNT(*)
	            FROM transactions
	            WHERE reversal_of_transaction_id = ?
	            """, Long.class, original.getId())).isZero();
	}
}
