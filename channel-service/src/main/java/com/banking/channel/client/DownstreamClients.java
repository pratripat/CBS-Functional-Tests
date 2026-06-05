package com.banking.channel.client;

import com.banking.channel.dto.ChannelDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DownstreamClients {

    private final RestTemplate restTemplate;

    @Value("${services.account-service.url}")
    private String accountServiceUrl;

    @Value("${services.funds-transfer-service.url}")
    private String fundsTransferServiceUrl;

    @Value("${services.transaction-history-service.url}")
    private String transactionHistoryServiceUrl;

    // ── Account Service ────────────────────────────────────────────────────

    public ChannelDtos.AccountResponse getAccount(String accountNumber) {
        try {
            return restTemplate.getForObject(
                    accountServiceUrl + "/api/v1/accounts/" + accountNumber,
                    ChannelDtos.AccountResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new DownstreamException("ACCOUNT_NOT_FOUND",
                    "Account not found: " + accountNumber, 404);
        }
    }

    public ChannelDtos.BalanceResponse getBalance(String accountNumber) {
        try {
            return restTemplate.getForObject(
                    accountServiceUrl + "/api/v1/accounts/" + accountNumber + "/balance",
                    ChannelDtos.BalanceResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new DownstreamException("ACCOUNT_NOT_FOUND",
                    "Account not found: " + accountNumber, 404);
        }
    }

    public List<ChannelDtos.AccountResponse> getAccountsByCustomer(String customerId) {
        return restTemplate.exchange(
                accountServiceUrl + "/api/v1/accounts/customer/" + customerId,
                HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ChannelDtos.AccountResponse>>() {})
                .getBody();
    }

    // ── Funds Transfer Service ─────────────────────────────────────────────

    public ChannelDtos.TransferResponse initiateTransfer(ChannelDtos.TransferRequest request) {
        Map<String, Object> body = Map.of(
                "debitAccountNumber", request.getDebitAccountNumber(),
                "creditAccountNumber", request.getCreditAccountNumber(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "remarks", request.getRemarks() != null ? request.getRemarks() : "",
                "channel", "CHANNEL",
                "idempotencyKey", request.getIdempotencyKey()
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
        ResponseEntity<ChannelDtos.TransferResponse> response = restTemplate.exchange(
                fundsTransferServiceUrl + "/api/v1/transfers",
                HttpMethod.POST, entity, ChannelDtos.TransferResponse.class);
        return response.getBody();
    }

    // ── Transaction History Service ────────────────────────────────────────

    public ChannelDtos.MiniStatementResponse getMiniStatement(String accountNumber) {
        // Fetch last 5 transactions
        String url = transactionHistoryServiceUrl
                + "/api/v1/transactions/account/" + accountNumber + "?page=0&size=5";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> page = restTemplate.getForObject(url, Map.class);
            if (page == null) return ChannelDtos.MiniStatementResponse.builder()
                    .accountNumber(accountNumber).transactions(List.of()).totalCount(0).build();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> txns = (List<Map<String, Object>>) page.get("transactions");
            List<ChannelDtos.TransactionItem> items = txns == null ? List.of() : txns.stream()
                    .map(t -> ChannelDtos.TransactionItem.builder()
                            .txnReferenceNumber((String) t.get("txnReferenceNumber"))
                            .txnType((String) t.get("txnType"))
                            .amount(new java.math.BigDecimal(t.get("amount").toString()))
                            .currency((String) t.get("currency"))
                            .description((String) t.get("description"))
                            .build())
                    .toList();
            return ChannelDtos.MiniStatementResponse.builder()
                    .accountNumber(accountNumber)
                    .transactions(items)
                    .totalCount(items.size())
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch mini statement for {}: {}", accountNumber, e.getMessage());
            return ChannelDtos.MiniStatementResponse.builder()
                    .accountNumber(accountNumber).transactions(List.of()).totalCount(0).build();
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    public static class DownstreamException extends RuntimeException {
        private final String errorCode;
        private final int httpStatus;
        public DownstreamException(String errorCode, String msg, int httpStatus) {
            super(msg);
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }
        public String getErrorCode() { return errorCode; }
        public int getHttpStatus() { return httpStatus; }
    }
}
