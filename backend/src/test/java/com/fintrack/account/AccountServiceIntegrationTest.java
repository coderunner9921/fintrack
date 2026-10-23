package com.fintrack.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import com.fintrack.transaction.LedgerEntryRepository;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class AccountServiceIntegrationTest {

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
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Account account;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                new User(
                        "Balance Integration Test",
                        "balance-test-" + UUID.randomUUID()
                                + "@example.com",
                        "test-password-hash"));

        account = accountRepository.saveAndFlush(
                new Account(
                        user,
                        "Test Savings",
                        "SAVINGS",
                        "INR",
                        new BigDecimal("1000.0000")));

        transactionId = UUID.randomUUID();

        jdbcTemplate.update("""
        	    INSERT INTO transactions
        	        (id, user_id, transaction_type, description, category,
        	         category_id, amount, currency, transaction_date, source_account_id)
        	    VALUES (?, ?, 'EXPENSE', 'Balance test', 'Food',
        	            (SELECT id FROM categories
        	             WHERE is_system = TRUE AND name = 'Food'),
        	            1.0000, 'INR', CURRENT_DATE, ?)
        	    """,
        	    transactionId, user.getId(), account.getId());
    }

    private void insertEntry(String side, String amount) {
        jdbcTemplate.update("""
            INSERT INTO ledger_entries
                (id, transaction_id, account_id, entry_side, amount)
            VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            transactionId,
            account.getId(),
            side,
            new BigDecimal(amount));
    }

    @Test
    void shouldReturnOpeningBalanceWhenThereAreNoEntries() {
        BigDecimal balance = accountService.getCurrentBalance(
                user.getId(), account.getId());

        assertThat(balance).isEqualByComparingTo("1000.0000");
    }

    @Test
    void shouldCalculateBalanceUsingRealLedgerEntries() {
        insertEntry("DEBIT", "150.0000");
        insertEntry("DEBIT", "50.0000");
        insertEntry("CREDIT", "25.0000");

        BigDecimal balance = accountService.getCurrentBalance(
                user.getId(), account.getId());

        assertThat(balance).isEqualByComparingTo("1175.0000");
    }

    @Test
    void shouldRejectAnotherUsersAccount() {
        User anotherUser = userRepository.saveAndFlush(
                new User(
                        "Different User",
                        "different-" + UUID.randomUUID()
                                + "@example.com",
                        "test-password-hash"));

        assertThatThrownBy(() ->
                accountService.getCurrentBalance(
                        anotherUser.getId(), account.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
