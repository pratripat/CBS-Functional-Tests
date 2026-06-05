package com.banking.history.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionDtos {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecordTransactionRequest {
        private String txnReferenceNumber;
        private String accountNumber;
        private String txnType;       // CR or DR
        private BigDecimal amount;
        private String currency;
        private String description;
        private String channel;
        private String counterpartyAccount;
        private BigDecimal balanceAfter;
        private String status;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransactionResponse {
        private String txnReferenceNumber;
        private String accountNumber;
        private String txnType;
        private BigDecimal amount;
        private String currency;
        private String description;
        private String channel;
        private String counterpartyAccount;
        private BigDecimal balanceAfter;
        private String status;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransactionPageResponse {
        private List<TransactionResponse> transactions;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
    }
}
