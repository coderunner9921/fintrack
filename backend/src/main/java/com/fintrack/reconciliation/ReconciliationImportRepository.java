package com.fintrack.reconciliation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationImportRepository
        extends JpaRepository<ReconciliationImport, UUID> {

    List<ReconciliationImport> findAllByUser_IdOrderByCreatedAtDesc(
            UUID userId);

    Optional<ReconciliationImport> findByIdAndUser_Id(
            UUID importId, UUID userId);
}
