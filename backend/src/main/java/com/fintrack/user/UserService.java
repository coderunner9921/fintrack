
package com.fintrack.user;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(
	        UserRepository userRepository,
	        PasswordEncoder passwordEncoder) {
	    this.userRepository = userRepository;
	    this.passwordEncoder = passwordEncoder;
	}

    @Transactional
    public User registerUser(String fullName, String email, String rawPassword) {

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }

        String normalizedName = fullName.trim();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException(
                    "Full name cannot exceed 100 characters.");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (!normalizedEmail.matches(
                "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$"
                        .toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Enter a valid email address.");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters.");
        }

        if (rawPassword.length() > 72) {
            throw new IllegalArgumentException(
                    "Password cannot exceed 72 characters.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User(
                normalizedName,
                normalizedEmail,
                passwordHash);

        return userRepository.save(user);
    }
    

	public User authenticateUser(String email, String rawPassword) {
	
	    if (email == null || email.isBlank()
	            || rawPassword == null || rawPassword.isEmpty()) {
	        throw new IllegalArgumentException(
	                "Invalid email or password.");
	    }
	
	    String normalizedEmail =
	            email.trim().toLowerCase(Locale.ROOT);
	
	    User user = userRepository.findByEmail(normalizedEmail)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Invalid email or password."));
	
	    if (!passwordEncoder.matches(
	            rawPassword, user.getPasswordHash())) {
	        throw new IllegalArgumentException(
	                "Invalid email or password.");
	    }
	
	    return user;
	}

}
