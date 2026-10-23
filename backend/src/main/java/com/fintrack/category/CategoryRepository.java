package com.fintrack.category;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByNameIgnoreCaseAndSystemTrue(String name);

    Optional<Category> findByUser_IdAndNameIgnoreCaseAndSystemFalse(
            UUID userId,
            String name);
    
    List<Category> findAllBySystemTrueOrderByNameAsc();

    List<Category> findAllBySystemFalseAndUser_IdOrderByNameAsc(UUID userId);
}
