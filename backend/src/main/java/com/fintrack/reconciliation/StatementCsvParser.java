
package com.fintrack.reconciliation;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.fintrack.reconciliation.dto.ParsedStatementRow;

@Component
public class StatementCsvParser {

    private static final Set<String> REQUIRED_HEADERS =
            Set.of("date", "description", "amount", "currency");

    private static final int MAX_ROWS = 10_000;

    public List<ParsedStatementRow> parse(InputStream inputStream)
            throws IOException {

        if (inputStream == null) {
            throw new CsvImportValidationException(
                    "CSV file is required.");
        }

        String content = new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8);

        // Remove a UTF-8 byte-order mark if the file contains one.
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }

        if (content.isBlank()) {
            throw new CsvImportValidationException(
                    "CSV file is empty.");
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(
                new StringReader(content), format)) {

            Map<String, String> headers =
                    validateHeaders(parser.getHeaderNames());

            List<ParsedStatementRow> rows = new ArrayList<>();

            for (CSVRecord record : parser) {
                int rowNumber = Math.toIntExact(
                        record.getRecordNumber() + 1);

                if (!record.isConsistent()) {
                    throw rowError(rowNumber,
                            "The number of columns does not match the header.");
                }

                if (rows.size() >= MAX_ROWS) {
                    throw new CsvImportValidationException(
                            "CSV exceeds the maximum of "
                                    + MAX_ROWS + " data rows.");
                }

                rows.add(parseRecord(record, rowNumber, headers));
            }

            if (rows.isEmpty()) {
                throw new CsvImportValidationException(
                        "CSV must contain at least one data row.");
            }

            return List.copyOf(rows);

        } catch (CsvImportValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CsvImportValidationException(
                    "CSV format is invalid. Check the header and column structure.");
        }
    }

    private Map<String, String> validateHeaders(List<String> headerNames) {
        if (headerNames == null || headerNames.isEmpty()) {
            throw new CsvImportValidationException(
                    "CSV header row is missing.");
        }

        Map<String, String> headers = new HashMap<>();
        Set<String> seen = new HashSet<>();

        for (String header : headerNames) {
            if (header == null || header.isBlank()) {
                throw new CsvImportValidationException(
                        "CSV contains an empty column name.");
            }

            String normalized = header.trim().toLowerCase(Locale.ROOT);

            if (!seen.add(normalized)) {
                throw new CsvImportValidationException(
                        "Duplicate CSV column: " + normalized);
            }

            headers.put(normalized, header);
        }

        Set<String> missing = new HashSet<>(REQUIRED_HEADERS);
        missing.removeAll(headers.keySet());

        if (!missing.isEmpty()) {
            throw new CsvImportValidationException(
                    "Missing required CSV columns: " + missing);
        }

        return headers;
    }

    private ParsedStatementRow parseRecord(
            CSVRecord record,
            int rowNumber,
            Map<String, String> headers) {

        String dateValue = value(record, headers, "date");
        String description = value(record, headers, "description");
        String amountValue = value(record, headers, "amount");
        String currencyValue = value(record, headers, "currency");
        String reference = value(record, headers, "reference");

        if (dateValue.isBlank()) {
            throw rowError(rowNumber, "Date is required.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateValue);
        } catch (DateTimeParseException exception) {
            throw rowError(rowNumber,
                    "Invalid date. Use YYYY-MM-DD.");
        }

        if (description.isBlank()) {
            throw rowError(rowNumber, "Description is required.");
        }

        if (description.length() > 255) {
            throw rowError(rowNumber,
                    "Description must not exceed 255 characters.");
        }

        if (amountValue.isBlank()) {
            throw rowError(rowNumber, "Amount is required.");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountValue);
        } catch (NumberFormatException exception) {
            throw rowError(rowNumber,
                    "Invalid amount: " + amountValue);
        }

        if (amount.signum() == 0) {
            throw rowError(rowNumber,
                    "Amount must not be zero.");
        }

        if (amount.scale() > 4
                || amount.precision() - amount.scale() > 15) {
            throw rowError(rowNumber,
                    "Amount exceeds the supported precision of NUMERIC(19,4).");
        }

        if (currencyValue.isBlank()) {
            throw rowError(rowNumber, "Currency is required.");
        }

        String currency = currencyValue.toUpperCase(Locale.ROOT);

        if (!currency.matches("[A-Z]{3}")) {
            throw rowError(rowNumber,
                    "Currency must be a three-letter code, such as INR.");
        }

        if (reference.length() > 255) {
            throw rowError(rowNumber,
                    "Reference must not exceed 255 characters.");
        }

        return new ParsedStatementRow(
                rowNumber,
                date,
                description,
                amount,
                currency,
                reference.isBlank() ? null : reference);
    }

    private String value(
            CSVRecord record,
            Map<String, String> headers,
            String name) {

        String actualHeader = headers.get(name);

        if (actualHeader == null) {
            return "";
        }

        return record.get(actualHeader).trim();
    }

    private CsvImportValidationException rowError(
            int rowNumber, String message) {

        return new CsvImportValidationException(
                "CSV row " + rowNumber + ": " + message);
    }
}
