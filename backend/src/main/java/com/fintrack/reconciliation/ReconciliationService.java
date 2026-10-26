package com.fintrack.reconciliation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.reconciliation.dto.ParsedStatementRow;
import com.fintrack.reconciliation.dto.ReconciliationImportResponse;
import com.fintrack.reconciliation.dto.StatementRowResponse;
import com.fintrack.transaction.FinancialTransaction;
import com.fintrack.transaction.FinancialTransactionRepository;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

@Service
public class ReconciliationService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final ReconciliationImportRepository importRepository;
    private final StatementRowRepository statementRowRepository;
    private final StatementCsvParser csvParser;
    private final StatementTransactionMatcher matcher;

    public ReconciliationService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            ReconciliationImportRepository importRepository,
            StatementRowRepository statementRowRepository,
            StatementCsvParser csvParser,
            StatementTransactionMatcher matcher) {

        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.importRepository = importRepository;
        this.statementRowRepository = statementRowRepository;
        this.csvParser = csvParser;
        this.matcher = matcher;
    }

    @Transactional
    public ReconciliationImportResponse importStatement(
            UUID userId,
            UUID accountId,
            String originalFilename,
            InputStream inputStream) throws IOException {

        if (userId == null || accountId == null) {
            throw new IllegalArgumentException(
                    "User and account are required.");
        }

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new CsvImportValidationException(
                    "A CSV filename is required.");
        }

        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CsvImportValidationException(
                    "Only CSV files are supported.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        Account account = accountRepository
                .findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found for this user."));

        // Parse and validate the entire CSV before creating the import.
        List<ParsedStatementRow> parsedRows = csvParser.parse(inputStream);

        List<FinancialTransaction> transactions =
                transactionRepository
                        .findAllByUser_IdOrderByTransactionDateDesc(userId);

        ReconciliationImport reconciliationImport =
                new ReconciliationImport(
                        user, account, originalFilename);

        reconciliationImport =
                importRepository.save(reconciliationImport);

        Map<DuplicateKey, StatementRow> firstOccurrences =
                new HashMap<>();

        List<StatementRow> savedRows = new ArrayList<>();

        int matchedCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int mismatchedCount = 0;

        for (ParsedStatementRow parsed : parsedRows) {
            StatementRow row = new StatementRow(
                    reconciliationImport,
                    parsed.rowNumber(),
                    parsed.date(),
                    parsed.description(),
                    parsed.amount(),
                    parsed.currency(),
                    parsed.reference());

            DuplicateKey key = DuplicateKey.from(parsed);
            StatementRow originalRow = firstOccurrences.get(key);

            if (originalRow != null) {
                row.markDuplicate(originalRow);
                duplicateCount++;
            } else {
                firstOccurrences.put(key, row);

                if (!account.getCurrency().equalsIgnoreCase(
                        parsed.currency())) {
                    row.markMismatched();
                    mismatchedCount++;
                } else {
                    StatementTransactionMatcher.MatchResult result =
                            matcher.match(
                                    parsed, accountId, transactions);

                    switch (result.status()) {
                        case MATCHED -> {
                            row.markMatched(result.transaction());
                            matchedCount++;
                        }
                        case UNMATCHED -> {
                            row.markUnmatched();
                            unmatchedCount++;
                        }
                        case DUPLICATE -> {
                            // The matcher doesn't currently classify
                            // duplicates; duplicate detection is handled
                            // above in this service.
                            row.markUnmatched();
                            unmatchedCount++;
                        }
                        case MISMATCHED -> {
                            row.markMismatched();
                            mismatchedCount++;
                        }
                        case PENDING -> throw new IllegalStateException(
                                "Matcher returned an unexpected PENDING status.");
                    }
                }
            }

            // Save each row so its generated ID is available if a later
            // duplicate refers back to this row.
            savedRows.add(statementRowRepository.save(row));
        }

        reconciliationImport.complete(
                parsedRows.size(),
                matchedCount,
                unmatchedCount,
                duplicateCount,
                mismatchedCount);

        reconciliationImport =
                importRepository.save(reconciliationImport);

        return ReconciliationImportResponse.from(reconciliationImport);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationImportResponse> getImports(UUID userId) {
        return importRepository
                .findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ReconciliationImportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationImportResponse getImport(
            UUID userId, UUID importId) {

        ReconciliationImport reconciliationImport =
                importRepository.findByIdAndUser_Id(importId, userId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Reconciliation import not found."));

        return ReconciliationImportResponse.from(reconciliationImport);
    }

    @Transactional(readOnly = true)
    public List<StatementRowResponse> getRows(
            UUID userId, UUID importId) {

        // Verify ownership before returning any statement rows.
        importRepository.findByIdAndUser_Id(importId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Reconciliation import not found."));

        return statementRowRepository
                .findAllByReconciliationImport_IdOrderByRowNumberAsc(
                        importId)
                .stream()
                .map(StatementRowResponse::from)
                .toList();
    }

    private record DuplicateKey(
            java.time.LocalDate date,
            String description,
            BigDecimal amount,
            String currency,
            String reference) {

        static DuplicateKey from(ParsedStatementRow row) {
            return new DuplicateKey(
                    row.date(),
                    row.description().trim().toLowerCase(Locale.ROOT),
                    row.amount().stripTrailingZeros(),
                    row.currency().toUpperCase(Locale.ROOT),
                    row.reference() == null
                            ? ""
                            : row.reference().trim()
                                    .toLowerCase(Locale.ROOT));
        }
    }
}
