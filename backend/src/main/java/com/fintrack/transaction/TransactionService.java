package com.fintrack.transaction;

import java.math.BigDecimal;
import java.util.List;
import com.fintrack.transaction.dto.TransactionResponse;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

@Service
public class TransactionService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository) {

        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }


    @Transactional
    public FinancialTransaction createTransaction(
            UUID userId,
            CreateTransactionRequest request) {

        // Preserve compatibility with existing tests and internal callers.
        return createTransactionInternal(userId, request, null);
    }

    @Transactional
    public FinancialTransaction createTransaction(
            UUID userId,
            CreateTransactionRequest request,
            String idempotencyKey) {

        String normalizedKey = requiredText(
                idempotencyKey, "Idempotency-Key");

        if (normalizedKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key cannot exceed 100 characters.");
        }

        return createTransactionInternal(
                userId, request, normalizedKey);
    }

    private FinancialTransaction createTransactionInternal(
            UUID userId,
            CreateTransactionRequest request,
            String idempotencyKey) {

        if (userId == null || request == null) {
            throw new IllegalArgumentException(
                    "User and transaction request are required.");
        }

        // Lock the user row for API requests that have an idempotency key.
        // This serializes same-user requests while we check and create.
        User user = (idempotencyKey == null
                ? userRepository.findById(userId)
                : userRepository.findByIdForUpdate(userId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "User does not exist."));

        String type = requiredText(
                request.getTransactionType(), "Transaction type")
                .toUpperCase(Locale.ROOT);

        String description = requiredText(
                request.getDescription(), "Description");

        String category = requiredText(
                request.getCategory(), "Category");

        String currency = requiredText(
                request.getCurrency(), "Currency")
                .toUpperCase(Locale.ROOT);

        BigDecimal amount = request.getAmount();
        LocalDate transactionDate = request.getTransactionDate();

        validateType(type);
        validateCurrency(currency);
        validateAmount(amount);

        if (transactionDate == null) {
            throw new IllegalArgumentException(
                    "Transaction date is required.");
        }

        if (description.length() > 255 || category.length() > 80) {
            throw new IllegalArgumentException(
                    "Description or category exceeds the allowed length.");
        }

        UUID sourceId = request.getSourceAccountId();
        UUID destinationId = request.getDestinationAccountId();

        // Check for a previous successful request before checking balances
        // or creating any new ledger entries.
        if (idempotencyKey != null) {
            var existing = transactionRepository
                    .findByUser_IdAndIdempotencyKey(userId, idempotencyKey);

            if (existing.isPresent()) {
                FinancialTransaction previous = existing.get();

                boolean sameRequest =
                        previous.getTransactionType().equals(type)
                        && previous.getDescription().equals(description)
                        && previous.getCategory().equals(category)
                        && previous.getAmount().compareTo(amount) == 0
                        && previous.getCurrency().equals(currency)
                        && previous.getTransactionDate().equals(transactionDate)
                        && java.util.Objects.equals(
                                previous.getSourceAccount() == null
                                        ? null : previous.getSourceAccount().getId(),
                                sourceId)
                        && java.util.Objects.equals(
                                previous.getDestinationAccount() == null
                                        ? null : previous.getDestinationAccount().getId(),
                                destinationId);

                if (!sameRequest) {
                    throw new IllegalArgumentException(
                            "This Idempotency-Key has already been used "
                            + "for a different transaction.");
                }

                return previous;
            }
        }

        Account source = null;
        Account destination = null;

        switch (type) {
            case "INCOME":
                if (sourceId != null || destinationId == null) {
                    throw new IllegalArgumentException(
                            "Income requires a destination account only.");
                }
                destination = getOwnedActiveAccount(userId, destinationId);
                break;

            case "EXPENSE":
                if (sourceId == null || destinationId != null) {
                    throw new IllegalArgumentException(
                            "Expense requires a source account only.");
                }
                source = getOwnedActiveAccountForUpdate(userId, sourceId);
                break;

            case "TRANSFER":
                if (sourceId == null || destinationId == null) {
                    throw new IllegalArgumentException(
                            "Transfer requires source and destination accounts.");
                }

                if (sourceId.equals(destinationId)) {
                    throw new IllegalArgumentException(
                            "Source and destination accounts must differ.");
                }

                source = getOwnedActiveAccountForUpdate(userId, sourceId);
                destination = getOwnedActiveAccount(userId, destinationId);

                if (!source.getCurrency().equalsIgnoreCase(
                        destination.getCurrency())) {
                    throw new IllegalArgumentException(
                            "Transfers between different currencies are not supported.");
                }
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported transaction type.");
        }

        Account relevantAccount = source != null ? source : destination;

        if (!currency.equalsIgnoreCase(relevantAccount.getCurrency())) {
            throw new IllegalArgumentException(
                    "Transaction currency must match the account currency.");
        }

        if (source != null) {
            validateSufficientFunds(source, amount);
        }

        FinancialTransaction transaction = new FinancialTransaction(
                user,
                type,
                description,
                category,
                amount,
                currency,
                transactionDate,
                source,
                destination,
                idempotencyKey
        );

        FinancialTransaction savedTransaction =
                transactionRepository.save(transaction);

        createLedgerEntries(
                savedTransaction, source, destination, amount, type);

        return savedTransaction;
    }


    private Account getOwnedActiveAccount(
            UUID userId, UUID accountId) {

        Account account = accountRepository
                .findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account does not exist for this user."));

        if (!account.isActive()) {
            throw new IllegalArgumentException(
                    "Inactive accounts cannot be used for transactions.");
        }

        return account;
    }

    private void createLedgerEntries(
            FinancialTransaction transaction,
            Account source,
            Account destination,
            BigDecimal amount,
            String type) {

        LedgerEntry debit;
        LedgerEntry credit;

        switch (type) {
            case "INCOME":
                debit = new LedgerEntry(
                        transaction, destination, null, "DEBIT", amount);

                credit = new LedgerEntry(
                        transaction, null, "INCOME", "CREDIT", amount);
                break;

            case "EXPENSE":
                debit = new LedgerEntry(
                        transaction, null, "EXPENSE", "DEBIT", amount);

                credit = new LedgerEntry(
                        transaction, source, null, "CREDIT", amount);
                break;

            case "TRANSFER":
                debit = new LedgerEntry(
                        transaction, destination, null, "DEBIT", amount);

                credit = new LedgerEntry(
                        transaction, source, null, "CREDIT", amount);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported transaction type.");
        }

        if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
            throw new IllegalStateException(
                    "Ledger debits and credits must balance.");
        }

        ledgerEntryRepository.saveAll(java.util.List.of(debit, credit));
    }

    private void validateType(String type) {
        if (!type.equals("INCOME")
                && !type.equals("EXPENSE")
                && !type.equals("TRANSFER")) {
            throw new IllegalArgumentException(
                    "Transaction type must be INCOME, EXPENSE, or TRANSFER.");
        }
    }

    private void validateCurrency(String currency) {
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a three-letter code.");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero.");
        }

        BigDecimal normalized = amount.stripTrailingZeros();
        int integerDigits = Math.max(
                0, normalized.precision() - normalized.scale());

        if (normalized.scale() > 4 || integerDigits > 15) {
            throw new IllegalArgumentException(
                    "Amount supports up to 15 integer digits "
                    + "and 4 decimal places.");
        }
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required.");
        }

        return value.trim();
    }
    
    private Account getOwnedActiveAccountForUpdate(
            UUID userId, UUID accountId) {

        Account account = accountRepository
                .findByIdAndUser_IdForUpdate(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account does not exist for this user."));

        if (!account.isActive()) {
            throw new IllegalArgumentException(
                    "Inactive accounts cannot be used for transactions.");
        }

        return account;
    }

    private BigDecimal getBalanceForAccount(Account account) {

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

    private void validateSufficientFunds(
            Account source, BigDecimal amount) {

        // Credit-card limits and liability accounting are not modelled yet.
        if ("CREDIT_CARD".equalsIgnoreCase(source.getAccountType())) {
            return;
        }

        BigDecimal currentBalance = getBalanceForAccount(source);

        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds. Available balance: "
                    + currentBalance.toPlainString());
        }
    }
    
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsForUser(UUID userId) {

        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User does not exist.");
        }

        return transactionRepository
                .findAllByUser_IdOrderByTransactionDateDesc(userId)
                .stream()
                .map(TransactionResponse::new)
                .toList();
    }
    
    @Transactional
    public FinancialTransaction reverseTransaction(
            UUID userId,
            UUID transactionId) {

        if (userId == null || transactionId == null) {
            throw new IllegalArgumentException(
                    "User and transaction IDs are required.");
        }

        // Lock the original transaction so competing reversal requests serialize.
        FinancialTransaction original = transactionRepository
                .findByIdAndUser_IdForUpdate(transactionId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction does not exist for this user."));

        if ("REVERSAL".equals(original.getTransactionType())) {
            throw new IllegalArgumentException(
                    "A reversal transaction cannot be reversed.");
        }

        if (transactionRepository
                .existsByReversalOfTransaction_Id(original.getId())) {
            throw new IllegalArgumentException(
                    "This transaction has already been reversed.");
        }

        Account source = original.getSourceAccount();
        Account destination = original.getDestinationAccount();

        // Reversing income or a transfer reduces the destination account's
        // balance, so lock it and verify that the funds are still available.
        if ("INCOME".equals(original.getTransactionType())
                || "TRANSFER".equals(original.getTransactionType())) {

            destination = getOwnedActiveAccountForUpdate(
                    userId, destination.getId());

            validateSufficientFunds(destination, original.getAmount());
        }

        FinancialTransaction reversal = new FinancialTransaction(
                original.getUser(),
                "REVERSAL",
                "Reversal of transaction " + original.getId(),
                original.getCategory(),
                original.getAmount(),
                original.getCurrency(),
                LocalDate.now(),
                source,
                destination,
                null);

        reversal.markAsReversalOf(original);

        FinancialTransaction savedReversal =
                transactionRepository.save(reversal);

        List<LedgerEntry> originalEntries =
                ledgerEntryRepository.findAllByTransaction_Id(
                        original.getId());

        // Refuse to reverse incomplete or unbalanced historical ledger data.
        if (originalEntries.size() != 2) {
            throw new IllegalStateException(
                    "Original transaction must have exactly two ledger entries.");
        }

        boolean hasDebit = originalEntries.stream()
                .anyMatch(entry -> "DEBIT".equals(entry.getEntrySide()));

        boolean hasCredit = originalEntries.stream()
                .anyMatch(entry -> "CREDIT".equals(entry.getEntrySide()));

        boolean amountsMatch = originalEntries.get(0).getAmount()
                .compareTo(originalEntries.get(1).getAmount()) == 0;

        if (!hasDebit || !hasCredit || !amountsMatch) {
            throw new IllegalStateException(
                    "Original transaction has an invalid or unbalanced ledger.");
        }

        List<LedgerEntry> compensatingEntries = originalEntries.stream()
                .map(entry -> new LedgerEntry(
                        savedReversal,
                        entry.getAccount(),
                        entry.getSystemAccountCode(),
                        "DEBIT".equals(entry.getEntrySide())
                                ? "CREDIT" : "DEBIT",
                        entry.getAmount()))
                .toList();

        ledgerEntryRepository.saveAll(compensatingEntries);

        return savedReversal;
    }
}
