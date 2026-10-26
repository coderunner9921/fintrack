package com.fintrack.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.transaction.FinancialTransaction;
import com.fintrack.transaction.FinancialTransactionRepository;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

class ReconciliationServiceTest {

    private UserRepository userRepository;
    private AccountRepository accountRepository;
    private FinancialTransactionRepository transactionRepository;
    private ReconciliationImportRepository importRepository;
    private StatementRowRepository statementRowRepository;

    private ReconciliationService service;

    private UUID userId;
    private UUID accountId;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
        transactionRepository =
                org.mockito.Mockito.mock(FinancialTransactionRepository.class);
        importRepository =
                org.mockito.Mockito.mock(ReconciliationImportRepository.class);
        statementRowRepository =
                org.mockito.Mockito.mock(StatementRowRepository.class);

        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        user = org.mockito.Mockito.mock(User.class);
        account = org.mockito.Mockito.mock(Account.class);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.of(account));

        when(account.getId()).thenReturn(accountId);
        when(account.getCurrency()).thenReturn("INR");

        when(transactionRepository
                .findAllByUser_IdOrderByTransactionDateDesc(userId))
                .thenReturn(List.of());

        when(importRepository.save(any(ReconciliationImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(statementRowRepository.save(any(StatementRow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = new ReconciliationService(
                userRepository,
                accountRepository,
                transactionRepository,
                importRepository,
                statementRowRepository,
                new StatementCsvParser(),
                new StatementTransactionMatcher());
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void duplicateStatementRowsAreFlaggedAndCounted() throws Exception {
        String content = """
                date,description,amount,currency,reference
                2026-10-20,Grocery Store,-450.00,INR,UPI123
                2026-10-20,Grocery Store,-450.00,INR,UPI123
                """;

        var response = service.importStatement(
                userId, accountId, "statement.csv", csv(content));

        assertEquals(2, response.totalRows());
        assertEquals(0, response.matchedRows());
        assertEquals(1, response.unmatchedRows());
        assertEquals(1, response.duplicateRows());
        assertEquals(0, response.mismatchedRows());

        ArgumentCaptor<StatementRow> captor =
                ArgumentCaptor.forClass(StatementRow.class);

        verify(statementRowRepository, org.mockito.Mockito.times(2))
                .save(captor.capture());

        List<StatementRow> savedRows = captor.getAllValues();

        assertEquals(
                StatementRow.RowStatus.UNMATCHED,
                savedRows.get(0).getStatus());

        assertEquals(
                StatementRow.RowStatus.DUPLICATE,
                savedRows.get(1).getStatus());

        org.junit.jupiter.api.Assertions.assertSame(
                savedRows.get(0),
                savedRows.get(1).getDuplicateOfRow());
    }

    @Test
    void invalidCsvIsRejectedBeforeImportIsSaved() {
        String content = """
                date,description,amount
                2026-10-20,Groceries,-100
                """;

        assertThrows(
                CsvImportValidationException.class,
                () -> service.importStatement(
                        userId, accountId, "statement.csv", csv(content)));

        verify(importRepository, never())
                .save(any(ReconciliationImport.class));

        verify(statementRowRepository, never())
                .save(any(StatementRow.class));
    }

    @Test
    void rejectsAccountNotOwnedByUser() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> service.importStatement(
                        userId,
                        accountId,
                        "statement.csv",
                        csv("""
                                date,description,amount,currency
                                2026-10-20,Groceries,-100,INR
                                """)));

        verify(importRepository, never())
                .save(any(ReconciliationImport.class));
    }

    @Test
    void rejectsNonCsvFilenameBeforePersistence() {
        assertThrows(
                CsvImportValidationException.class,
                () -> service.importStatement(
                        userId,
                        accountId,
                        "statement.txt",
                        csv("""
                                date,description,amount,currency
                                2026-10-20,Groceries,-100,INR
                                """)));

        verify(importRepository, never())
                .save(any(ReconciliationImport.class));
    }

    @Test
    void currencyDifferentFromSelectedAccountIsMismatched()
            throws Exception {

        var response = service.importStatement(
                userId,
                accountId,
                "statement.csv",
                csv("""
                        date,description,amount,currency
                        2026-10-20,Groceries,-100,USD
                        """));

        assertEquals(1, response.totalRows());
        assertEquals(0, response.matchedRows());
        assertEquals(0, response.unmatchedRows());
        assertEquals(1, response.mismatchedRows());
    }
}
