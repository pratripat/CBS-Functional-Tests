package com.banking.history.repository;

import com.banking.history.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByAccountNumberOrderByCreatedAtDesc(String accountNumber, Pageable pageable);
    Optional<Transaction> findByTxnReferenceNumber(String txnReferenceNumber);
    Page<Transaction> findByAccountNumberAndCreatedAtBetweenOrderByCreatedAtDesc(
            String accountNumber, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
