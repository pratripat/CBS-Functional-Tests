package com.banking.transfer.repository;

import com.banking.transfer.entity.FundsTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FundsTransferRepository extends JpaRepository<FundsTransfer, Long> {
    Optional<FundsTransfer> findByIdempotencyKey(String idempotencyKey);
    Optional<FundsTransfer> findByTxnReferenceNumber(String txnReferenceNumber);
}
