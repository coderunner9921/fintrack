
package com.fintrack.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerUserShouldSaveUserWithHashedPassword() {
        when(userRepository.existsByEmail("alan@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("StrongPass123"))
                .thenReturn("$2a$12$exampleHashedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = userService.registerUser(
                "Alan Cardoza",
                "Alan@Example.com",
                "StrongPass123");

        assertEquals("Alan Cardoza", savedUser.getFullName());
        assertEquals("alan@example.com", savedUser.getEmail());

        // The User class intentionally does not expose its password hash.
        verify(passwordEncoder).encode("StrongPass123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUserShouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("alan@example.com"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(
                        "Alan Cardoza",
                        "alan@example.com",
                        "StrongPass123"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUserShouldRejectInvalidEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(
                        "Alan Cardoza",
                        "not-an-email",
                        "StrongPass123"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUserShouldRejectShortPassword() {
        when(userRepository.existsByEmail("alan@example.com"))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(
                        "Alan Cardoza",
                        "alan@example.com",
                        "short"));

        verify(passwordEncoder, never()).encode(any(String.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
