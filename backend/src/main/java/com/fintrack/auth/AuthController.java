
package com.fintrack.auth;

import com.fintrack.user.User;
import com.fintrack.user.UserService;
import com.fintrack.user.dto.LoginRequest;
import com.fintrack.user.dto.LoginResponse;
import com.fintrack.user.dto.RegisterRequest;
import com.fintrack.user.dto.RegisterResponse;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            @Value("${jwt.expiration-ms}") long expirationMs) {

        this.userService = userService;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
    }

    @PostMapping("/register")
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request) {

        try {
            User user = userService.registerUser(
                    request.getFullName(),
                    request.getEmail(),
                    request.getPassword());

            return new RegisterResponse(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getCreatedAt());

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage());
        }
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        try {
            User user = userService.authenticateUser(
                    request.getEmail(),
                    request.getPassword());

            String token = jwtService.generateToken(user.getId());

            return new LoginResponse(token, expirationMs);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }
    }
}
