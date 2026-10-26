package com.fintrack.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import com.fintrack.transaction.LedgerEntry;
import com.fintrack.transaction.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fintrack.account.Account;
import com.fintrack.reconciliation.dto.ParsedStatementRow;
import com.fintrack.transaction.FinancialTransaction;

class StatementTransactionMatcherTest {

    private StatementTransactionMatcher matcher;
    private UUID accountId;
    private UUID otherAccountId;
    private LedgerEntryRepository ledgerEntryRepository;
    private Account account;
    private Account otherAccount;
    private final LocalDate transactionDate =
            LocalDate.of(2026, 10, 20);

    @BeforeEach
    void setUp() {
    	ledgerEntryRepository = Mockito.mock(LedgerEntryRepository.class);
    	matcher = new StatementTransactionMatcher(ledgerEntryRepository);

        accountId = UUID.randomUUID();
        otherAccountId = UUID.randomUUID();

        account = mock(Account.class);
        otherAccount = mock(Account.class);

        when(account.getId()).thenReturn(accountId);
        when(otherAccount.getId()).thenReturn(otherAccountId);
    }

    private FinancialTransaction transaction(
            String type,
            BigDecimal amount,
            String currency,
            LocalDate date,
            Account source,
            Account destination) {

        FinancialTransaction transaction =
                mock(FinancialTransaction.class);

        when(transaction.getTransactionType()).thenReturn(type);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getCurrency()).thenReturn(currency);
        when(transaction.getTransactionDate()).thenReturn(date);
        when(transaction.getSourceAccount()).thenReturn(source);
        when(transaction.getDestinationAccount()).thenReturn(destination);

