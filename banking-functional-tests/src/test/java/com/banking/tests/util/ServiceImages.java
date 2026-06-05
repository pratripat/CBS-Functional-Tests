package com.banking.tests.util;

/**
 * Centralised Docker image names for all platform services.
 * Update these when you tag and push new images.
 */
public final class ServiceImages {
    private ServiceImages() {}

    public static final String CHANNEL_SERVICE          = "banking/channel-service:latest";
    public static final String ACCOUNT_SERVICE          = "banking/account-service:latest";
    public static final String FUNDS_TRANSFER_SERVICE   = "banking/funds-transfer-service:latest";
    public static final String TRANSACTION_HISTORY_SVC  = "banking/transaction-history-service:latest";
    public static final String POSTGRES                 = "postgres:15-alpine";
}
