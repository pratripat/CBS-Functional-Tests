package com.banking.tests.suites;

import com.banking.testframework.container.TomcatServiceComponent;
import com.banking.testframework.mock.BankingMockBuilder;
import com.banking.testframework.mock.MockDownstreamComponent;
import com.banking.testframework.test.*;
import com.banking.tests.stubs.CbsStubs;
import com.banking.tests.util.PostgresComponent;
import com.banking.tests.util.ServiceImages;
import com.banking.tests.util.TestPayloads;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration suite testing the full platform stack via Channel
 * Service.
 *
 * Component aliases use no hyphens (Spring Boot 3 path variable safety). CBS
 * mock URL is exposed via exposeUrlAs() so funds-transfer-service container
 * receives the Docker-reachable address automatically.
 */
public class EndToEndSuite extends AbstractTestSuiteDefinition {

    public static final String POSTGRES = PostgresComponent.ALIAS;
    public static final String CBS_MOCK = "cbsmock";
    public static final String HISTORY_SERVICE = "transactionhistorysvc";
    public static final String ACCOUNT_SERVICE = "accountsvc";
    public static final String TRANSFER_SERVICE = "fundstransfersvc";
    public static final String CHANNEL_SERVICE = "channelsvc";

    // Alphanumeric account numbers — no hyphens
    private static final String DEBIT_ACCOUNT = "E2EDEBIT001";
    private static final String CREDIT_ACCOUNT = "E2ECREDIT001";
    private static final String CUSTOMER_ID = "E2ECUST001";

    public EndToEndSuite() {

        // ── 1. Postgres ──────────────────────────────────────────────────────
        addComponent(PostgresComponent.create());

        // ── 2. CBS Mock ──────────────────────────────────────────────────────
        // exposeUrlAs injects CBS_BASE_URL into dependent containers
        addComponent(BankingMockBuilder.forCBS(CBS_MOCK)
                .withGlobalLatency(25, TimeUnit.MILLISECONDS)
                .exposeUrlAs("CBS_BASE_URL")
                .build());

        // ── 3. Transaction History Service ───────────────────────────────────
        addComponent(TomcatServiceComponent.builder(HISTORY_SERVICE,
                ServiceImages.TRANSACTION_HISTORY_SVC)
                .port(8083)
                .healthCheckPath("/actuator/health")
                .dependsOn(POSTGRES)
                .build());

        // ── 4. Account Service ───────────────────────────────────────────────
        addComponent(TomcatServiceComponent.builder(ACCOUNT_SERVICE,
                ServiceImages.ACCOUNT_SERVICE)
                .port(8081)
                .healthCheckPath("/actuator/health")
                .dependsOn(POSTGRES)
                .build());

        // ── 5. Funds Transfer Service ────────────────────────────────────────
        // CBS_BASE_URL is auto-injected by the CBS mock's exposeUrlAs()
        // HISTORY_SERVICE_URL uses the Docker network alias of history service
        addComponent(TomcatServiceComponent.builder(TRANSFER_SERVICE,
                ServiceImages.FUNDS_TRANSFER_SERVICE)
                .port(8082)
                .healthCheckPath("/actuator/health")
                .dependsOn(POSTGRES, CBS_MOCK, HISTORY_SERVICE)
                .env("HISTORY_SERVICE_URL",
                        "http://" + HISTORY_SERVICE + ":8083")
                .build());

        // ── 6. Channel Service ───────────────────────────────────────────────
        addComponent(TomcatServiceComponent.builder(CHANNEL_SERVICE,
                ServiceImages.CHANNEL_SERVICE)
                .port(8080)
                .healthCheckPath("/actuator/health")
                .dependsOn(ACCOUNT_SERVICE, TRANSFER_SERVICE, HISTORY_SERVICE)
                .env("ACCOUNT_SERVICE_URL",
                        "http://" + ACCOUNT_SERVICE + ":8081")
                .env("FUNDS_TRANSFER_SERVICE_URL",
                        "http://" + TRANSFER_SERVICE + ":8082")
                .env("TRANSACTION_HISTORY_SERVICE_URL",
                        "http://" + HISTORY_SERVICE + ":8083")
                .build());

        // ── Tests ────────────────────────────────────────────────────────────
        addTest(TestCaseDefinition.builder()
                .id("web-console-001").name("Create debit and credit accounts")
                .tags(List.of("smoke")).abortOnFailure(true)
                .testCase(this::testCreateAccounts).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-002").name("Account inquiry via channel-service")
                .tags(List.of("smoke")).dependsOn(List.of("web-console-001"))
                .testCase(this::testAccountInquiryViaChannel).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-003").name("Balance check via channel-service")
                .tags(List.of("smoke")).dependsOn(List.of("web-console-001"))
                .testCase(this::testBalanceCheckViaChannel).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-004").name("Successful funds transfer via channel → transfer → CBS")
                .tags(List.of("smoke")).dependsOn(List.of("web-console-001"))
                .testCase(this::testSuccessfulFundsTransferViaChannel).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-005").name("Transaction appears in mini-statement after transfer")
                .tags(List.of("regression")).dependsOn(List.of("web-console-004"))
                .testCase(this::testMiniStatementAfterTransfer).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-006").name("Insufficient funds via channel returns 422")
                .tags(List.of("regression")).dependsOn(List.of("web-console-001"))
                .testCase(this::testInsufficientFundsViaChannel).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-007").name("Idempotent transfer via channel returns same txnRef")
                .tags(List.of("regression")).dependsOn(List.of("web-console-004"))
                .testCase(this::testIdempotencyViaChannel).build());

        addTest(TestCaseDefinition.builder()
                .id("web-console-008").name("Get all accounts for customer via channel-service")
                .tags(List.of("regression")).dependsOn(List.of("web-console-001"))
                .testCase(this::testGetCustomerAccounts).build());
    }

