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
 * Integration tests for the Funds Transfer Service.
 *
 * Key design: MockDownstreamComponent now exposes its Docker-reachable URL via
 * getExposedEnvironment() using the key set by exposeUrlAs().
 * TomcatServiceComponent picks this up automatically via the accumulated env.
 */
public class FundsTransferServiceSuite extends AbstractTestSuiteDefinition {

    public static final String TRANSFER_SERVICE = "fundstransferservice";
    public static final String CBS_MOCK = "cbsmock";
    public static final String HISTORY_MOCK = "historymock";

    public FundsTransferServiceSuite() {
        // ── Components ──────────────────────────────────────────────────────
        addComponent(PostgresComponent.create());

        // CBS mock — exposeUrlAs causes CBS_BASE_URL to be injected into
        // dependent containers pointing to host.docker.internal/bridge-IP:PORT
        MockDownstreamComponent cbsMock = BankingMockBuilder.forCBS(CBS_MOCK)
                .withGlobalLatency(20, TimeUnit.MILLISECONDS)
                .exposeUrlAs("CBS_BASE_URL") // funds-transfer-service reads this
                .build();
        addComponent(cbsMock);

        // History service mock — exposes HISTORY_SERVICE_URL
        addComponent(MockDownstreamComponent.builder(HISTORY_MOCK)
                .exposeUrlAs("HISTORY_SERVICE_URL")
                .withStub(server -> server.stubFor(
                post(urlPathMatching("/api/v1/transactions"))
                        .willReturn(aResponse().withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"txnReferenceNumber\":\"recorded\"}"))))
                .build());

        // Funds Transfer Service — CBS_BASE_URL and HISTORY_SERVICE_URL are
        // injected automatically via the mocks' exposeUrlAs declarations.
        // We append the path suffix for CBS_BASE_URL here since WireMock
        // exposes the root and the service config expects the full ops path.
        addComponent(TomcatServiceComponent.builder(TRANSFER_SERVICE,
                ServiceImages.FUNDS_TRANSFER_SERVICE)
                .port(8082)
                .healthCheckPath("/actuator/health")
                .dependsOn(PostgresComponent.ALIAS, CBS_MOCK, HISTORY_MOCK)
                .build());

        // ── Tests ────────────────────────────────────────────────────────────
        addTest(TestCaseDefinition.builder()
                .id("FT-001").name("Successful funds transfer returns 201 with txnRef")
                .tags(List.of("smoke")).testCase(this::testSuccessfulTransfer).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-002").name("Insufficient funds — CBS 51 maps to 422 INSUFFICIENT_FUNDS")
                .tags(List.of("regression")).testCase(this::testInsufficientFunds).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-003").name("Idempotency — same key returns same txnRef, CBS called once")
                .tags(List.of("regression")).dependsOn(List.of("FT-001"))
                .testCase(this::testIdempotency).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-004").name("CBS timeout — service returns 504 CBS_TIMEOUT")
                .tags(List.of("regression")).testCase(this::testCbsTimeout).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-005").name("Missing amount — validation returns 400")
                .tags(List.of("regression")).testCase(this::testMissingAmount).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-006").name("Amount over limit — validation returns 400")
                .tags(List.of("regression")).testCase(this::testOverLimit).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-007").name("CBS unavailable — maps to 422 CBS_UNAVAILABLE")
                .tags(List.of("regression")).testCase(this::testCbsUnavailable).build());

