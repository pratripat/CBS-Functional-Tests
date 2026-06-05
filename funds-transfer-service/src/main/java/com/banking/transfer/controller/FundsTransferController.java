package com.banking.transfer.controller;

import com.banking.transfer.dto.TransferDtos;
import com.banking.transfer.service.FundsTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class FundsTransferController {

    private final FundsTransferService service;

    @PostMapping
    public ResponseEntity<TransferDtos.TransferResponse> initiateTransfer(
            @Valid @RequestBody TransferDtos.TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.initiateTransfer(request));
    }

    @GetMapping("/{txnRef}")
    public ResponseEntity<TransferDtos.TransferResponse> getTransfer(
            @PathVariable("txnRef") String txnRef) {
        return ResponseEntity.ok(service.getTransfer(txnRef));
    }
}
