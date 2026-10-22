
package com.fintrack.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RegisterResponse {

    private UUID id;
    private String fullName;
    private String email;
    private OffsetDateTime createdAt;

    public RegisterResponse() {
    }

    public RegisterResponse(
            UUID id,
            String fullName,
            String email,
            OffsetDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
