package com.banking.tests.stubs;

import java.math.BigDecimal;

/**
 * WireMock response body JSON for all CBS (Core Banking System) operations.
 * Shared across all test suites to keep stub payloads consistent.
 */
public final class CbsStubs {
    private CbsStubs() {}

    // ── Funds Transfer ─────────────────────────────────────────────────────

    public static String fundsTransferSuccess(String txnRef) {
        return """
                {
                  "txnReferenceNumber": "%s",
                  "status": "SUCCESS",
                  "responseCode": "00",
                  "description": "Transaction completed successfully",
                  "processingDate": "2024-03-15"
                }
                """.formatted(txnRef);
    }

    public static String fundsTransferInsufficientFunds() {
        return """
                {
                  "txnReferenceNumber": null,
                  "status": "FAILED",
                  "responseCode": "51",
                  "description": "Insufficient funds in debit account"
                }
                """;
    }

    public static String fundsTransferInvalidAccount() {
        return """
                {
                  "txnReferenceNumber": null,
                  "status": "FAILED",
                  "responseCode": "14",
                  "description": "Invalid account number"
                }
                """;
    }

    public static String fundsTransferCbsUnavailable() {
        return """
                {
                  "responseCode": "91",
                  "description": "CBS temporarily unavailable"
                }
                """;
    }
}
