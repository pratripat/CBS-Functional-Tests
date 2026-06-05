package com.banking.history.controller;

import com.banking.history.dto.TransactionDtos;
import com.banking.history.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionHistoryController {

    private final TransactionHistoryService service;

    @PostMapping
    public ResponseEntity<TransactionDtos.TransactionResponse> recordTransaction(
            @RequestBody TransactionDtos.RecordTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordTransaction(request));
    }

    @GetMapping("/{txnRefNumber}")
    public ResponseEntity<TransactionDtos.TransactionResponse> getTransaction(
            @PathVariable("txnRefNumber") String txnRefNumber) {
        return ResponseEntity.ok(service.getTransaction(txnRefNumber));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<TransactionDtos.TransactionPageResponse> getTransactions(
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(service.getTransactions(accountNumber, page, size));
    }

    @GetMapping("/account/{accountNumber}/range")
    public ResponseEntity<TransactionDtos.TransactionPageResponse> getTransactionsByRange(
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(
                service.getTransactionsByDateRange(accountNumber, from, to, page, size));
    }
}
