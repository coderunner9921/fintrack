package com.fintrack.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.auth.JwtService;
import com.fintrack.transaction.FinancialTransaction;
import com.fintrack.transaction.TransactionService;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AnalyticsControllerIntegrationTest {

    private static final String TEST_SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    private static final LocalDate FROM =
            LocalDate.of(2026, 10, 1);

    private static final LocalDate TO =
            LocalDate.of(2026, 10, 31);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> TEST_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionService transactionService;

    private User userA;
    private User userB;
    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        userA = userRepository.saveAndFlush(
                new User(
                        "Analytics User A",
                        "analytics-a-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        userB = userRepository.saveAndFlush(
                new User(
                        "Analytics User B",
                        "analytics-b-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        userAToken = jwtService.generateToken(userA.getId());
        userBToken = jwtService.generateToken(userB.getId());
    }

    private MockHttpServletRequestBuilder requestAs(
            String path, String token) {
        return get(path).header("Authorization", "Bearer " + token);
    }

    private Account createAccount(User owner) {
        return accountRepository.saveAndFlush(
                new Account(
                        owner,
                        "Analytics Savings " + UUID.randomUUID(),
                        "SAVINGS",
                        "INR",
                        new BigDecimal("1000.00")));
    }

    private FinancialTransaction createExpense(
            User owner,
            String amount,
            LocalDate transactionDate,
            String idempotencyKey) {

        Account account = createAccount(owner);

        CreateTransactionRequest request =
                new CreateTransactionRequest();

        request.setTransactionType("EXPENSE");
        request.setDescription("Analytics API test expense");
        request.setCategory("Food");
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("INR");
        request.setTransactionDate(transactionDate);
        request.setSourceAccountId(account.getId());
        request.setDestinationAccountId(null);

        return transactionService.createTransaction(
                owner.getId(), request, idempotencyKey);
    }

    @Test
    void summaryEndpointReturnsAnalyticsForAuthenticatedUser()
            throws Exception {

        createExpense(
                userA,
                "75.00",
                LocalDate.of(2026, 10, 10),
                "analytics-api-summary-001");

        mockMvc.perform(requestAs(
                        "/api/analytics/summary?from=2026-10-01&to=2026-10-31",
                        userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.to").value("2026-10-31"))
                .andExpect(jsonPath("$.currencies").isArray())
                .andExpect(jsonPath("$.currencies.length()").value(1))
                .andExpect(jsonPath("$.currencies[0].currency").value("INR"))
                .andExpect(jsonPath("$.currencies[0].totalIncome").value(0))
                .andExpect(jsonPath("$.currencies[0].totalExpenses").value(75))
                .andExpect(jsonPath("$.currencies[0].netCashFlow").value(-75))
                .andExpect(jsonPath(
                        "$.currencies[0].expenseTransactionCount").value(1))
                .andExpect(jsonPath(
                        "$.currencies[0].expensesByCategory[0].categoryName")
                        .value("Food"))
                .andExpect(jsonPath(
                        "$.currencies[0].expensesByCategory[0].total")
                        .value(75));
    }

    @Test
    void summaryEndpointWithoutTokenReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get(
                        "/api/analytics/summary?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summaryEndpointRejectsInvalidDateRange()
            throws Exception {

        mockMvc.perform(requestAs(
                        "/api/analytics/summary?from=2026-10-31&to=2026-10-01",
                        userAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryEndpointOnlyIncludesAuthenticatedUsersTransactions()
            throws Exception {

        createExpense(
                userA,
                "40.00",
                LocalDate.of(2026, 10, 10),
                "analytics-api-isolation-a-001");

        createExpense(
                userB,
                "90.00",
                LocalDate.of(2026, 10, 12),
                "analytics-api-isolation-b-001");

        mockMvc.perform(requestAs(
                        "/api/analytics/summary?from=2026-10-01&to=2026-10-31",
                        userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies[0].totalExpenses").value(40))
                .andExpect(jsonPath(
                        "$.currencies[0].expenseTransactionCount").value(1));

        mockMvc.perform(requestAs(
                        "/api/analytics/summary?from=2026-10-01&to=2026-10-31",
                        userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies[0].totalExpenses").value(90))
                .andExpect(jsonPath(
                        "$.currencies[0].expenseTransactionCount").value(1));
    }
}