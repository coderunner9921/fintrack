package com.fintrack.category.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fintrack.category.Category;

public class CategoryResponse {

    private final UUID id;
    private final String name;
    private final String categoryType;
    private final boolean system;
    private final OffsetDateTime createdAt;

    public CategoryResponse(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.categoryType = category.getCategoryType();
        this.system = category.isSystem();
        this.createdAt = category.getCreatedAt();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public boolean isSystem() {
        return system;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}