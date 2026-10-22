package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.UUID;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Testcontainers
@Transactional
class LedgerEntryRepositoryIntegrationTest {

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
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    private void insertRequiredRecords() {
        jdbcTemplate.update("""
            INSERT INTO users
                (id, full_name, email, password_hash)
            VALUES (?, ?, ?, ?)
            """,
            userId, "Integration Test",
            "ledger-test-" + userId + "@example.com",
            "test-hash");

        jdbcTemplate.update("""
            INSERT INTO accounts
                (id, user_id, name, account_type, currency, opening_balance)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            accountId, userId, "Test Savings", "SAVINGS", "INR",
            new BigDecimal("100.0000"));

        jdbcTemplate.update("""
            INSERT INTO transactions
                (id, user_id, transaction_type, description, category,
                 amount, currency, transaction_date, source_account_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?)
            """,
            transactionId, userId, "EXPENSE", "Repository test",
            "Testing", new BigDecimal("25.0000"), "INR", accountId);
    }

    private void insertLedgerEntry(
            UUID entryId, String entrySide, String amount) {
        jdbcTemplate.update("""
            INSERT INTO ledger_entries
                (id, transaction_id, account_id, entry_side, amount)
            VALUES (?, ?, ?, ?, ?)
            """,
            entryId, transactionId, accountId, entrySide,
            new BigDecimal(amount));
    }

    @Test
    void shouldSumDebitEntries() {
        insertRequiredRecords();

        insertLedgerEntry(
                UUID.fromString("44444444-4444-4444-4444-444444444441"),
                "DEBIT", "10.0000");

        insertLedgerEntry(
                UUID.fromString("44444444-4444-4444-4444-444444444442"),
                "DEBIT", "15.0000");

        BigDecimal total =
                ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                        accountId, "DEBIT");

        assertThat(total).isEqualByComparingTo("25.0000");
    }

    @Test
    void shouldSumCreditEntries() {
        insertRequiredRecords();

        insertLedgerEntry(
                UUID.fromString("55555555-5555-5555-5555-555555555551"),
                "CREDIT", "4.0000");

        insertLedgerEntry(
                UUID.fromString("55555555-5555-5555-5555-555555555552"),
                "CREDIT", "6.5000");

        BigDecimal total =
                ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                        accountId, "CREDIT");

        assertThat(total).isEqualByComparingTo("10.5000");
    }

    @Test
    void shouldReturnZeroWhenAccountHasNoLedgerEntries() {
        insertRequiredRecords();

        BigDecimal debits =
                ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                        accountId, "DEBIT");

        BigDecimal credits =
                ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                        accountId, "CREDIT");

        assertThat(debits).isEqualByComparingTo("0");
        assertThat(credits).isEqualByComparingTo("0");
    }
    
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRejectUpdateAndPreserveOriginalLedgerEntry() {
        insertRequiredRecords();

        UUID entryId = UUID.randomUUID();

        insertLedgerEntry(entryId, "DEBIT", "25.0000");

        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                    UPDATE ledger_entries
                    SET amount = ?
                    WHERE id = ?
                    """,
                    new BigDecimal("99.0000"), entryId))
        	.hasMessageContaining(
                    "Ledger entries are immutable; create a compensating entry instead.");

        BigDecimal storedAmount = jdbcTemplate.queryForObject(
                "SELECT amount FROM ledger_entries WHERE id = ?",
                BigDecimal.class,
                entryId);

        assertThat(storedAmount).isEqualByComparingTo("25.0000");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRejectDeleteAndPreserveOriginalLedgerEntry() {
        insertRequiredRecords();

        UUID entryId = UUID.randomUUID();

        insertLedgerEntry(entryId, "CREDIT", "25.0000");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "DELETE FROM ledger_entries WHERE id = ?",
                        entryId))
	        .hasMessageContaining(
	        	    "Ledger entries are immutable; create a compensating entry instead.");

        Integer remainingEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE id = ?",
                Integer.class,
                entryId);

        assertThat(remainingEntries).isEqualTo(1);

        BigDecimal storedAmount = jdbcTemplate.queryForObject(
                "SELECT amount FROM ledger_entries WHERE id = ?",
                BigDecimal.class,
                entryId);

        assertThat(storedAmount).isEqualByComparingTo("25.0000");
    }
}
