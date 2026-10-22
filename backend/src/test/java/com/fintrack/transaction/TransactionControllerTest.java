package com.fintrack.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.OffsetDateTime;
import com.fintrack.transaction.FinancialTransaction;
class TransactionControllerTest {

    private TransactionService transactionService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        transactionService = Mockito.mock(TransactionService.class);
        TransactionController controller =
                new TransactionController(transactionService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        userId = UUID.randomUUID();
    }

    private RequestPostProcessor authenticatedUser() {
        return request -> {
            request.setUserPrincipal(
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(), null));
            return request;
        };
    }

    @Test
    void getTransactionsReturnsEmptyListWhenUserHasNoTransactions()
            throws Exception {

        when(transactionService.getTransactionsForUser(userId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/transactions")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createTransactionReturnsCreatedWhenRequestIsValid()
            throws Exception {

        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTransactionType("EXPENSE");
        request.setDescription("Lunch");
        request.setCategory("Food");
        request.setAmount(new BigDecimal("25.00"));
        request.setCurrency("INR");
        request.setTransactionDate(LocalDate.now());
        request.setSourceAccountId(accountId);

        FinancialTransaction savedTransaction =
                Mockito.mock(FinancialTransaction.class);

        when(savedTransaction.getId()).thenReturn(transactionId);
        when(savedTransaction.getTransactionType()).thenReturn("EXPENSE");
        when(savedTransaction.getDescription()).thenReturn("Lunch");
        when(savedTransaction.getCategory()).thenReturn("Food");
        when(savedTransaction.getAmount()).thenReturn(new BigDecimal("25.00"));
        when(savedTransaction.getCurrency()).thenReturn("INR");
        when(savedTransaction.getTransactionDate()).thenReturn(LocalDate.now());
        when(savedTransaction.getSourceAccount()).thenReturn(null);
        when(savedTransaction.getDestinationAccount()).thenReturn(null);
        when(savedTransaction.getCreatedAt()).thenReturn(OffsetDateTime.now());

        when(transactionService.createTransaction(
                eq(userId),
                any(CreateTransactionRequest.class),
                eq("controller-test-001")))
                .thenReturn(savedTransaction);

        mockMvc.perform(post("/api/transactions")
                        .with(authenticatedUser())
                        .header("Idempotency-Key", "controller-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$.description").value("Lunch"))
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void createTransactionWithoutIdempotencyKeyReturnsBadRequest()
            throws Exception {

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTransactionType("EXPENSE");
        request.setDescription("Lunch");
        request.setCategory("Food");
        request.setAmount(new BigDecimal("25.00"));
        request.setCurrency("INR");
        request.setTransactionDate(LocalDate.now());
        request.setSourceAccountId(UUID.randomUUID());

        mockMvc.perform(post("/api/transactions")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
