package com.fintrack.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fintrack.reconciliation.dto.ParsedStatementRow;

class StatementCsvParserTest {

    private StatementCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new StatementCsvParser();
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesValidCsvWithPositiveAndNegativeAmounts() throws Exception {
        String content = """
                date,description,amount,currency,reference
                2026-10-20,Grocery Store,-450.00,INR,UPI123
                2026-10-21,Salary,25000.00,INR,SAL001
                """;

        List<ParsedStatementRow> rows = parser.parse(csv(content));

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("Grocery Store", rows.get(0).description());
        assertEquals("-450.00",
                rows.get(0).amount().toPlainString());
        assertEquals("UPI123", rows.get(0).reference());
        assertEquals("INR", rows.get(1).currency());
    }

    @Test
    void acceptsHeadersWithDifferentCapitalization() throws Exception {
        String content = """
                DATE,Description,AMOUNT,CURRENCY
                2026-10-20,Groceries,-100,INR
                """;

        List<ParsedStatementRow> rows = parser.parse(csv(content));

        assertEquals(1, rows.size());
        assertNull(rows.get(0).reference());
    }

    @Test
    void rejectsMissingRequiredHeader() {
        String content = """
                date,description,amount
                2026-10-20,Groceries,-100
                """;

        CsvImportValidationException exception =
                assertThrows(CsvImportValidationException.class,
                        () -> parser.parse(csv(content)));

        assertTrue(exception.getMessage().contains("currency"));
    }

    @Test
    void rejectsInvalidDate() {
        String content = """
                date,description,amount,currency
                20-10-2026,Groceries,-100,INR
                """;

        CsvImportValidationException exception =
                assertThrows(CsvImportValidationException.class,
                        () -> parser.parse(csv(content)));

        assertTrue(exception.getMessage().contains("row 2"));
        assertTrue(exception.getMessage().contains("YYYY-MM-DD"));
    }

    @Test
    void rejectsInvalidAmount() {
        String content = """
                date,description,amount,currency
                2026-10-20,Groceries,abc,INR
                """;

        CsvImportValidationException exception =
                assertThrows(CsvImportValidationException.class,
                        () -> parser.parse(csv(content)));

        assertTrue(exception.getMessage().contains("Invalid amount"));
    }

    @Test
    void rejectsZeroAmount() {
        String content = """
                date,description,amount,currency
                2026-10-20,Groceries,0,INR
                """;

        assertThrows(CsvImportValidationException.class,
                () -> parser.parse(csv(content)));
    }

    @Test
    void rejectsDuplicateHeaders() {
        String content = """
                date,description,amount,currency,DATE
                2026-10-20,Groceries,-100,INR,2026-10-20
                """;

        assertThrows(CsvImportValidationException.class,
                () -> parser.parse(csv(content)));
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(CsvImportValidationException.class,
                () -> parser.parse(csv("")));
    }

    @Test
    void rejectsHeaderWithoutDataRows() {
        String content = """
                date,description,amount,currency
                """;

        assertThrows(CsvImportValidationException.class,
                () -> parser.parse(csv(content)));
    }

    @Test
    void acceptsQuotedDescriptionsContainingCommas() throws Exception {
        String content = """
                date,description,amount,currency,reference
                2026-10-20,"Cafe, Bandra",-250.50,INR,REF001
                """;

        List<ParsedStatementRow> rows = parser.parse(csv(content));

        assertEquals("Cafe, Bandra", rows.get(0).description());
    }

    @Test
    void rejectsCurrencyThatIsNotThreeLetters() {
        String content = """
                date,description,amount,currency
                2026-10-20,Groceries,-100,INDIA
                """;

        assertThrows(CsvImportValidationException.class,
                () -> parser.parse(csv(content)));
    }
}
