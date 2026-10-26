package com.fintrack.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementRowRepository
        extends JpaRepository<StatementRow, UUID> {

    List<StatementRow> findAllByReconciliationImport_IdOrderByRowNumberAsc(
            UUID importId);

    List<StatementRow> findAllByReconciliationImport_IdAndStatus(
            UUID importId, StatementRow.RowStatus status);

    long countByReconciliationImport_IdAndStatus(
            UUID importId, StatementRow.RowStatus status);
}
