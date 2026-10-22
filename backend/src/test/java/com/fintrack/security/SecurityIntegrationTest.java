package com.fintrack.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.auth.JwtService;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class SecurityIntegrationTest {

    private static final String TEST_SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

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
    private JdbcTemplate jdbcTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;

    private User userA;
    private User userB;
    private Account accountA;
    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        userA = userRepository.saveAndFlush(new User(
                "Security User A",
                "security-a-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        userB = userRepository.saveAndFlush(new User(
                "Security User B",
                "security-b-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        accountA = accountRepository.saveAndFlush(new Account(
                userA,
                "User A Savings",
                "SAVINGS",
                "INR",
                new BigDecimal("1000.0000")));

        userAToken = jwtService.generateToken(userA.getId());
        userBToken = jwtService.generateToken(userB.getId());
    }

    private MockHttpServletRequestBuilder requestAs(
            String path, String token) {
        return get(path).header("Authorization", "Bearer " + token);
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized()
            throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsUnauthorized()
            throws Exception {
        mockMvc.perform(requestAs(
                        "/api/accounts", "this-is-not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorized()
            throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(TEST_SECRET));

        Date issuedAt = new Date(System.currentTimeMillis() - 120_000);
        Date expiration = new Date(System.currentTimeMillis() - 60_000);

        String expiredToken = Jwts.builder()
                .subject(userA.getId().toString())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();

        mockMvc.perform(requestAs("/api/accounts", expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotAccessAdminEndpoint()
            throws Exception {
        mockMvc.perform(requestAs("/api/admin/status", userAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminEndpoint()
            throws Exception {
        User admin = userRepository.saveAndFlush(new User(
                "Security Admin",
                "security-admin-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        // Provision the admin role directly in the test database.
        // Public registration must never accept a requested role.
        jdbcTemplate.update(
                "UPDATE users SET role = 'ADMIN' WHERE id = ?",
                admin.getId());
        entityManager.clear();

        String adminToken = jwtService.generateToken(admin.getId());

        mockMvc.perform(requestAs("/api/admin/status", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message")
                        .value("Admin access verified"));
    }

    @Test
    void userCannotReadAnotherUsersAccount()
            throws Exception {
        mockMvc.perform(requestAs(
                        "/api/accounts/" + accountA.getId(), userBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountListContainsOnlyAuthenticatedUsersAccounts()
            throws Exception {
        mockMvc.perform(requestAs("/api/accounts", userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(requestAs("/api/accounts", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void userCannotSeeAnotherUsersTransactions()
            throws Exception {
        jdbcTemplate.update("""
                INSERT INTO transactions
                    (id, user_id, transaction_type, description, category,
                     amount, currency, transaction_date, source_account_id)
                VALUES (?, ?, 'EXPENSE', 'Private transaction', 'Testing',
                        10.0000, 'INR', CURRENT_DATE, ?)
                """,
                UUID.randomUUID(),
                userA.getId(),
                accountA.getId());

        mockMvc.perform(requestAs("/api/transactions", userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(requestAs("/api/transactions", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
