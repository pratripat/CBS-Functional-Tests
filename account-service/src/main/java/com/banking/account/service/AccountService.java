package com.banking.account.service;

import com.banking.account.dto.AccountDtos;
import com.banking.account.entity.Account;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountDtos.AccountResponse getAccount(String accountNumber) {
        log.info("Fetching account: {}", accountNumber);
        Account account = findAccount(accountNumber);
        return toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountDtos.BalanceResponse getBalance(String accountNumber) {
        log.info("Fetching balance for account: {}", accountNumber);
        Account account = findAccount(accountNumber);
        return AccountDtos.BalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .availableBalance(account.getAvailableBalance())
                .currentBalance(account.getCurrentBalance())
                .currency(account.getCurrency())
                .asOfDateTime(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountResponse> getAccountsByCustomer(String customerId) {
        return accountRepository.findByCustomerId(customerId)
                .stream().map(this::toAccountResponse).toList();
    }

    @Transactional
    public AccountDtos.AccountResponse createAccount(AccountDtos.CreateAccountRequest request) {
        log.info("Creating account: {}", request.getAccountNumber());
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new IllegalArgumentException("Account already exists: " + request.getAccountNumber());
        }
        Account account = Account.builder()
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .accountType(Account.AccountType.valueOf(request.getAccountType()))
                .currency(request.getCurrency())
                .availableBalance(request.getInitialBalance())
                .currentBalance(request.getInitialBalance())
                .status(Account.AccountStatus.ACTIVE)
                .branchCode(request.getBranchCode())
                .customerId(request.getCustomerId())
                .build();
        return toAccountResponse(accountRepository.save(account));
    }

    @Transactional
    public void updateBalance(AccountDtos.UpdateBalanceRequest request) {
        log.info("Updating balance for account: {} by {}", request.getAccountNumber(), request.getAmount());
        Account account = findAccount(request.getAccountNumber());

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getStatus());
        }

        BigDecimal newBalance = account.getAvailableBalance().add(request.getAmount());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        }
        account.setAvailableBalance(newBalance);
        account.setCurrentBalance(account.getCurrentBalance().add(request.getAmount()));
        accountRepository.save(account);
    }

    @Transactional
    public AccountDtos.AccountResponse updateStatus(String accountNumber, String status) {
        Account account = findAccount(accountNumber);
        account.setStatus(Account.AccountStatus.valueOf(status));
        return toAccountResponse(accountRepository.save(account));
    }

    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private AccountDtos.AccountResponse toAccountResponse(Account account) {
        return AccountDtos.AccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().name())
                .currency(account.getCurrency())
                .availableBalance(account.getAvailableBalance())
                .currentBalance(account.getCurrentBalance())
                .status(account.getStatus().name())
                .branchCode(account.getBranchCode())
                .customerId(account.getCustomerId())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
