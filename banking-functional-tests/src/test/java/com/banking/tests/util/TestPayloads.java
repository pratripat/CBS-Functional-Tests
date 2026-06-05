package com.banking.tests.util;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Factory methods for building JSON request bodies used across test suites.
 */
public final class TestPayloads {
    private TestPayloads() {}

    public static String fundsTransferRequest(String debitAcc, String creditAcc,
                                               BigDecimal amount, String currency,
                                               String idempotencyKey) {
        return """
                {
                  "debitAccountNumber": "%s",
                  "creditAccountNumber": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "remarks": "Integration test transfer",
                  "idempotencyKey": "%s"
                }
                """.formatted(debitAcc, creditAcc, amount.toPlainString(), currency, idempotencyKey);
    }

    public static String createAccountRequest(String accountNumber, String accountName,
                                               String type, String currency,
                                               BigDecimal initialBalance,
                                               String branchCode, String customerId) {
        return """
                {
                  "accountNumber": "%s",
                  "accountName": "%s",
                  "accountType": "%s",
                  "currency": "%s",
                  "initialBalance": %s,
                  "branchCode": "%s",
                  "customerId": "%s"
                }
                """.formatted(accountNumber, accountName, type, currency,
                initialBalance.toPlainString(), branchCode, customerId);
    }

    public static String updateBalanceRequest(BigDecimal amount, String description) {
        return """
                {
                  "amount": %s,
                  "description": "%s"
                }
                """.formatted(amount.toPlainString(), description);
    }

    /** Generate a unique test account number */
    public static String randomAccount() {
        return "ACC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    /** Generate a unique idempotency key */
    public static String randomKey() {
        return UUID.randomUUID().toString();
    }
}
