package com.fintrack.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fintrack.account.Account;
import com.fintrack.reconciliation.dto.ParsedStatementRow;
import com.fintrack.transaction.FinancialTransaction;
import com.fintrack.transaction.LedgerEntry;
import com.fintrack.transaction.LedgerEntryRepository;

@Component
public class StatementTransactionMatcher {

    public record MatchResult(
            StatementRow.RowStatus status,
            FinancialTransaction transaction) {
    }

    private enum Direction {
        DEBIT,
        CREDIT
    }

    private final LedgerEntryRepository ledgerEntryRepository;

    /*
     * Retained for existing unit-test callers.
     * Without a repository, reversal matches fail safely as MISMATCHED.
     */
    public StatementTransactionMatcher() {
        this.ledgerEntryRepository = null;
    }

    @Autowired
    public StatementTransactionMatcher(
            LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository =
                Objects.requireNonNull(ledgerEntryRepository);
    }

    public MatchResult match(
            ParsedStatementRow statementRow,
            UUID accountId,
            List<FinancialTransaction> transactions) {

        Objects.requireNonNull(statementRow, "statementRow is required");
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(transactions, "transactions is required");

        Direction statementDirection =
                statementRow.amount().signum() < 0
                        ? Direction.DEBIT
                        : Direction.CREDIT;

        BigDecimal statementAmount = statementRow.amount().abs();

        List<FinancialTransaction> sameAccountDateDirection =
                transactions.stream()
                        .filter(transaction ->
                                involvesAccount(transaction, accountId))
                        .filter(transaction ->
                                transaction.getTransactionDate()
                                        .equals(statementRow.date()))
                        .filter(transaction ->
                                accountDirection(transaction, accountId)
                                        == statementDirection)
                        .toList();

        List<FinancialTransaction> exactMatches =
                sameAccountDateDirection.stream()
                        .filter(transaction ->
                                transaction.getCurrency()
                                        .equalsIgnoreCase(
                                                statementRow.currency()))
                        .filter(transaction ->
                                transaction.getAmount().compareTo(
                                        statementAmount) == 0)
                        .toList();

        if (exactMatches.size() == 1) {
            return new MatchResult(
                    StatementRow.RowStatus.MATCHED,
                    exactMatches.get(0));
        }

        if (exactMatches.size() > 1) {
            return new MatchResult(
            		StatementRow.RowStatus.MISMATCHED,
                    null);
        }
        boolean invalidReversal = transactions.stream()
                .filter(t -> "REVERSAL".equals(t.getTransactionType()))
                .filter(t -> involvesAccount(t, accountId))
                .filter(t -> t.getTransactionDate().equals(statementRow.date()))
                .filter(t -> t.getAmount().compareTo(statementAmount) == 0)
                .filter(t -> t.getCurrency().equalsIgnoreCase(statementRow.currency()))
                .anyMatch(t -> accountDirection(t, accountId) == null);

        if (invalidReversal) {
            return new MatchResult(StatementRow.RowStatus.MISMATCHED, null);
        }

        if (!sameAccountDateDirection.isEmpty()) {

            boolean unverifiableReversal = transactions.stream()
                    .filter(transaction ->
                            "REVERSAL".equals(transaction.getTransactionType()))
                    .filter(transaction ->
                            involvesAccount(transaction, accountId))
                    .filter(transaction ->
                            transaction.getTransactionDate()
                                    .equals(statementRow.date()))
                    .anyMatch(transaction ->
                            accountDirection(transaction, accountId) == null);

            if (unverifiableReversal) {
                return new MatchResult(
                        StatementRow.RowStatus.MISMATCHED,
                        null);
            }
        	
            return new MatchResult(
                    StatementRow.RowStatus.MISMATCHED,
                    null);
        }

        return new MatchResult(
                StatementRow.RowStatus.UNMATCHED,
                null);
    }

    private boolean involvesAccount(
            FinancialTransaction transaction,
            UUID accountId) {

        return hasAccountId(transaction.getSourceAccount(), accountId)
                || hasAccountId(
                        transaction.getDestinationAccount(), accountId);
    }

    private boolean hasAccountId(Account account, UUID accountId) {
        return account != null
                && account.getId().equals(accountId);
    }