        addTest(TestCaseDefinition.builder()
                .id("FT-008").name("History service records debit and credit entries")
                .tags(List.of("regression")).testCase(this::testHistoryServiceCalled).build());
    }

    @Override
    public String getSuiteName() {
        return "funds-transfer-service-suite";
    }

    @Override
    public void beforeEach(TestContext ctx, TestCaseDefinition def) {
        ctx.getMockServer(CBS_MOCK).resetAll();
        ctx.getMockServer(HISTORY_MOCK).resetAll();
        // Re-register default history stub after reset
        ctx.getMockServer(HISTORY_MOCK).stubFor(
                post(urlPathMatching("/api/v1/transactions"))
                        .willReturn(aResponse().withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"txnReferenceNumber\":\"recorded\"}")));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────
    private TestResult testSuccessfulTransfer(TestContext ctx) throws Exception {
        String txnRef = "CBS" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess(txnRef))));

        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers",
                TestPayloads.fundsTransferRequest(
                        "ACC001", "ACC002", new BigDecimal("10000.00"),
                        "INR", TestPayloads.randomKey()));

        assertThat(response.code()).isEqualTo(201);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(body.path("txnReferenceNumber").asText()).isNotBlank();

        ctx.getMockServer(CBS_MOCK).verify(1,
                postRequestedFor(urlEqualTo("/funds-transfer")));

        ctx.recordMetadata("txnRef", body.path("txnReferenceNumber").asText());
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testInsufficientFunds(TestContext ctx) throws Exception {
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferInsufficientFunds())));

        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers",
                TestPayloads.fundsTransferRequest(
                        "ACC001", "ACC002", new BigDecimal("999999.00"),
                        "INR", TestPayloads.randomKey()));

        assertThat(response.code()).isEqualTo(422);
        assertThat(response.bodyAsJson().path("errorCode").asText())
                .isEqualTo("INSUFFICIENT_FUNDS");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testIdempotency(TestContext ctx) throws Exception {
        String idempotencyKey = TestPayloads.randomKey();
        String cbsTxnRef = "CBSIDEM" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess(cbsTxnRef))));

        String payload = TestPayloads.fundsTransferRequest(
                "ACC001", "ACC002", new BigDecimal("5000.00"), "INR", idempotencyKey);

        var first = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers", payload);
        assertThat(first.code()).isEqualTo(201);
        String firstTxnRef = first.bodyAsJson().path("txnReferenceNumber").asText();

        var second = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers", payload);
        assertThat(second.code()).isIn(200, 201);
        assertThat(second.bodyAsJson().path("txnReferenceNumber").asText()).isEqualTo(firstTxnRef);

        ctx.getMockServer(CBS_MOCK).verify(1,
                postRequestedFor(urlEqualTo("/funds-transfer")));

        ctx.recordMetadata("txnRef", firstTxnRef);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testCbsTimeout(TestContext ctx) throws Exception {
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess("TIMEOUT"))
                                .withFixedDelay(30_000)));

        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers",
                TestPayloads.fundsTransferRequest(
                        "ACC001", "ACC002", new BigDecimal("1000.00"),
                        "INR", TestPayloads.randomKey()));

        assertThat(response.code()).isEqualTo(504);
        assertThat(response.bodyAsJson().path("errorCode").asText()).isEqualTo("CBS_TIMEOUT");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testMissingAmount(TestContext ctx) throws Exception {
        String payload = """
                {"debitAccountNumber":"ACC001","creditAccountNumber":"ACC002",
                 "currency":"INR","idempotencyKey":"%s"}
                """.formatted(TestPayloads.randomKey());

        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers", payload);

        assertThat(response.code()).isEqualTo(400);
        assertThat(response.bodyAsJson().path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        ctx.getMockServer(CBS_MOCK).verify(0,
                postRequestedFor(urlEqualTo("/funds-transfer")));
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testOverLimit(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers",
                TestPayloads.fundsTransferRequest(
                        "ACC001", "ACC002", new BigDecimal("1000001.00"),
                        "INR", TestPayloads.randomKey()));

        assertThat(response.code()).isEqualTo(400);
        assertThat(response.bodyAsJson().path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        ctx.getMockServer(CBS_MOCK).verify(0,
                postRequestedFor(urlEqualTo("/funds-transfer")));
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testCbsUnavailable(TestContext ctx) throws Exception {
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferCbsUnavailable())));

        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers",
                TestPayloads.fundsTransferRequest(
                        "ACC001", "ACC002", new BigDecimal("5000.00"),
                        "INR", TestPayloads.randomKey()));

        assertThat(response.code()).isEqualTo(422);
        assertThat(response.bodyAsJson().path("errorCode").asText()).isEqualTo("CBS_UNAVAILABLE");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testHistoryServiceCalled(TestContext ctx) throws Exception {
        String txnRef = "CBSHIST" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess(txnRef))));

        ctx.getHttpClient().post(
                ctx.getServiceUrl(TRANSFER_SERVICE) + "/api/v1/transfers",
                TestPayloads.fundsTransferRequest(
                        "ACC001", "ACC002", new BigDecimal("2000.00"),
                        "INR", TestPayloads.randomKey()));

        ctx.getMockServer(HISTORY_MOCK).verify(
                moreThanOrExactly(2),
                postRequestedFor(urlPathMatching("/api/v1/transactions")));

        return TestResult.builder().status(TestStatus.PASSED).build();
    }
}
