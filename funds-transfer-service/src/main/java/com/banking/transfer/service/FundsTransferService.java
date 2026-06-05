package com.banking.transfer.service;

import com.banking.transfer.client.CbsClient;
import com.banking.transfer.client.HistoryServiceClient;
import com.banking.transfer.dto.TransferDtos;
import com.banking.transfer.entity.FundsTransfer;
import com.banking.transfer.repository.FundsTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundsTransferService {

    private final FundsTransferRepository repository;
    private final CbsClient cbsClient;
    private final HistoryServiceClient historyClient;

    @Transactional
    public TransferDtos.TransferResponse initiateTransfer(TransferDtos.TransferRequest request) {
        log.info("Initiating transfer: idempotencyKey={}", request.getIdempotencyKey());

        // ── Idempotency check ──────────────────────────────────────────────
        // Synchronize to avoid a race where two concurrent requests both miss the
        // idempotency lookup and both execute a transfer (tests run single JVM).
        synchronized (this) {
            return repository.findByIdempotencyKey(request.getIdempotencyKey())
                    .map(existing -> {
                        log.info("Duplicate request detected for idempotency key: {}",
                                request.getIdempotencyKey());
                        return toResponse(existing);
                    })
                    .orElseGet(() -> executeNewTransfer(request));
        }
    }

    private TransferDtos.TransferResponse executeNewTransfer(TransferDtos.TransferRequest request) {
        String txnRef = generateTxnRef();

        // Persist in INITIATED state first (for audit trail)
        FundsTransfer transfer = repository.save(FundsTransfer.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .txnReferenceNumber(txnRef)
                .debitAccountNumber(request.getDebitAccountNumber())
                .creditAccountNumber(request.getCreditAccountNumber())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .remarks(request.getRemarks())
                .channel(request.getChannel())
                .status(FundsTransfer.TransferStatus.PROCESSING)
                .build());

        try {
            // ── Call CBS ───────────────────────────────────────────────────
            TransferDtos.CbsTransferResponse cbsResponse =
                    cbsClient.executeFundsTransfer(request, txnRef);

            if (cbsResponse == null) {
                throw new RuntimeException("Empty response from CBS");
            }

            boolean success = "SUCCESS".equals(cbsResponse.getStatus())
                    || "00".equals(cbsResponse.getResponseCode());

            // ── Update transfer record ─────────────────────────────────────
            transfer.setStatus(success
                    ? FundsTransfer.TransferStatus.SUCCESS
                    : FundsTransfer.TransferStatus.FAILED);
            transfer.setResponseCode(cbsResponse.getResponseCode());
            transfer.setErrorMessage(success ? null : cbsResponse.getDescription());
            repository.save(transfer);

            // ── Record in history service (best-effort) ────────────────────
            if (success) {
                historyClient.recordTransaction(
                        txnRef, request.getDebitAccountNumber(), "DR",
                        request.getAmount(), request.getCurrency(),
                        request.getRemarks(), request.getChannel(),
                        request.getCreditAccountNumber(), null, "SUCCESS");
                historyClient.recordTransaction(
                        txnRef + "-CR", request.getCreditAccountNumber(), "CR",
                        request.getAmount(), request.getCurrency(),
                        request.getRemarks(), request.getChannel(),
                        request.getDebitAccountNumber(), null, "SUCCESS");
            }

            if (!success) {
                String errorCode = mapCbsResponseToErrorCode(cbsResponse.getResponseCode());
                throw new TransferFailedException(errorCode, cbsResponse.getDescription());
            }

            return toResponse(transfer);

        } catch (CbsClient.CbsTimeoutException e) {
            transfer.setStatus(FundsTransfer.TransferStatus.FAILED);
            transfer.setResponseCode("68");
            transfer.setErrorMessage("CBS_TIMEOUT");
            repository.save(transfer);
            throw e;
        } catch (TransferFailedException e) {
            throw e;
        } catch (Exception e) {
            transfer.setStatus(FundsTransfer.TransferStatus.FAILED);
            transfer.setErrorMessage(e.getMessage());
            repository.save(transfer);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public TransferDtos.TransferResponse getTransfer(String txnRef) {
        return repository.findByTxnReferenceNumber(txnRef)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Transfer not found: " + txnRef));
    }

    private String generateTxnRef() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String mapCbsResponseToErrorCode(String cbsCode) {
        if (cbsCode == null) return "CBS_ERROR";
        return switch (cbsCode) {
            case "51" -> "INSUFFICIENT_FUNDS";
            case "14" -> "INVALID_ACCOUNT";
            case "62" -> "ACCOUNT_DORMANT";
            case "91" -> "CBS_UNAVAILABLE";
            case "94" -> "DUPLICATE_TRANSACTION";
            case "96" -> "CBS_SYSTEM_ERROR";
            default   -> "CBS_ERROR_" + cbsCode;
        };
    }

    private TransferDtos.TransferResponse toResponse(FundsTransfer t) {
        return TransferDtos.TransferResponse.builder()
                .txnReferenceNumber(t.getTxnReferenceNumber())
                .status(t.getStatus().name())
                .responseCode(t.getResponseCode())
                .description(t.getErrorMessage())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .debitAccountNumber(t.getDebitAccountNumber())
                .creditAccountNumber(t.getCreditAccountNumber())
                .processedAt(t.getUpdatedAt())
                .build();
    }

    public static class TransferFailedException extends RuntimeException {
        private final String errorCode;
        public TransferFailedException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        public String getErrorCode() { return errorCode; }
    }
}
