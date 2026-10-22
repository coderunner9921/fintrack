package com.fintrack.account;

import java.util.List;
import java.util.UUID;
import com.fintrack.account.dto.AccountResponse;
import com.fintrack.account.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> getAccounts(
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        return accountService.getAccountsForUser(userId)
                .stream()
                .map(account -> new AccountResponse(
                        account,
                        accountService.getCurrentBalance(userId, account.getId())))
                .toList();
    }
    

	@GetMapping("/{accountId}")
	public AccountResponse getAccount(
	        @PathVariable UUID accountId,
	        Authentication authentication) {
	
	    UUID userId = UUID.fromString(authentication.getName());
	
	    try {
	        Account account = accountService.getAccountForUser(
	                userId, accountId);
	
	        return new AccountResponse(
	                account,
	                accountService.getCurrentBalance(userId, account.getId()));
	
	    } catch (IllegalArgumentException exception) {
	        throw new ResponseStatusException(
	                HttpStatus.NOT_FOUND,
	                "Account not found.");
	    }
	}


    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(
            HttpStatus.CREATED)
    public AccountResponse createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        try {
            Account account = accountService.createAccount(
                    userId,
                    request.getName(),
                    request.getAccountType(),
                    request.getCurrency(),
                    request.getOpeningBalance());

            return new AccountResponse(
                    account,
                    accountService.getCurrentBalance(userId, account.getId()));

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage());
        }
    }
}
