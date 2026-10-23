
package com.fintrack.category;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.Authentication;

import com.fintrack.category.dto.CategoryResponse;
import com.fintrack.category.dto.CreateCategoryRequest;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> getCategories(Authentication authentication) {
        UUID userId = getUserId(authentication);

        return categoryService.getCategories(userId)
                .stream()
                .map(CategoryResponse::new)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(
            Authentication authentication,
            @Valid @RequestBody CreateCategoryRequest request) {

        UUID userId = getUserId(authentication);

        Category category = categoryService.createCategory(
                userId, request.getName(), request.getCategoryType());

        return new CategoryResponse(category);
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse updateCategory(
            Authentication authentication,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CreateCategoryRequest request) {

        UUID userId = getUserId(authentication);

        Category category = categoryService.updateCategory(
                userId, categoryId, request.getName(), request.getCategoryType());

        return new CategoryResponse(category);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            Authentication authentication,
            @PathVariable UUID categoryId) {

        UUID userId = getUserId(authentication);
        categoryService.deleteCategory(userId, categoryId);
    }

    private UUID getUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
