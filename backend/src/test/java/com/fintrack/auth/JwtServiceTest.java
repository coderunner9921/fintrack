
package com.fintrack.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);

        String testSecret = Base64.getEncoder()
                .encodeToString(secretBytes);

        jwtService = new JwtService(testSecret, 900_000);
    }

    @Test
    void generateTokenShouldContainCorrectUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId, jwtService.extractUserId(token));
    }

    @Test
    void tokenShouldBeInvalidWhenSignatureIsChanged() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId);
        String tamperedToken = token + "invalid";

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }

    @Test
    void tokenShouldBeInvalidWhenMalformed() {
        assertFalse(jwtService.isTokenValid("not-a-jwt-token"));
    }
}
