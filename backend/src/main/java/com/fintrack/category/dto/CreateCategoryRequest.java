package com.fintrack.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required.")
    @Size(max = 80, message = "Category name cannot exceed 80 characters.")
    private String name;

    @NotBlank(message = "Category type is required.")
    @Pattern(
        regexp = "(?i)INCOME|EXPENSE|BOTH",
        message = "Category type must be INCOME, EXPENSE, or BOTH."
    )
    private String categoryType = "BOTH";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(String categoryType) {
        this.categoryType = categoryType;
    }
}