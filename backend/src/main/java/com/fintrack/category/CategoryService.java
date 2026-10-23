
package com.fintrack.category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fintrack.transaction.FinancialTransactionRepository;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FinancialTransactionRepository transactionRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            FinancialTransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<Category> getCategories(UUID userId) {
        List<Category> categories = new ArrayList<>();

        categories.addAll(
                categoryRepository.findAllBySystemTrueOrderByNameAsc());

        categories.addAll(
                categoryRepository.findAllBySystemFalseAndUser_IdOrderByNameAsc(userId));

        categories.sort(
                Comparator.comparing(
                        Category::getName,
                        String.CASE_INSENSITIVE_ORDER));

        return categories;
    }

    @Transactional
    public Category createCategory(UUID userId, String name, String categoryType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        String normalizedName = normalizeName(name);
        String normalizedType = normalizeType(categoryType);

        ensureNameAvailable(userId, normalizedName, null);

        Category category = new Category(
                user, normalizedName, normalizedType, false);

        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(
            UUID userId, UUID categoryId, String name, String categoryType) {

        Category category = getOwnedCustomCategory(userId, categoryId);

        String normalizedName = normalizeName(name);
        String normalizedType = normalizeType(categoryType);

        ensureNameAvailable(userId, normalizedName, categoryId);

        if (transactionRepository.existsByCategoryEntity_Id(categoryId)
                && !category.getCategoryType().equals(normalizedType)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category type cannot be changed after transactions use this category.");
        }

        category.setName(normalizedName);
        category.setCategoryType(normalizedType);

        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(UUID userId, UUID categoryId) {
        Category category = getOwnedCustomCategory(userId, categoryId);

        if (transactionRepository.existsByCategoryEntity_Id(categoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This category cannot be deleted because transactions use it.");
        }

        categoryRepository.delete(category);
    }

    private Category getOwnedCustomCategory(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found."));

        if (category.isSystem()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "System categories cannot be changed.");
        }

        if (category.getUser() == null
                || !category.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Category not found.");
        }

        return category;
    }

    private void ensureNameAvailable(
            UUID userId, String name, UUID excludedCategoryId) {

        boolean systemNameExists = categoryRepository
                .findByNameIgnoreCaseAndSystemTrue(name)
                .isPresent();

        boolean customNameExists = categoryRepository
                .findByUser_IdAndNameIgnoreCaseAndSystemFalse(userId, name)
                .filter(category -> !category.getId().equals(excludedCategoryId))
                .isPresent();

        if (systemNameExists || customNameExists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name already exists.");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Category name is required.");
        }

        String normalized = name.trim();

        if (normalized.length() > 80) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category name cannot exceed 80 characters.");
        }

        return normalized;
    }

    private String normalizeType(String categoryType) {
        if (categoryType == null || categoryType.isBlank()) {
            return "BOTH";
        }

        String normalized = categoryType.trim().toUpperCase(Locale.ROOT);

        if (!List.of("INCOME", "EXPENSE", "BOTH").contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category type must be INCOME, EXPENSE, or BOTH.");
        }

        return normalized;
    }
}
