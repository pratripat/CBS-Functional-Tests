package com.banking.transfer.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransferDtos {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransferRequest {
        @NotBlank(message = "Debit account number is required")
        private String debitAccountNumber;

        @NotBlank(message = "Credit account number is required")
        private String creditAccountNumber;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000.00", message = "Amount exceeds per-transaction limit of 10,00,000")
        private BigDecimal amount;

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        private String currency;

        private String remarks;
        private String channel;

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
        private String debitAccountNumber;
        private String creditAccountNumber;
        private LocalDateTime processedAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
    }

    // Response from CBS (core banking system mock)
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CbsTransferResponse {
        private String txnReferenceNumber;
        private String status;
        private String responseCode;
        private String description;
        private String processingDate;
    }
}
