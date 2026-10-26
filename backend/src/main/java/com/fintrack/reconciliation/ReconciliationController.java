package com.fintrack.reconciliation;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fintrack.reconciliation.dto.ReconciliationImportResponse;
import com.fintrack.reconciliation.dto.StatementRowResponse;

@RestController
@RequestMapping("/api/reconciliation/imports")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(
            ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReconciliationImportResponse> importStatement(
            Authentication authentication,
            @RequestParam UUID accountId,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new CsvImportValidationException(
                    "Please upload a non-empty CSV file.");
        }

        UUID userId = UUID.fromString(authentication.getName());

        ReconciliationImportResponse response =
                reconciliationService.importStatement(
                        userId,
                        accountId,
                        file.getOriginalFilename(),
                        file.getInputStream());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ReconciliationImportResponse> getImports(
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        return reconciliationService.getImports(userId);
    }

    @GetMapping("/{importId}")
    public ReconciliationImportResponse getImport(
            Authentication authentication,
            @PathVariable UUID importId) {

        UUID userId = UUID.fromString(authentication.getName());

        return reconciliationService.getImport(userId, importId);
    }

    @GetMapping("/{importId}/rows")
    public List<StatementRowResponse> getRows(
            Authentication authentication,
            @PathVariable UUID importId) {

        UUID userId = UUID.fromString(authentication.getName());

        return reconciliationService.getRows(userId, importId);
    }

    @ExceptionHandler(CsvImportValidationException.class)
    public ResponseEntity<Map<String, String>> handleCsvValidation(
            CsvImportValidationException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidArgument(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage()));
    }
}