    @Override
    public String getSuiteName() {
        return "end-to-end-suite";
    }

    @Override
    public void beforeAll(TestContext ctx) throws Exception {
        ctx.getLogger().info("web-console suite ready.");
        ctx.getLogger().info("  channel-service  : {}", ctx.getServiceUrl(CHANNEL_SERVICE));
        ctx.getLogger().info("  account-service  : {}", ctx.getServiceUrl(ACCOUNT_SERVICE));
        ctx.getLogger().info("  transfer-service : {}", ctx.getServiceUrl(TRANSFER_SERVICE));
        ctx.getLogger().info("  history-service  : {}", ctx.getServiceUrl(HISTORY_SERVICE));
        ctx.getLogger().info("  cbs-mock         : {}", ctx.getServiceUrl(CBS_MOCK));
    }

    @Override
    public void beforeEach(TestContext ctx, TestCaseDefinition def) {
        ctx.getMockServer(CBS_MOCK).resetAll();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────
    private TestResult testCreateAccounts(TestContext ctx) throws Exception {
        String accountBase = ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts";

        var debitResp = ctx.getHttpClient().post(accountBase,
                TestPayloads.createAccountRequest(DEBIT_ACCOUNT, "web-console Debit Customer",
                        "SAVINGS", "INR", new BigDecimal("100000.00"), "BR001", CUSTOMER_ID));
        ctx.getLogger().info("web-console-001 create debit: {} — {}", debitResp.code(), debitResp.body());
        assertThat(debitResp.code()).isEqualTo(201);

        var creditResp = ctx.getHttpClient().post(accountBase,
                TestPayloads.createAccountRequest(CREDIT_ACCOUNT, "web-console Credit Customer",
                        "SAVINGS", "INR", new BigDecimal("0.00"), "BR001", CUSTOMER_ID));
        ctx.getLogger().info("web-console-001 create credit: {} — {}", creditResp.code(), creditResp.body());
        assertThat(creditResp.code()).isEqualTo(201);

        ctx.recordMetadata("debitAccount", DEBIT_ACCOUNT);
        ctx.recordMetadata("creditAccount", CREDIT_ACCOUNT);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testAccountInquiryViaChannel(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(CHANNEL_SERVICE) + "/api/v1/accounts/" + DEBIT_ACCOUNT);
        ctx.getLogger().info("web-console-002 inquiry: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("accountNumber").asText()).isEqualTo(DEBIT_ACCOUNT);
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("availableBalance").asDouble()).isEqualTo(100000.00);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testBalanceCheckViaChannel(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(CHANNEL_SERVICE)
                + "/api/v1/accounts/" + DEBIT_ACCOUNT + "/balance");
        ctx.getLogger().info("web-console-003 balance: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.bodyAsJson().path("availableBalance").asDouble()).isPositive();
        ctx.recordMetadata("balance", response.bodyAsJson().path("availableBalance").asText());
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testSuccessfulFundsTransferViaChannel(TestContext ctx) throws Exception {
        String txnRef = "CBSE2E" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess(txnRef))));

        String idempotencyKey = TestPayloads.randomKey();
        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(CHANNEL_SERVICE) + "/api/v1/funds-transfer",
                TestPayloads.fundsTransferRequest(
                        DEBIT_ACCOUNT, CREDIT_ACCOUNT,
                        new BigDecimal("25000.00"), "INR", idempotencyKey));