        return transaction;
    }

    private ParsedStatementRow statement(
            String amount,
            String currency,
            LocalDate date) {

        return new ParsedStatementRow(
                2,
                date,
                "Test transaction",
                new BigDecimal(amount),
                currency,
                null);
    }
    
    private LedgerEntry ledgerEntry(
            Account entryAccount,
            String side,
            BigDecimal amount) {

        LedgerEntry entry = Mockito.mock(LedgerEntry.class);

        Mockito.doReturn(entryAccount).when(entry).getAccount();
        Mockito.doReturn(side).when(entry).getEntrySide();
        Mockito.doReturn(amount).when(entry).getAmount();

        return entry;
    }
	
	@Test
	void matchesReversedExpenseAsStatementCredit() {
	    UUID originalId = UUID.randomUUID();
	    UUID reversalId = UUID.randomUUID();
	
	    BigDecimal amount = new BigDecimal("450.00");
	
	    FinancialTransaction original = transaction(
	            "EXPENSE", amount, "INR", transactionDate, account, null);
	
	    when(original.getId()).thenReturn(originalId);
	
	    FinancialTransaction reversal = transaction(
	            "REVERSAL", amount, "INR", transactionDate, account, null);
	
	    when(reversal.getId()).thenReturn(reversalId);
	    when(reversal.getReversalOfTransaction()).thenReturn(original);
	
	    LedgerEntry originalEntry = ledgerEntry(account, "CREDIT", amount);
	    LedgerEntry reversalEntry = ledgerEntry(account, "DEBIT", amount);

	    when(ledgerEntryRepository.findAllByTransaction_Id(originalId))
	            .thenReturn(List.of(originalEntry));

	    when(ledgerEntryRepository.findAllByTransaction_Id(reversalId))
	            .thenReturn(List.of(reversalEntry));
	    var result = matcher.match(
	            statement("450.00", "INR", transactionDate),
	            accountId,
	            List.of(reversal));
	
	    assertEquals(StatementRow.RowStatus.MATCHED, result.status());
	    assertSame(reversal, result.transaction());
	}
	
	@Test
	void matchesReversedIncomeAsStatementDebit() {
	    UUID originalId = UUID.randomUUID();
	    UUID reversalId = UUID.randomUUID();
	
	    BigDecimal amount = new BigDecimal("25000.00");
	
	    FinancialTransaction original = transaction(
	            "INCOME", amount, "INR", transactionDate, null, account);
	
	    when(original.getId()).thenReturn(originalId);
	
	    FinancialTransaction reversal = transaction(
	            "REVERSAL", amount, "INR", transactionDate, null, account);
	
	    when(reversal.getId()).thenReturn(reversalId);
	    when(reversal.getReversalOfTransaction()).thenReturn(original);
	
	    LedgerEntry originalEntry = ledgerEntry(account, "DEBIT", amount);
	    LedgerEntry reversalEntry = ledgerEntry(account, "CREDIT", amount);

	    when(ledgerEntryRepository.findAllByTransaction_Id(originalId))
	            .thenReturn(List.of(originalEntry));

	    when(ledgerEntryRepository.findAllByTransaction_Id(reversalId))
	            .thenReturn(List.of(reversalEntry));
	
	    var result = matcher.match(
	            statement("-25000.00", "INR", transactionDate),
	            accountId,
	            List.of(reversal));
	
	    assertEquals(StatementRow.RowStatus.MATCHED, result.status());
	    assertSame(reversal, result.transaction());
	}
	
	@Test
	void flagsReversalWithInconsistentLedgerSidesAsMismatched() {
	    UUID originalId = UUID.randomUUID();
	    UUID reversalId = UUID.randomUUID();
	
	    BigDecimal amount = new BigDecimal("450.00");
	
	    FinancialTransaction original = transaction(
	            "EXPENSE", amount, "INR", transactionDate, account, null);
	
	    when(original.getId()).thenReturn(originalId);
	
	    FinancialTransaction reversal = transaction(
	            "REVERSAL", amount, "INR", transactionDate, account, null);
	
	    when(reversal.getId()).thenReturn(reversalId);
	    when(reversal.getReversalOfTransaction()).thenReturn(original);
	
	    // Both entries have the same side, which violates reversal rules.
	    LedgerEntry originalEntry = ledgerEntry(account, "CREDIT", amount);
	    LedgerEntry reversalEntry = ledgerEntry(account, "CREDIT", amount);

	    when(ledgerEntryRepository.findAllByTransaction_Id(originalId))
	            .thenReturn(List.of(originalEntry));

	    when(ledgerEntryRepository.findAllByTransaction_Id(reversalId))
	            .thenReturn(List.of(reversalEntry));
	
	    var result = matcher.match(
	            statement("450.00", "INR", transactionDate),
	            accountId,
	            List.of(reversal));
	
	    assertEquals(StatementRow.RowStatus.MISMATCHED, result.status());
	    assertNull(result.transaction());
	}


    @Test
    void matchesExpenseDebit() {
        FinancialTransaction expense = transaction(
                "EXPENSE",
                new BigDecimal("450.00"),
                "INR",
                transactionDate,
                account,
                null);

        var result = matcher.match(
                statement("-450.00", "INR", transactionDate),
                accountId,
                List.of(expense));

        assertEquals(StatementRow.RowStatus.MATCHED, result.status());
        assertSame(expense, result.transaction());
    }

    @Test
    void matchesIncomeCredit() {
        FinancialTransaction income = transaction(
                "INCOME",
                new BigDecimal("25000.00"),
                "INR",
                transactionDate,
                null,
                account);

        var result = matcher.match(
                statement("25000.00", "INR", transactionDate),
                accountId,
                List.of(income));

        assertEquals(StatementRow.RowStatus.MATCHED, result.status());
        assertSame(income, result.transaction());
    }

    @Test
    void matchesTransferDebitForSourceAccount() {
        FinancialTransaction transfer = transaction(
                "TRANSFER",
                new BigDecimal("1000.00"),
                "INR",
                transactionDate,
                account,
                otherAccount);

        var result = matcher.match(
                statement("-1000.00", "INR", transactionDate),
                accountId,
                List.of(transfer));

        assertEquals(StatementRow.RowStatus.MATCHED, result.status());
        assertSame(transfer, result.transaction());
    }

    @Test
    void matchesTransferCreditForDestinationAccount() {
        FinancialTransaction transfer = transaction(
                "TRANSFER",
                new BigDecimal("1000.00"),
                "INR",
                transactionDate,
                otherAccount,
                account);

        var result = matcher.match(
                statement("1000.00", "INR", transactionDate),
                accountId,
                List.of(transfer));

        assertEquals(StatementRow.RowStatus.MATCHED, result.status());
        assertSame(transfer, result.transaction());
    }

    @Test
    void flagsAmountMismatch() {
        FinancialTransaction expense = transaction(
                "EXPENSE",
                new BigDecimal("450.00"),
                "INR",
                transactionDate,
                account,
                null);

        var result = matcher.match(
                statement("-500.00", "INR", transactionDate),
                accountId,
                List.of(expense));

        assertEquals(StatementRow.RowStatus.MISMATCHED, result.status());
        assertNull(result.transaction());
    }

    @Test
    void ignoresTransactionsBelongingToAnotherAccount() {
        FinancialTransaction expense = transaction(
                "EXPENSE",
                new BigDecimal("450.00"),
                "INR",
                transactionDate,
                otherAccount,
                null);

        var result = matcher.match(
                statement("-450.00", "INR", transactionDate),
                accountId,
                List.of(expense));

        assertEquals(StatementRow.RowStatus.UNMATCHED, result.status());
        assertNull(result.transaction());
    }

    @Test
    void flagsAmbiguousExactMatches() {
        FinancialTransaction first = transaction(
                "EXPENSE",
                new BigDecimal("450.00"),
                "INR",
                transactionDate,
                account,
                null);

        FinancialTransaction second = transaction(
                "EXPENSE",
                new BigDecimal("450.00"),
                "INR",
                transactionDate,
                account,
                null);

        var result = matcher.match(
                statement("-450.00", "INR", transactionDate),
                accountId,
                List.of(first, second));

        assertEquals(StatementRow.RowStatus.MISMATCHED, result.status());
        assertNull(result.transaction());
    }

    @Test
    void returnsUnmatchedWhenNoTransactionExists() {
        var result = matcher.match(
                statement("-450.00", "INR", transactionDate),
                accountId,
                List.of());

        assertEquals(StatementRow.RowStatus.UNMATCHED, result.status());
        assertNull(result.transaction());
    }

	@Test
	void matchesReversedTransferAsCreditForOriginalSourceAccount() {
	    UUID originalId = UUID.randomUUID();
	    UUID reversalId = UUID.randomUUID();
	
	    BigDecimal amount = new BigDecimal("1000.00");
	
	    FinancialTransaction original = transaction(
	            "TRANSFER", amount, "INR", transactionDate,
	            account, otherAccount);
	
	    when(original.getId()).thenReturn(originalId);
	
	    FinancialTransaction reversal = transaction(
	            "REVERSAL", amount, "INR", transactionDate,
	            account, otherAccount);
	
	    when(reversal.getId()).thenReturn(reversalId);
	    when(reversal.getReversalOfTransaction()).thenReturn(original);
	
	    LedgerEntry originalEntry = ledgerEntry(account, "CREDIT", amount);
	    LedgerEntry reversalEntry = ledgerEntry(account, "DEBIT", amount);

	    when(ledgerEntryRepository.findAllByTransaction_Id(originalId))
	            .thenReturn(List.of(originalEntry));

	    when(ledgerEntryRepository.findAllByTransaction_Id(reversalId))
	            .thenReturn(List.of(reversalEntry));
	
	    var result = matcher.match(
	            statement("1000.00", "INR", transactionDate),
	            accountId,
	            List.of(reversal));
	
	    assertEquals(StatementRow.RowStatus.MATCHED, result.status());
	    assertSame(reversal, result.transaction());
	}
	
	@Test
	void matchesReversedTransferAsDebitForOriginalDestinationAccount() {
	    UUID originalId = UUID.randomUUID();
	    UUID reversalId = UUID.randomUUID();
	
	    BigDecimal amount = new BigDecimal("1000.00");
	
	    FinancialTransaction original = transaction(
	            "TRANSFER", amount, "INR", transactionDate,
	            otherAccount, account);
	
	    when(original.getId()).thenReturn(originalId);
	
	    FinancialTransaction reversal = transaction(
	            "REVERSAL", amount, "INR", transactionDate,
	            otherAccount, account);
	
	    when(reversal.getId()).thenReturn(reversalId);
	    when(reversal.getReversalOfTransaction()).thenReturn(original);
	
	    LedgerEntry originalEntry = ledgerEntry(account, "DEBIT", amount);
	    LedgerEntry reversalEntry = ledgerEntry(account, "CREDIT", amount);

	    when(ledgerEntryRepository.findAllByTransaction_Id(originalId))
	            .thenReturn(List.of(originalEntry));

	    when(ledgerEntryRepository.findAllByTransaction_Id(reversalId))
	            .thenReturn(List.of(reversalEntry));
	
	    var result = matcher.match(
	            statement("-1000.00", "INR", transactionDate),
	            accountId,
	            List.of(reversal));
	
	    assertEquals(StatementRow.RowStatus.MATCHED, result.status());
	    assertSame(reversal, result.transaction());
	}

}
