package com.banking.tests.suites;

import com.banking.testframework.container.TomcatServiceComponent;
import com.banking.testframework.test.*;
import com.banking.tests.util.PostgresComponent;
import com.banking.tests.util.ServiceImages;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Transaction History Service. Fix: TH-006 now
 * expects 404 (after adding GlobalExceptionHandler). Fix: aliases use no
 * hyphens.
 */
public class TransactionHistoryServiceSuite extends AbstractTestSuiteDefinition {

    public static final String HISTORY_SERVICE = "transactionhistoryservice";
    // Use alphanumeric account number — no hyphens
    private static final String TEST_ACCOUNT = "HISTACC001";

    public TransactionHistoryServiceSuite() {
        addComponent(PostgresComponent.create());

        addComponent(TomcatServiceComponent.builder(HISTORY_SERVICE,
                ServiceImages.TRANSACTION_HISTORY_SVC)
                .port(8083)
                .healthCheckPath("/actuator/health")
                .dependsOn(PostgresComponent.ALIAS)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("TH-001").name("Record a debit transaction")
                .tags(List.of("smoke")).testCase(this::testRecordDebitTransaction).build());

        addTest(TestCaseDefinition.builder()
                .id("TH-002").name("Record a credit transaction")
                .tags(List.of("smoke")).testCase(this::testRecordCreditTransaction).build());

        addTest(TestCaseDefinition.builder()
                .id("TH-003").name("Get transaction by reference number")
                .tags(List.of("smoke")).dependsOn(List.of("TH-001"))
                .testCase(this::testGetTransaction).build());

        addTest(TestCaseDefinition.builder()
                .id("TH-004").name("Get paginated transaction history for account")
                .tags(List.of("regression")).dependsOn(List.of("TH-001", "TH-002"))
                .testCase(this::testGetTransactionHistory).build());

        addTest(TestCaseDefinition.builder()
                .id("TH-005").name("Record idempotency — duplicate txnRef returns existing record")
                .tags(List.of("regression")).testCase(this::testRecordIdempotency).build());

        addTest(TestCaseDefinition.builder()
                .id("TH-006").name("Get unknown transaction returns 404")
                .tags(List.of("regression")).testCase(this::testGetUnknownTransaction).build());
    }

    @Override
    public String getSuiteName() {
        return "transaction-history-service-suite";
    }

    private String buildRecordRequest(String txnRef, String accountNumber,
            String txnType, double amount) {
        return """
                {
                  "txnReferenceNumber": "%s",
                  "accountNumber": "%s",
                  "txnType": "%s",
                  "amount": %.2f,
                  "currency": "INR",
                  "description": "Integration test transaction",
                  "channel": "API",
                  "counterpartyAccount": "OTHERACC001",
                  "balanceAfter": 45000.00,
                  "status": "SUCCESS"
                }
                """.formatted(txnRef, accountNumber, txnType, amount);
    }

    private String randomRef(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private TestResult testRecordDebitTransaction(TestContext ctx) throws Exception {
        String txnRef = randomRef("TXNDR");
        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions",
                buildRecordRequest(txnRef, TEST_ACCOUNT, "DR", 5000.00));

        assertThat(response.code()).isEqualTo(201);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("txnReferenceNumber").asText()).isEqualTo(txnRef);
        assertThat(body.path("txnType").asText()).isEqualTo("DR");
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

        ctx.recordMetadata("debitTxnRef", txnRef);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testRecordCreditTransaction(TestContext ctx) throws Exception {
        String txnRef = randomRef("TXNCR");
        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions",
                buildRecordRequest(txnRef, TEST_ACCOUNT, "CR", 10000.00));

        assertThat(response.code()).isEqualTo(201);
        assertThat(response.bodyAsJson().path("txnType").asText()).isEqualTo("CR");

        ctx.recordMetadata("creditTxnRef", txnRef);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetTransaction(TestContext ctx) throws Exception {
        // Record a fresh transaction then retrieve it
        String freshRef = randomRef("TXNGET");
        ctx.getHttpClient().post(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions",
                buildRecordRequest(freshRef, TEST_ACCOUNT, "DR", 3000.00));

        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions/" + freshRef);

        assertThat(response.code()).isEqualTo(200);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("txnReferenceNumber").asText()).isEqualTo(freshRef);
        assertThat(body.path("accountNumber").asText()).isEqualTo(TEST_ACCOUNT);
        assertThat(body.path("amount").asDouble()).isEqualTo(3000.00);
        assertThat(body.path("createdAt").asText()).isNotBlank();
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetTransactionHistory(TestContext ctx) throws Exception {
        // Seed a few transactions
        for (int i = 0; i < 3; i++) {
            ctx.getHttpClient().post(
                    ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions",
                    buildRecordRequest(randomRef("TXNPG" + i), TEST_ACCOUNT,
                            i % 2 == 0 ? "CR" : "DR", 1000.0 * (i + 1)));
        }

        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(HISTORY_SERVICE)
                + "/api/v1/transactions/account/" + TEST_ACCOUNT + "?page=0&size=10");

        assertThat(response.code()).isEqualTo(200);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("transactions").isArray()).isTrue();
        assertThat(body.path("transactions").size()).isGreaterThanOrEqualTo(3);
        assertThat(body.path("totalElements").asLong()).isPositive();

        ctx.recordMetadata("transactionCount", String.valueOf(body.path("totalElements").asLong()));
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testRecordIdempotency(TestContext ctx) throws Exception {
        String txnRef = randomRef("TXNIDEM");
        String payload = buildRecordRequest(txnRef, TEST_ACCOUNT, "DR", 7500.00);

        var first = ctx.getHttpClient().post(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions", payload);
        assertThat(first.code()).isEqualTo(201);

        // Same txnRef — should return the existing record, not create a duplicate
        var second = ctx.getHttpClient().post(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions", payload);
        assertThat(second.code()).isIn(200, 201);
        assertThat(second.bodyAsJson().path("txnReferenceNumber").asText()).isEqualTo(txnRef);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetUnknownTransaction(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(HISTORY_SERVICE) + "/api/v1/transactions/UNKNOWNTXN99999");
        // Now returns 404 with the GlobalExceptionHandler in place
        assertThat(response.code()).isEqualTo(404);
        assertThat(response.bodyAsJson().path("errorCode").asText())
                .isEqualTo("TRANSACTION_NOT_FOUND");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }
}
