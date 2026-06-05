package com.banking.channel.controller;

import com.banking.channel.client.DownstreamClients;
import com.banking.channel.dto.ChannelDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ChannelController {

    private final DownstreamClients clients;

    // ── Funds Transfer ─────────────────────────────────────────────────────

    @PostMapping("/funds-transfer")
    public ResponseEntity<ChannelDtos.TransferResponse> fundsTransfer(
            @Valid @RequestBody ChannelDtos.TransferRequest request) {
        log.info("Channel: funds transfer request idempotencyKey={}",
                request.getIdempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clients.initiateTransfer(request));
    }

    // ── Account Inquiry ────────────────────────────────────────────────────

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<ChannelDtos.AccountResponse> getAccount(
            @PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(clients.getAccount(accountNumber));
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    public ResponseEntity<ChannelDtos.BalanceResponse> getBalance(
            @PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(clients.getBalance(accountNumber));
    }

    @GetMapping("/accounts/customer/{customerId}")
    public ResponseEntity<List<ChannelDtos.AccountResponse>> getAccountsByCustomer(
            @PathVariable("customerId") String customerId) {
        return ResponseEntity.ok(clients.getAccountsByCustomer(customerId));
    }

    // ── Transaction History ────────────────────────────────────────────────

    @GetMapping("/accounts/{accountNumber}/mini-statement")
    public ResponseEntity<ChannelDtos.MiniStatementResponse> getMiniStatement(
            @PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(clients.getMiniStatement(accountNumber));
    }
}
