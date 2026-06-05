package com.banking.account.dto;

import com.banking.account.entity.Account;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountDtos {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AccountResponse {
        private String accountNumber;
        private String accountName;
        private String accountType;
        private String currency;
        private BigDecimal availableBalance;
        private BigDecimal currentBalance;
        private String status;
        private String branchCode;
        private String customerId;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BalanceResponse {
        private String accountNumber;
        private BigDecimal availableBalance;
        private BigDecimal currentBalance;
        private String currency;
        private LocalDateTime asOfDateTime;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateAccountRequest {
        private String accountNumber;
        private String accountName;
        private String accountType;
        private String currency;
        private BigDecimal initialBalance;
        private String branchCode;
        private String customerId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateBalanceRequest {
        private String accountNumber;
        private BigDecimal amount;   // positive = credit, negative = debit
        private String description;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
    }
}