    private Direction accountDirection(
            FinancialTransaction transaction,
            UUID accountId) {

        if (transaction == null) {
            return null;
        }

        String type = transaction.getTransactionType();

        boolean isSource =
                hasAccountId(transaction.getSourceAccount(), accountId);

        boolean isDestination =
                hasAccountId(
                        transaction.getDestinationAccount(), accountId);

        return switch (type) {
            case "INCOME" -> isDestination
                    ? Direction.CREDIT : null;

            case "EXPENSE" -> isSource
                    ? Direction.DEBIT : null;

            case "TRANSFER" -> isSource
                    ? Direction.DEBIT
                    : isDestination ? Direction.CREDIT : null;

            case "REVERSAL" -> reversalDirection(
                    transaction, accountId);

            default -> null;
        };
    }

    private Direction reversalDirection(
            FinancialTransaction reversal,
            UUID accountId) {

        if (ledgerEntryRepository == null) {
            return null;
        }

        FinancialTransaction original =
                reversal.getReversalOfTransaction();

        if (original == null
                || "REVERSAL".equals(original.getTransactionType())
                || !involvesAccount(original, accountId)
                || !involvesAccount(reversal, accountId)
                || reversal.getAmount().compareTo(
                        original.getAmount()) != 0
                || !reversal.getCurrency().equalsIgnoreCase(
                        original.getCurrency())) {
            return null;
        }

        List<LedgerEntry> originalEntries =
                ledgerEntryRepository.findAllByTransaction_Id(
                        original.getId());

        List<LedgerEntry> reversalEntries =
                ledgerEntryRepository.findAllByTransaction_Id(
                        reversal.getId());

        List<LedgerEntry> originalAccountEntries =
                originalEntries.stream()
                        .filter(entry ->
                                hasAccountId(entry.getAccount(), accountId))
                        .toList();

        List<LedgerEntry> reversalAccountEntries =
                reversalEntries.stream()
                        .filter(entry ->
                                hasAccountId(entry.getAccount(), accountId))
                        .toList();

        // Require exactly one account entry for each transaction.
        // Missing or ambiguous ledger evidence must not be guessed.
        if (originalAccountEntries.size() != 1
                || reversalAccountEntries.size() != 1) {
            return null;
        }

        LedgerEntry originalEntry = originalAccountEntries.get(0);
        LedgerEntry reversalEntry = reversalAccountEntries.get(0);

        if (originalEntry.getAmount().compareTo(
                    original.getAmount()) != 0
                || reversalEntry.getAmount().compareTo(
                    reversal.getAmount()) != 0) {
            return null;
        }

        String originalSide = originalEntry.getEntrySide();
        String reversalSide = reversalEntry.getEntrySide();

        if (!isLedgerSide(originalSide)
                || !isLedgerSide(reversalSide)
                || originalSide.equals(reversalSide)) {
            return null;
        }

        /*
         * Validate that the original's account role agrees with its
         * ledger side. Under this project's convention:
         *   INCOME destination -> ledger DEBIT -> statement CREDIT
         *   EXPENSE source     -> ledger CREDIT -> statement DEBIT
         *   TRANSFER source    -> ledger CREDIT -> statement DEBIT
         *   TRANSFER dest.     -> ledger DEBIT -> statement CREDIT
         */
        Direction originalExpectedDirection =
                originalAccountDirection(original, accountId);

        if (originalExpectedDirection == null
                || directionFromLedgerSide(originalSide)
                        != originalExpectedDirection) {
            return null;
        }

        // A reversal must invert the original's ledger side and direction.
        Direction reversalDirection =
                directionFromLedgerSide(reversalSide);

        if (reversalDirection == originalExpectedDirection) {
            return null;
        }

        return reversalDirection;
    }

    private Direction originalAccountDirection(
            FinancialTransaction original,
            UUID accountId) {

        boolean isSource =
                hasAccountId(original.getSourceAccount(), accountId);

        boolean isDestination =
                hasAccountId(original.getDestinationAccount(), accountId);

        return switch (original.getTransactionType()) {
            case "INCOME" -> isDestination
                    ? Direction.CREDIT : null;

            case "EXPENSE" -> isSource
                    ? Direction.DEBIT : null;

            case "TRANSFER" -> isSource
                    ? Direction.DEBIT
                    : isDestination ? Direction.CREDIT : null;

            default -> null;
        };
    }

    private Direction directionFromLedgerSide(String entrySide) {
        // The project's account ledger convention is opposite to
        // the statement's cash-flow direction.
        return switch (entrySide) {
            case "DEBIT" -> Direction.CREDIT;
            case "CREDIT" -> Direction.DEBIT;
            default -> null;
        };
    }

    private boolean isLedgerSide(String entrySide) {
        return "DEBIT".equals(entrySide)
                || "CREDIT".equals(entrySide);
    }
}
