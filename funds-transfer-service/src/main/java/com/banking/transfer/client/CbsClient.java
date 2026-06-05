package com.banking.transfer.client;

import com.banking.transfer.dto.TransferDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CbsClient {

    private final RestTemplate restTemplate;

    @Value("${cbs.base-url}")
    private String cbsBaseUrl;

    @Value("${cbs.timeout-ms:5000}")
    private int timeoutMs;

    public TransferDtos.CbsTransferResponse executeFundsTransfer(
            TransferDtos.TransferRequest request, String txnRef) {

        String url = cbsBaseUrl.endsWith("/") ? cbsBaseUrl + "funds-transfer" : cbsBaseUrl + "/funds-transfer";
        log.info("Calling CBS funds transfer: {} → {}", txnRef, url);

        Map<String, Object> cbsRequest = Map.of(
                "txnReferenceNumber", txnRef,
                "debitAccountNumber", request.getDebitAccountNumber(),
                "creditAccountNumber", request.getCreditAccountNumber(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "remarks", request.getRemarks() != null ? request.getRemarks() : "",
                "channel", request.getChannel() != null ? request.getChannel() : "API"
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cbsRequest, headers);

            ResponseEntity<TransferDtos.CbsTransferResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity,
                            TransferDtos.CbsTransferResponse.class);

            log.info("CBS response for {}: {}", txnRef,
                    response.getBody() != null ? response.getBody().getResponseCode() : "null");
            return response.getBody();

        } catch (ResourceAccessException e) {
            log.error("CBS timeout for txn {}: {}", txnRef, e.getMessage());
            throw new CbsTimeoutException("CBS did not respond within " + timeoutMs + "ms");
        } catch (HttpClientErrorException e) {
            log.error("CBS client error for txn {}: {}", txnRef, e.getMessage());
            throw new CbsException("CBS returned error: " + e.getStatusCode());
        }
    }

    public static class CbsTimeoutException extends RuntimeException {
        public CbsTimeoutException(String msg) { super(msg); }
    }

    public static class CbsException extends RuntimeException {
        public CbsException(String msg) { super(msg); }
    }
}
