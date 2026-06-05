package com.banking.channel.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChannelDtos {

    // ── Funds Transfer ─────────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransferRequest {
        @NotBlank(message = "Debit account number is required")
        private String debitAccountNumber;
        @NotBlank(message = "Credit account number is required")
        private String creditAccountNumber;
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000000.00", message = "Amount exceeds per-transaction limit")
        private BigDecimal amount;
        @NotBlank(message = "Currency is required")
        private String currency;
        private String remarks;
        @NotBlank(message = "Idempotency key is required")
        private String idempotencyKey;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransferResponse {
        private String txnReferenceNumber;
        private String status;
        private String responseCode;
        private String description;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime processedAt;
    }

    // ── Account Inquiry ────────────────────────────────────────────────────

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
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BalanceResponse {
        private String accountNumber;
        private BigDecimal availableBalance;
        private BigDecimal currentBalance;
        private String currency;
        private LocalDateTime asOfDateTime;
    }

    // ── Transaction History ────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MiniStatementResponse {
        private String accountNumber;
        private java.util.List<TransactionItem> transactions;
        private int totalCount;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransactionItem {
        private String txnReferenceNumber;
        private String txnType;
        private BigDecimal amount;
        private String currency;
        private String description;
        private BigDecimal balanceAfter;
        private LocalDateTime createdAt;
    }

    // ── Error ──────────────────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
    }
}
