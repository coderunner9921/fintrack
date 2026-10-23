package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import com.fintrack.analytics.AnalyticsService;
import com.fintrack.analytics.dto.AnalyticsSummaryResponse;
import com.fintrack.analytics.dto.CurrencyAnalyticsResponse;
import com.fintrack.analytics.dto.CategoryExpenseResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
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
import com.fintrack.category.CategoryService;
import org.springframework.web.server.ResponseStatusException;

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
    private AnalyticsService analyticsService;
    
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private CategoryService categoryService;

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

    private CreateTransactionRequest incomeRequest(
            UUID accountId,
            String amount,
            LocalDate transactionDate) {

        CreateTransactionRequest request =
                new CreateTransactionRequest();

        request.setTransactionType("INCOME");
        request.setDescription("Integration test income");
        request.setCategory("Salary");
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("INR");
        request.setTransactionDate(transactionDate);
        request.setSourceAccountId(null);
        request.setDestinationAccountId(accountId);

        return request;
    }
    
    private CreateTransactionRequest expenseRequest(
            UUID accountId, String amount) {

        CreateTransactionRequest request =
                new CreateTransactionRequest();

        request.setTransactionType("EXPENSE");
        request.setDescription("Integration test expense");
        request.setCategory("Food");
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
	                        category_id,
	                        amount,
	                        currency,
	                        transaction_date,
	                        source_account_id,
	                        destination_account_id
	                    )
	                    VALUES (
	                        ?, ?, 'EXPENSE', ?, 'Food',
	                        (SELECT id FROM categories
	                         WHERE is_system = TRUE AND name = 'Food'),
	                        ?, ?, ?, ?, NULL
	                    )
	                    """,
	                    transactionId,
	                    user.getId(),
	                    "Cross-user ownership test",
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
	    request.setCategory("Food");
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
	void userCannotUseAnotherUsersCustomCategory() {
	    // Create a second user who owns the custom category.
	    User otherUser = userRepository.saveAndFlush(
	            new User(
	                    "Other Category Owner",
	                    "category-owner-" + UUID.randomUUID() + "@example.com",
	                    "test-password-hash"));
	
	    Category privateCategory = categoryRepository.saveAndFlush(
	            new Category(
	                    otherUser,
	                    "PrivateCategory",
	                    "BOTH",
	                    false));
	
	    // Create an account owned by the current test user.
	    Account account = createAccount(new BigDecimal("100.00"));
	
	    // Change the otherwise-valid expense request to use the other
	    // user's custom category.
	    CreateTransactionRequest request =
	            expenseRequest(account.getId(), "25.00");
	    request.setCategory("PrivateCategory");
	
	    long transactionsBefore = countTransactions();
	    long entriesBefore = countLedgerEntries();
	
	    // The category must not be visible to this user.
	    assertThrows(
	            IllegalArgumentException.class,
	            () -> transactionService.createTransaction(
	                    user.getId(), request));
	
	    // The rejected request must not create financial records.
	    assertThat(countTransactions()).isEqualTo(transactionsBefore);
	    assertThat(countLedgerEntries()).isEqualTo(entriesBefore);
	
	    // Confirm that the category still belongs to the other user.
	    assertThat(privateCategory.getUser().getId())
	            .isEqualTo(otherUser.getId());
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
	    transferRequest.setCategory("Food");
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
	
	@Test
	void userCanCreateCustomCategory() {
	    Category category = categoryService.createCategory(
	            user.getId(), "Pet Care", "EXPENSE");
	
	    assertThat(category.getId()).isNotNull();
	    assertThat(category.getName()).isEqualTo("Pet Care");
	    assertThat(category.getCategoryType()).isEqualTo("EXPENSE");
	    assertThat(category.isSystem()).isFalse();
	    assertThat(category.getUser().getId()).isEqualTo(user.getId());
	}
	
	@Test
	void userCannotDeleteSystemCategory() {
	    Category systemCategory = categoryRepository
	            .findByNameIgnoreCaseAndSystemTrue("Food")
	            .orElseThrow();
	
	    assertThrows(
	            ResponseStatusException.class,
	            () -> categoryService.deleteCategory(
	                    user.getId(), systemCategory.getId()));
	
	    assertThat(categoryRepository.existsById(systemCategory.getId()))
	            .isTrue();
	}

	@Test
	void userCannotDeleteCategoryUsedByTransaction() {
	    Category category = categoryService.createCategory(
	            user.getId(), "Subscriptions", "EXPENSE");
	
	    Account account = createAccount(new BigDecimal("100.00"));
	    CreateTransactionRequest request =
	            expenseRequest(account.getId(), "25.00");
	    request.setCategory("Subscriptions");
	
	    transactionService.createTransaction(user.getId(), request);
	
	    assertThrows(
	            ResponseStatusException.class,
	            () -> categoryService.deleteCategory(
	                    user.getId(), category.getId()));
	
	    assertThat(categoryRepository.existsById(category.getId()))
	            .isTrue();
	}

	@Test
	void userCanUpdateOwnCustomCategory() {
	    Category category = categoryService.createCategory(
	            user.getId(), "Pet Care", "EXPENSE");
	
	    Category updated = categoryService.updateCategory(
	            user.getId(), category.getId(), "Pet Expenses", "EXPENSE");
	
	    assertThat(updated.getId()).isEqualTo(category.getId());
	    assertThat(updated.getName()).isEqualTo("Pet Expenses");
	    assertThat(updated.getCategoryType()).isEqualTo("EXPENSE");
	    assertThat(updated.getUser().getId()).isEqualTo(user.getId());
	}
	
	@Test
	void userCannotUpdateOrDeleteAnotherUsersCustomCategory() {
	    User otherUser = userRepository.saveAndFlush(
	    		new User(
	                    "Other Category Owner",
	                    "category-owner-" + UUID.randomUUID() + "@example.com",
	                    "test-password-hash"));
	
	    Category privateCategory = categoryRepository.saveAndFlush(
	            new Category(otherUser, "Private Category", "BOTH", false));
	
	    assertThrows(
	            ResponseStatusException.class,
	            () -> categoryService.updateCategory(
	                    user.getId(), privateCategory.getId(),
	                    "Renamed Category", "BOTH"));
	
	    assertThrows(
	            ResponseStatusException.class,
	            () -> categoryService.deleteCategory(
	                    user.getId(), privateCategory.getId()));
	
	    Category unchanged = categoryRepository.findById(privateCategory.getId())
	            .orElseThrow();
	
	    assertThat(unchanged.getName()).isEqualTo("Private Category");
	    assertThat(unchanged.getUser().getId()).isEqualTo(otherUser.getId());
	}

	@Test
	void analyticsSummaryCalculatesIncomeExpensesAndCategoryTotals() {
	    Account account = createAccount(new BigDecimal("1000.00"));

	    LocalDate from = LocalDate.of(2026, 10, 1);
	    LocalDate to = LocalDate.of(2026, 10, 31);

	    CreateTransactionRequest income =
	            incomeRequest(account.getId(), "500.00",
	                    LocalDate.of(2026, 10, 5));

	    transactionService.createTransaction(
	            user.getId(), income, "analytics-income-001");

	    CreateTransactionRequest expense1 =
	            expenseRequest(account.getId(), "100.00");
	    expense1.setTransactionDate(LocalDate.of(2026, 10, 10));

	    transactionService.createTransaction(
	            user.getId(), expense1, "analytics-expense-001");

	    CreateTransactionRequest expense2 =
	            expenseRequest(account.getId(), "50.00");
	    expense2.setTransactionDate(LocalDate.of(2026, 10, 15));

	    transactionService.createTransaction(
	            user.getId(), expense2, "analytics-expense-002");

	    AnalyticsSummaryResponse summary =
	            analyticsService.getSummary(user.getId(), from, to);

	    assertThat(summary.from()).isEqualTo(from);
	    assertThat(summary.to()).isEqualTo(to);
	    assertThat(summary.currencies()).hasSize(1);

	    CurrencyAnalyticsResponse inr = summary.currencies().get(0);

	    assertThat(inr.currency()).isEqualTo("INR");
	    assertThat(inr.totalIncome()).isEqualByComparingTo("500.00");
	    assertThat(inr.totalExpenses()).isEqualByComparingTo("150.00");
	    assertThat(inr.netCashFlow()).isEqualByComparingTo("350.00");
	    assertThat(inr.incomeTransactionCount()).isEqualTo(1);
	    assertThat(inr.expenseTransactionCount()).isEqualTo(2);

	    assertThat(inr.expensesByCategory()).hasSize(1);

	    CategoryExpenseResponse food = inr.expensesByCategory().get(0);

	    assertThat(food.categoryName()).isEqualTo("Food");
	    assertThat(food.total()).isEqualByComparingTo("150.00");
	}

	@Test
	void analyticsExcludesReversedExpenseFromTotalsAndCategoryBreakdown() {
	    Account account = createAccount(new BigDecimal("1000.00"));

	    LocalDate from = LocalDate.of(2026, 10, 1);
	    LocalDate to = LocalDate.of(2026, 10, 31);

	    CreateTransactionRequest expenseRequest =
	            expenseRequest(account.getId(), "100.00");

	    expenseRequest.setTransactionDate(LocalDate.of(2026, 10, 10));

	    FinancialTransaction expense =
	            transactionService.createTransaction(
	                    user.getId(),
	                    expenseRequest,
	                    "analytics-reversed-expense-001");

	    // Reverse the original expense using the existing reversal logic.
	    transactionService.reverseTransaction(
	            user.getId(),
	            expense.getId());

	    AnalyticsSummaryResponse summary =
	            analyticsService.getSummary(user.getId(), from, to);

	    assertThat(summary.currencies()).hasSize(1);

	    CurrencyAnalyticsResponse inr = summary.currencies().get(0);

	    assertThat(inr.currency()).isEqualTo("INR");
	    assertThat(inr.totalIncome()).isEqualByComparingTo("0.00");
	    assertThat(inr.totalExpenses()).isEqualByComparingTo("0.00");
	    assertThat(inr.netCashFlow()).isEqualByComparingTo("0.00");
	    assertThat(inr.incomeTransactionCount()).isZero();
	    assertThat(inr.expenseTransactionCount()).isZero();
	    assertThat(inr.expensesByCategory()).isEmpty();
	}

	@Test
	void analyticsIncludesOnlyTransactionsWithinRequestedDateRange() {
	    Account account = createAccount(new BigDecimal("1000.00"));

	    CreateTransactionRequest insideRange =
	            expenseRequest(account.getId(), "40.00");
	    insideRange.setTransactionDate(LocalDate.of(2026, 10, 10));

	    transactionService.createTransaction(
	            user.getId(),
	            insideRange,
	            "analytics-date-inside-001");

	    CreateTransactionRequest outsideRange =
	            expenseRequest(account.getId(), "60.00");
	    outsideRange.setTransactionDate(LocalDate.of(2026, 9, 30));

	    transactionService.createTransaction(
	            user.getId(),
	            outsideRange,
	            "analytics-date-outside-001");

	    AnalyticsSummaryResponse summary =
	            analyticsService.getSummary(
	                    user.getId(),
	                    LocalDate.of(2026, 10, 1),
	                    LocalDate.of(2026, 10, 31));

	    assertThat(summary.currencies()).hasSize(1);

	    CurrencyAnalyticsResponse inr = summary.currencies().get(0);

	    assertThat(inr.totalExpenses()).isEqualByComparingTo("40.00");
	    assertThat(inr.expenseTransactionCount()).isEqualTo(1);
	    assertThat(inr.expensesByCategory()).hasSize(1);
	    assertThat(inr.expensesByCategory().get(0).total())
	            .isEqualByComparingTo("40.00");
	}

	@Test
	void analyticsRejectsStartDateAfterEndDate() {
	    assertThrows(
	            ResponseStatusException.class,
	            () -> analyticsService.getSummary(
	                    user.getId(),
	                    LocalDate.of(2026, 10, 31),
	                    LocalDate.of(2026, 10, 1)));
	}

	@Test
	void analyticsKeepsDifferentCurrenciesSeparate() {
	    LocalDate from = LocalDate.of(2026, 10, 1);
	    LocalDate to = LocalDate.of(2026, 10, 31);

	    Account inrAccount = createAccount(new BigDecimal("1000.00"));

	    Account usdAccount = accountRepository.saveAndFlush(
	            new Account(
	                    user,
	                    "USD Savings " + UUID.randomUUID(),
	                    "SAVINGS",
	                    "USD",
	                    new BigDecimal("1000.00")));

	    CreateTransactionRequest inrExpense =
	            expenseRequest(inrAccount.getId(), "100.00");
	    inrExpense.setTransactionDate(LocalDate.of(2026, 10, 10));

	    transactionService.createTransaction(
	            user.getId(), inrExpense, "analytics-currency-inr-001");

	    CreateTransactionRequest usdExpense =
	            expenseRequest(usdAccount.getId(), "25.00");
	    usdExpense.setCurrency("USD");
	    usdExpense.setTransactionDate(LocalDate.of(2026, 10, 12));

	    transactionService.createTransaction(
	            user.getId(), usdExpense, "analytics-currency-usd-001");

	    AnalyticsSummaryResponse summary =
	            analyticsService.getSummary(user.getId(), from, to);

	    assertThat(summary.currencies()).hasSize(2);

	    CurrencyAnalyticsResponse inr = summary.currencies().stream()
	            .filter(row -> row.currency().equals("INR"))
	            .findFirst()
	            .orElseThrow();

	    CurrencyAnalyticsResponse usd = summary.currencies().stream()
	            .filter(row -> row.currency().equals("USD"))
	            .findFirst()
	            .orElseThrow();

	    assertThat(inr.totalExpenses()).isEqualByComparingTo("100.00");
	    assertThat(usd.totalExpenses()).isEqualByComparingTo("25.00");

	    assertThat(inr.expenseTransactionCount()).isEqualTo(1);
	    assertThat(usd.expenseTransactionCount()).isEqualTo(1);
	}
	
	@Test
	void analyticsExcludesTransfersFromIncomeAndExpenseTotals() {
	    Account source = createAccount(new BigDecimal("1000.00"));
	    Account destination = createAccount(new BigDecimal("200.00"));

	    LocalDate transactionDate = LocalDate.of(2026, 10, 15);

	    CreateTransactionRequest transfer = new CreateTransactionRequest();
	    transfer.setTransactionType("TRANSFER");
	    transfer.setDescription("Analytics transfer test");
	    transfer.setCategory("Food");
	    transfer.setAmount(new BigDecimal("100.00"));
	    transfer.setCurrency("INR");
	    transfer.setTransactionDate(transactionDate);
	    transfer.setSourceAccountId(source.getId());
	    transfer.setDestinationAccountId(destination.getId());

	    transactionService.createTransaction(
	            user.getId(), transfer, "analytics-transfer-001");

	    AnalyticsSummaryResponse summary =
	            analyticsService.getSummary(
	                    user.getId(),
	                    LocalDate.of(2026, 10, 1),
	                    LocalDate.of(2026, 10, 31));

	    // Transfers alone should not create an income/expense currency summary.
	    assertThat(summary.currencies()).isEmpty();
	}
}