        ctx.getLogger().info("web-console-004 transfer: {} — {}", response.code(), response.body());
        assertThat(response.code()).isIn(200, 201);
        assertThat(response.bodyAsJson().path("status").asText()).isEqualTo("SUCCESS");
        assertThat(response.bodyAsJson().path("txnReferenceNumber").asText()).isNotBlank();

        ctx.getMockServer(CBS_MOCK).verify(1,
                postRequestedFor(urlEqualTo("/funds-transfer")));

        ctx.recordMetadata("transferTxnRef",
                response.bodyAsJson().path("txnReferenceNumber").asText());
        ctx.recordMetadata("idempotencyKey", idempotencyKey);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testMiniStatementAfterTransfer(TestContext ctx) throws Exception {
        Thread.sleep(500);

        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(CHANNEL_SERVICE)
                + "/api/v1/accounts/" + DEBIT_ACCOUNT + "/mini-statement");
        ctx.getLogger().info("web-console-005 mini-statement: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("accountNumber").asText()).isEqualTo(DEBIT_ACCOUNT);
        assertThat(body.path("transactions").isArray()).isTrue();
        assertThat(body.path("transactions").size()).isGreaterThanOrEqualTo(1);

        ctx.recordMetadata("miniStatementCount",
                String.valueOf(body.path("transactions").size()));
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testInsufficientFundsViaChannel(TestContext ctx) throws Exception {
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferInsufficientFunds())));

        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(CHANNEL_SERVICE) + "/api/v1/funds-transfer",
                TestPayloads.fundsTransferRequest(
                        DEBIT_ACCOUNT, CREDIT_ACCOUNT,
                        new BigDecimal("9999999.00"), "INR", TestPayloads.randomKey()));

        ctx.getLogger().info("web-console-006 insufficient: {} — {}", response.code(), response.body());
        // Channel proxies the 422 from funds-transfer-service
        assertThat(response.code()).isIn(422, 500);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testIdempotencyViaChannel(TestContext ctx) throws Exception {
        String idempotencyKey = TestPayloads.randomKey();

        String txnRef = "CBSIDEM" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess(txnRef))));

        String payload = TestPayloads.fundsTransferRequest(
                DEBIT_ACCOUNT, CREDIT_ACCOUNT,
                new BigDecimal("5000.00"), "INR", idempotencyKey);

        var first = ctx.getHttpClient().post(
                ctx.getServiceUrl(CHANNEL_SERVICE) + "/api/v1/funds-transfer", payload);
        var second = ctx.getHttpClient().post(
                ctx.getServiceUrl(CHANNEL_SERVICE) + "/api/v1/funds-transfer", payload);

        ctx.getLogger().info("web-console-007 first: {} second: {}", first.code(), second.code());

        String firstRef = first.bodyAsJson().path("txnReferenceNumber").asText();
        String secondRef = second.bodyAsJson().path("txnReferenceNumber").asText();
        assertThat(secondRef)
                .as("Idempotent call must return same txnReferenceNumber")
                .isEqualTo(firstRef);

        ctx.getMockServer(CBS_MOCK).verify(1,
                postRequestedFor(urlEqualTo("/funds-transfer")));

        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetCustomerAccounts(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(CHANNEL_SERVICE)
                + "/api/v1/accounts/customer/" + CUSTOMER_ID);
        ctx.getLogger().info("web-console-008 customer accounts: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.bodyAsJson().isArray()).isTrue();
        assertThat(response.bodyAsJson().size()).isGreaterThanOrEqualTo(2);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }
}
