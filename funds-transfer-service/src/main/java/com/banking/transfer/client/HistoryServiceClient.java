package com.banking.transfer.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HistoryServiceClient {

    private final RestTemplate restTemplate;

    @Value("${history-service.base-url}")
    private String historyServiceUrl;

    public void recordTransaction(String txnRef, String accountNumber,
                                   String txnType, BigDecimal amount,
                                   String currency, String description,
                                   String channel, String counterpartyAccount,
                                   BigDecimal balanceAfter, String status) {
        try {
            Map<String, Object> request = Map.of(
                    "txnReferenceNumber", txnRef,
                    "accountNumber", accountNumber,
                    "txnType", txnType,
                    "amount", amount,
                    "currency", currency,
                    "description", description != null ? description : "",
                    "channel", channel != null ? channel : "API",
                    "counterpartyAccount", counterpartyAccount != null ? counterpartyAccount : "",
                    "balanceAfter", balanceAfter != null ? balanceAfter : BigDecimal.ZERO,
                    "status", status
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(
                    historyServiceUrl + "/api/v1/transactions",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    Void.class);
            log.debug("Transaction {} recorded in history service", txnRef);
        } catch (Exception e) {
            // Non-critical — log and continue; do not fail the transfer
            log.error("Failed to record transaction {} in history service: {}", txnRef, e.getMessage());
        }
    }
}
