package com.fintrack.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionResponse> getTransactions(
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        try {
            return transactionService.getTransactionsForUser(userId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found.");
        }
    }

    @PostMapping
    public org.springframework.http.ResponseEntity<TransactionResponse>
            createTransaction(
                    @Valid @RequestBody CreateTransactionRequest request,
                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                    Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        try {
            FinancialTransaction transaction =
                    transactionService.createTransaction(
                            userId, request, idempotencyKey);

            return org.springframework.http.ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new TransactionResponse(transaction));

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
    
    @PostMapping("/{transactionId}/reverse")
    public org.springframework.http.ResponseEntity<TransactionResponse>
            reverseTransaction(
                    @PathVariable UUID transactionId,
                    Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        try {
            FinancialTransaction reversal =
                    transactionService.reverseTransaction(
                            userId, transactionId);

            return org.springframework.http.ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new TransactionResponse(reversal));

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
