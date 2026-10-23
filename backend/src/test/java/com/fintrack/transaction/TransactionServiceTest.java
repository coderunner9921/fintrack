package com.fintrack.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

class TransactionServiceTest {

    private UserRepository userRepository;
    private AccountRepository accountRepository;
    private FinancialTransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private TransactionService transactionService;
    private CategoryRepository categoryRepository;

    private UUID userId;
    private UUID sourceId;
    private UUID destinationId;

    private User user;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(FinancialTransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        categoryRepository = mock(CategoryRepository.class);

        Category category = mock(Category.class);
        when(category.getCategoryType()).thenReturn("BOTH");
        when(categoryRepository.findByNameIgnoreCaseAndSystemTrue("General"))
                .thenReturn(Optional.of(category));

        transactionService = new TransactionService(
                userRepository,
                accountRepository,
                transactionRepository,
                ledgerEntryRepository,
                categoryRepository);

        userId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        destinationId = UUID.randomUUID();

        user = mock(User.class);
        source = mock(Account.class);
        destination = mock(Account.class);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(source.isActive()).thenReturn(true);
        when(source.getCurrency()).thenReturn("INR");

        when(destination.isActive()).thenReturn(true);
        when(destination.getCurrency()).thenReturn("INR");

        when(accountRepository.findByIdAndUser_Id(sourceId, userId))
                .thenReturn(Optional.of(source));

        when(accountRepository.findByIdAndUser_Id(destinationId, userId))
                .thenReturn(Optional.of(destination));

        when(transactionRepository.save(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        when(source.getId()).thenReturn(sourceId);
        when(source.getAccountType()).thenReturn("SAVINGS");
        when(source.getOpeningBalance())
                .thenReturn(new BigDecimal("1000.00"));

        when(destination.getId()).thenReturn(destinationId);
        when(destination.getAccountType()).thenReturn("SAVINGS");
        when(destination.getOpeningBalance())
                .thenReturn(new BigDecimal("1000.00"));

        when(accountRepository.findByIdAndUser_IdForUpdate(
                sourceId, userId))
                .thenReturn(Optional.of(source));

        when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                any(UUID.class), anyString()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void incomeCreatesBalancedLedgerEntries() {
        CreateTransactionRequest request =
                request("INCOME", null, destinationId, "250.00");

        FinancialTransaction result =
                transactionService.createTransaction(userId, request);

        assertEquals("INCOME", result.getTransactionType());
        verifyLedgerEntries("DEBIT", destination, "CREDIT", "INCOME");
    }

    @Test
    void expenseCreatesBalancedLedgerEntries() {
        CreateTransactionRequest request =
                request("EXPENSE", sourceId, null, "125.00");

        FinancialTransaction result =
                transactionService.createTransaction(userId, request);

        assertEquals("EXPENSE", result.getTransactionType());
        verifyLedgerEntries("DEBIT", null, "CREDIT", null);
    }

    @Test
    void transferCreatesBalancedLedgerEntries() {
        CreateTransactionRequest request =
                request("TRANSFER", sourceId, destinationId, "75.00");

        FinancialTransaction result =
                transactionService.createTransaction(userId, request);

        assertEquals("TRANSFER", result.getTransactionType());
        verifyLedgerEntries("DEBIT", destination, "CREDIT", null);
    }

    @Test
    void rejectsAccountNotOwnedByUser() {
        when(accountRepository.findByIdAndUser_IdForUpdate(sourceId, userId))
                .thenReturn(Optional.empty());

        CreateTransactionRequest request =
                request("EXPENSE", sourceId, null, "50.00");

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createTransaction(userId, request));

        verify(transactionRepository, never())
                .save(any(FinancialTransaction.class));
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void rejectsTransferBetweenDifferentCurrencies() {
        when(destination.getCurrency()).thenReturn("USD");

        CreateTransactionRequest request =
                request("TRANSFER", sourceId, destinationId, "50.00");

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createTransaction(userId, request));

        verify(transactionRepository, never())
                .save(any(FinancialTransaction.class));
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void rejectsZeroAmount() {
        CreateTransactionRequest request =
                request("EXPENSE", sourceId, null, "0");

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createTransaction(userId, request));

        verify(transactionRepository, never())
                .save(any(FinancialTransaction.class));
    }


	@Test
	void incomeCategoryAllowsIncomeTransaction() {
	    stubSystemCategoryType("INCOME");
	
	    CreateTransactionRequest request =
	            request("INCOME", null, destinationId, "250.00");
	
	    FinancialTransaction result =
	            transactionService.createTransaction(userId, request);
	
	    assertEquals("INCOME", result.getTransactionType());
	    verify(transactionRepository).save(any(FinancialTransaction.class));
	    verifyLedgerEntries("DEBIT", destination, "CREDIT", "INCOME");
	}
	
	@Test
	void incomeCategoryRejectsExpenseTransaction() {
	    stubSystemCategoryType("INCOME");
	
	    CreateTransactionRequest request =
	            request("EXPENSE", sourceId, null, "50.00");
	
	    assertThrows(IllegalArgumentException.class,
	            () -> transactionService.createTransaction(userId, request));
	
	    verify(transactionRepository, never())
	            .save(any(FinancialTransaction.class));
	    verify(ledgerEntryRepository, never()).saveAll(any());
	}
	
	@Test
	void expenseCategoryAllowsExpenseTransaction() {
	    stubSystemCategoryType("EXPENSE");
	
	    CreateTransactionRequest request =
	            request("EXPENSE", sourceId, null, "50.00");
	
	    FinancialTransaction result =
	            transactionService.createTransaction(userId, request);
	
	    assertEquals("EXPENSE", result.getTransactionType());
	    verify(transactionRepository).save(any(FinancialTransaction.class));
	    verifyLedgerEntries("DEBIT", null, "CREDIT", null);
	}
	
	@Test
	void expenseCategoryRejectsIncomeTransaction() {
	    stubSystemCategoryType("EXPENSE");
	
	    CreateTransactionRequest request =
	            request("INCOME", null, destinationId, "50.00");
	
	    assertThrows(IllegalArgumentException.class,
	            () -> transactionService.createTransaction(userId, request));
	
	    verify(transactionRepository, never())
	            .save(any(FinancialTransaction.class));
	    verify(ledgerEntryRepository, never()).saveAll(any());
	}
	
	private void stubSystemCategoryType(String categoryType) {
	    Category category = mock(Category.class);
	
	    when(category.getCategoryType()).thenReturn(categoryType);
	
	    when(categoryRepository.findByNameIgnoreCaseAndSystemTrue("General"))
	            .thenReturn(Optional.of(category));
	}

    private CreateTransactionRequest request(
            String type, UUID sourceAccountId,
            UUID destinationAccountId, String amount) {

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTransactionType(type);
        request.setDescription("Test transaction");
        request.setCategory("General");
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("INR");
        request.setTransactionDate(LocalDate.now());
        request.setSourceAccountId(sourceAccountId);
        request.setDestinationAccountId(destinationAccountId);

        return request;
    }

    private void verifyLedgerEntries(
            String expectedDebitSide,
            Account expectedDebitAccount,
            String expectedCreditSide,
            String expectedSystemCreditCode) {

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<LedgerEntry>> captor =
                (ArgumentCaptor<Iterable<LedgerEntry>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(Iterable.class);

        verify(ledgerEntryRepository).saveAll(captor.capture());

        List<LedgerEntry> entries = StreamSupport.stream(
                captor.getValue().spliterator(), false).toList();

        assertEquals(2, entries.size());

        LedgerEntry debit = entries.stream()
                .filter(entry -> "DEBIT".equals(entry.getEntrySide()))
                .findFirst().orElseThrow();

        LedgerEntry credit = entries.stream()
                .filter(entry -> "CREDIT".equals(entry.getEntrySide()))
                .findFirst().orElseThrow();

        assertEquals(expectedDebitSide, debit.getEntrySide());
        assertEquals(expectedCreditSide, credit.getEntrySide());

        assertEquals(0, debit.getAmount().compareTo(credit.getAmount()));

        if (expectedDebitAccount != null) {
            assertEquals(expectedDebitAccount, debit.getAccount());
        }

        if (expectedSystemCreditCode != null) {
            assertEquals(expectedSystemCreditCode, credit.getSystemAccountCode());
        }
    }
    
    @Test
    void rejectsExpenseWhenFundsAreInsufficient() {
        when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                sourceId, "DEBIT"))
                .thenReturn(new BigDecimal("100.00"));

        when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
                sourceId, "CREDIT"))
                .thenReturn(new BigDecimal("950.00"));

        CreateTransactionRequest request =
                request("EXPENSE", sourceId, null, "200.00");

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createTransaction(userId, request));

        verify(transactionRepository, never())
                .save(any(FinancialTransaction.class));

        verify(ledgerEntryRepository, never()).saveAll(any());
    }
}
