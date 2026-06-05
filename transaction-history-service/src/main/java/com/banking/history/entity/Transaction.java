package com.banking.history.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_account_number", columnList = "account_number"),
    @Index(name = "idx_txn_ref", columnList = "txn_reference_number")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_reference_number", nullable = false, unique = true)
    private String txnReferenceNumber;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "txn_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private TxnType txnType;   // CR / DR

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Column(name = "channel", length = 30)
    private String channel;

    @Column(name = "counterparty_account")
    private String counterpartyAccount;

    @Column(name = "balance_after", precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TxnStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TxnType   { CR, DR }
    public enum TxnStatus { SUCCESS, FAILED, PENDING, REVERSED }
}
