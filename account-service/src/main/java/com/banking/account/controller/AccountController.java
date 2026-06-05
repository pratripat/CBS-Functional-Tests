package com.banking.account.controller;

import com.banking.account.dto.AccountDtos;
import com.banking.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountDtos.AccountResponse> getAccount(
            @PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<AccountDtos.BalanceResponse> getBalance(
            @PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountDtos.AccountResponse>> getAccountsByCustomer(
            @PathVariable("customerId") String customerId) {
        return ResponseEntity.ok(accountService.getAccountsByCustomer(customerId));
    }

    @PostMapping
    public ResponseEntity<AccountDtos.AccountResponse> createAccount(
            @RequestBody AccountDtos.CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request));
    }

    @PutMapping("/{accountNumber}/balance")
    public ResponseEntity<Void> updateBalance(
            @PathVariable("accountNumber") String accountNumber,
            @RequestBody AccountDtos.UpdateBalanceRequest request) {
        request.setAccountNumber(accountNumber);
        accountService.updateBalance(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountNumber}/status")
    public ResponseEntity<AccountDtos.AccountResponse> updateStatus(
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam("status") String status) {
        return ResponseEntity.ok(accountService.updateStatus(accountNumber, status));
    }
}
