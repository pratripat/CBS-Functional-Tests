package com.banking.transfer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "funds_transfers", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_txn_ref", columnList = "txn_reference_number")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FundsTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "txn_reference_number", unique = true)
    private String txnReferenceNumber;

    @Column(name = "debit_account_number", nullable = false)
    private String debitAccountNumber;

    @Column(name = "credit_account_number", nullable = false)
    private String creditAccountNumber;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "channel", length = 30)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TransferStatus {
        INITIATED, PROCESSING, SUCCESS, FAILED, DUPLICATE
    }
}
