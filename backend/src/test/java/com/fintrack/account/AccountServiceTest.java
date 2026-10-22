package com.fintrack.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.fintrack.transaction.LedgerEntryRepository;
import org.mockito.Mock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
    	accountService = new AccountService(
    	        accountRepository,
    	        userRepository,
    	        ledgerEntryRepository);
    }

    @Test
    void createAccountShouldSaveValidAccount() {
        UUID userId = UUID.randomUUID();

        User user = new User(
                "Alan Cardoza",
                "alan@example.com",
                "test-hash");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account savedAccount = accountService.createAccount(
                userId,
                "  Main Savings  ",
                "savings",
                "inr",
                new BigDecimal("1500.00"));

        assertEquals("Main Savings", savedAccount.getName());
        assertEquals("SAVINGS", savedAccount.getAccountType());
        assertEquals("INR", savedAccount.getCurrency());
        assertEquals(
                0,
                new BigDecimal("1500.00")
                        .compareTo(savedAccount.getOpeningBalance()));
        assertSame(user, savedAccount.getUser());

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccountShouldRejectInvalidAccountType() {
        UUID userId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(
                        userId,
                        "My Account",
                        "INVALID_TYPE",
                        "INR",
                        BigDecimal.ZERO));

        verify(userRepository, never()).findById(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createAccountShouldRejectNegativeOpeningBalance() {
        UUID userId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(
                        userId,
                        "My Account",
                        "SAVINGS",
                        "INR",
                        new BigDecimal("-100.00")));

        verify(userRepository, never()).findById(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void getAccountsForUserShouldReturnUsersAccounts() {
        UUID userId = UUID.randomUUID();
        List<Account> expectedAccounts = List.of();

        when(userRepository.existsById(userId)).thenReturn(true);
        when(accountRepository.findAllByUser_Id(userId))
                .thenReturn(expectedAccounts);

        List<Account> result = accountService.getAccountsForUser(userId);

        assertEquals(expectedAccounts, result);
        verify(accountRepository).findAllByUser_Id(userId);
    }

    @Test
    void getAccountForUserShouldRejectAccountNotOwnedByUser() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(accountRepository.findByIdAndUser_Id(accountId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountForUser(userId, accountId));

        verify(accountRepository)
                .findByIdAndUser_Id(accountId, userId);
    }
    
	@Test
	void getCurrentBalanceWithNoLedgerEntriesShouldReturnOpeningBalance() {
	    UUID userId = UUID.randomUUID();
	    UUID accountId = UUID.randomUUID();
	
	    Account account = mock(Account.class);
	
	    when(account.getId()).thenReturn(accountId);
	    when(account.getOpeningBalance()).thenReturn(new BigDecimal("1000.00"));
	
	    when(accountRepository.findByIdAndUser_Id(accountId, userId))
	            .thenReturn(Optional.of(account));
	
	    when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	            accountId, "DEBIT"))
	            .thenReturn(BigDecimal.ZERO);
	
	    when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	            accountId, "CREDIT"))
	            .thenReturn(BigDecimal.ZERO);
	
	    BigDecimal balance =
	            accountService.getCurrentBalance(userId, accountId);
	
	    assertEquals(
	            0,
	            new BigDecimal("1000.00").compareTo(balance));
	}
	
	@Test
	void getCurrentBalanceShouldAddDebitsToOpeningBalance() {
	    UUID userId = UUID.randomUUID();
	    UUID accountId = UUID.randomUUID();
	
	    Account account = mock(Account.class);
	
	    when(account.getId()).thenReturn(accountId);
	    when(account.getOpeningBalance()).thenReturn(new BigDecimal("1000.00"));
	
	    when(accountRepository.findByIdAndUser_Id(accountId, userId))
	            .thenReturn(Optional.of(account));
	
	    when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	            accountId, "DEBIT"))
	            .thenReturn(new BigDecimal("150.00"));
	
	    when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	            accountId, "CREDIT"))
	            .thenReturn(new BigDecimal("25.00"));
	
	    BigDecimal balance =
	            accountService.getCurrentBalance(userId, accountId);
	
	    assertEquals(
	            0,
	            new BigDecimal("1125.00").compareTo(balance));
	
	    verify(ledgerEntryRepository)
	            .sumAmountByAccountAndEntrySide(accountId, "DEBIT");
	
	    verify(ledgerEntryRepository)
	            .sumAmountByAccountAndEntrySide(accountId, "CREDIT");
	}
	
	@Test
	void getCurrentBalanceShouldSubtractCreditsFromOpeningBalance() {
	    UUID userId = UUID.randomUUID();
	    UUID accountId = UUID.randomUUID();
	
	    Account account = mock(Account.class);
	
	    when(account.getId()).thenReturn(accountId);
	    when(account.getOpeningBalance()).thenReturn(new BigDecimal("1000.00"));
	
	    when(accountRepository.findByIdAndUser_Id(accountId, userId))
	            .thenReturn(Optional.of(account));
	
	    when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	            accountId, "DEBIT"))
	            .thenReturn(BigDecimal.ZERO);
	
	    when(ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	            accountId, "CREDIT"))
	            .thenReturn(new BigDecimal("75.00"));
	
	    BigDecimal balance =
	            accountService.getCurrentBalance(userId, accountId);
	
	    assertEquals(
	            0,
	            new BigDecimal("925.00").compareTo(balance));
	}
	
	@Test
	void getCurrentBalanceShouldRejectAccountNotOwnedByUser() {
	    UUID userId = UUID.randomUUID();
	    UUID accountId = UUID.randomUUID();
	
	    when(accountRepository.findByIdAndUser_Id(accountId, userId))
	            .thenReturn(Optional.empty());
	
	    assertThrows(
	            IllegalArgumentException.class,
	            () -> accountService.getCurrentBalance(userId, accountId));
	
	    verifyNoInteractions(ledgerEntryRepository);
	}
    	
}
