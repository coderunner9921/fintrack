package com.fintrack.account;

import java.math.BigDecimal;
import com.fintrack.transaction.LedgerEntryRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Account createAccount(
            UUID userId,
            String name,
            String accountType,
            String currency,
            BigDecimal openingBalance) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name is required.");
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException(
                    "Account name cannot exceed 100 characters.");
        }

        if (accountType == null || accountType.isBlank()) {
            throw new IllegalArgumentException("Account type is required.");
        }

        String normalizedType = accountType.trim()
                .toUpperCase(Locale.ROOT);

        List<String> allowedTypes = List.of(
                "CASH",
                "SAVINGS",
                "CURRENT",
                "CREDIT_CARD",
                "INVESTMENT",
                "OTHER");

        if (!allowedTypes.contains(normalizedType)) {
            throw new IllegalArgumentException("Invalid account type.");
        }

        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a three-letter code, such as INR.");
        }

        String normalizedCurrency = currency.trim()
                .toUpperCase(Locale.ROOT);

        if (openingBalance == null || openingBalance.scale() > 4
                || openingBalance.precision() > 19) {
            throw new IllegalArgumentException(
                    "Opening balance must fit NUMERIC(19,4).");
        }

        if (openingBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Opening balance cannot be negative.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found."));

        Account account = new Account(
                user,
                normalizedName,
                normalizedType,
                normalizedCurrency,
                openingBalance);

        return accountRepository.save(account);
    }

    public List<Account> getAccountsForUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found.");
        }

        return accountRepository.findAllByUser_Id(userId);
    }

    public Account getAccountForUser(UUID userId, UUID accountId) {
        if (userId == null || accountId == null) {
            throw new IllegalArgumentException(
                    "User ID and account ID are required.");
        }

        return accountRepository
                .findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found for this user."));
    }
    

	public BigDecimal getCurrentBalance(UUID userId, UUID accountId) {
	    Account account = getAccountForUser(userId, accountId);
	
	    BigDecimal totalDebits =
	            ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	                    account.getId(), "DEBIT");
	
	    BigDecimal totalCredits =
	            ledgerEntryRepository.sumAmountByAccountAndEntrySide(
	                    account.getId(), "CREDIT");
	
	    return account.getOpeningBalance()
	            .add(totalDebits)
	            .subtract(totalCredits);
	}
}
