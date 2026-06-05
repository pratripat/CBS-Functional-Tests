package com.banking.history.service;

import com.banking.history.dto.TransactionDtos;
import com.banking.history.entity.Transaction;
import com.banking.history.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionHistoryService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionDtos.TransactionResponse recordTransaction(
            TransactionDtos.RecordTransactionRequest request) {
        log.info("Recording transaction: {}", request.getTxnReferenceNumber());

        // Idempotency: return existing if already recorded
        return transactionRepository.findByTxnReferenceNumber(request.getTxnReferenceNumber())
                .map(this::toResponse)
                .orElseGet(() -> {
                    Transaction txn = Transaction.builder()
                            .txnReferenceNumber(request.getTxnReferenceNumber())
                            .accountNumber(request.getAccountNumber())
                            .txnType(Transaction.TxnType.valueOf(request.getTxnType()))
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .description(request.getDescription())
                            .channel(request.getChannel())
                            .counterpartyAccount(request.getCounterpartyAccount())
                            .balanceAfter(request.getBalanceAfter())
                            .status(Transaction.TxnStatus.valueOf(request.getStatus()))
                            .build();
                    return toResponse(transactionRepository.save(txn));
                });
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionPageResponse getTransactions(
            String accountNumber, int page, int size) {
        Page<Transaction> txnPage = transactionRepository
                .findByAccountNumberOrderByCreatedAtDesc(
                        accountNumber, PageRequest.of(page, size));
        return buildPageResponse(txnPage);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionPageResponse getTransactionsByDateRange(
            String accountNumber, LocalDateTime from, LocalDateTime to, int page, int size) {
        Page<Transaction> txnPage = transactionRepository
                .findByAccountNumberAndCreatedAtBetweenOrderByCreatedAtDesc(
                        accountNumber, from, to, PageRequest.of(page, size));
        return buildPageResponse(txnPage);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionResponse getTransaction(String txnRefNumber) {
        return transactionRepository.findByTxnReferenceNumber(txnRefNumber)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txnRefNumber));
    }

    private TransactionDtos.TransactionPageResponse buildPageResponse(Page<Transaction> page) {
        return TransactionDtos.TransactionPageResponse.builder()
                .transactions(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private TransactionDtos.TransactionResponse toResponse(Transaction t) {
        return TransactionDtos.TransactionResponse.builder()
                .txnReferenceNumber(t.getTxnReferenceNumber())
                .accountNumber(t.getAccountNumber())
                .txnType(t.getTxnType().name())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .description(t.getDescription())
                .channel(t.getChannel())
                .counterpartyAccount(t.getCounterpartyAccount())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus().name())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
