package com.banking.tests.suites;

import com.banking.testframework.container.TomcatServiceComponent;
import com.banking.testframework.test.*;
import com.banking.tests.util.PostgresComponent;
import com.banking.tests.util.ServiceImages;
import com.banking.tests.util.TestPayloads;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountServiceSuite extends AbstractTestSuiteDefinition {

    public static final String ACCOUNT_SERVICE = "accountservice";
    private static final String ACC_ACTIVE = "ACCACTIVE001";
    private static final String ACC_ACTIVE2 = "ACCACTIVE002";
    private static final String CUSTOMER_ID = "CUSTTEST001";

    public AccountServiceSuite() {
        addComponent(PostgresComponent.create());

        addComponent(TomcatServiceComponent.builder(ACCOUNT_SERVICE, ServiceImages.ACCOUNT_SERVICE)
                .port(8081)
                .healthCheckPath("/actuator/health")
                .dependsOn(PostgresComponent.ALIAS)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-001").name("Create active savings account")
                .tags(List.of("smoke")).abortOnFailure(true)
                .testCase(this::testCreateAccount).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-002").name("Get account by account number")
                .tags(List.of("smoke")).dependsOn(List.of("ACC-001"))
                .testCase(this::testGetAccount).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-003").name("Get balance returns correct available balance")
                .tags(List.of("smoke")).dependsOn(List.of("ACC-001"))
                .testCase(this::testGetBalance).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-004").name("Credit balance updates available balance correctly")
                .tags(List.of("regression")).dependsOn(List.of("ACC-001"))
                .testCase(this::testCreditBalance).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-005").name("Debit balance updates available balance correctly")
                .tags(List.of("regression")).dependsOn(List.of("ACC-004"))
                .testCase(this::testDebitBalance).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-006").name("Debit exceeding balance returns 422")
                .tags(List.of("regression")).dependsOn(List.of("ACC-001"))
                .testCase(this::testDebitInsufficientFunds).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-007").name("Get account — not found returns 404")
                .tags(List.of("regression"))
                .testCase(this::testAccountNotFound).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-008").name("Get accounts by customer ID returns all accounts")
                .tags(List.of("regression")).dependsOn(List.of("ACC-001"))
                .testCase(this::testGetByCustomer).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-009").name("Update account status to DORMANT")
                .tags(List.of("regression")).dependsOn(List.of("ACC-001"))
                .testCase(this::testUpdateStatus).build());

        addTest(TestCaseDefinition.builder()
                .id("ACC-010").name("Debit from DORMANT account returns 422")
                .tags(List.of("regression")).dependsOn(List.of("ACC-009"))
                .testCase(this::testDebitDormantAccount).build());
    }

    @Override
    public String getSuiteName() {
        return "account-service-suite";
    }

    @Override
    public void beforeAll(TestContext ctx) throws Exception {
        String base = ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts";

        // Diagnostic: hit health endpoint first and log the response
        var health = ctx.getHttpClient().get(ctx.getServiceUrl(ACCOUNT_SERVICE) + "/actuator/health");
        ctx.getLogger().info("Health check response: {} — {}", health.code(), health.body());

        var r1 = ctx.getHttpClient().post(base,
                TestPayloads.createAccountRequest(ACC_ACTIVE, "Test Customer Active",
                        "SAVINGS", "INR", new BigDecimal("50000.00"), "BR001", CUSTOMER_ID));
        ctx.getLogger().info("Seed ACC_ACTIVE response: {} — {}", r1.code(), r1.body());
        assertThat(r1.code()).as("Seed account 1 should be 201, got: " + r1.body()).isEqualTo(201);

        var r2 = ctx.getHttpClient().post(base,
                TestPayloads.createAccountRequest(ACC_ACTIVE2, "Test Customer Active 2",
                        "CURRENT", "INR", new BigDecimal("10000.00"), "BR001", CUSTOMER_ID));
        ctx.getLogger().info("Seed ACC_ACTIVE2 response: {} — {}", r2.code(), r2.body());
        assertThat(r2.code()).as("Seed account 2 should be 201, got: " + r2.body()).isEqualTo(201);
    }

    private TestResult testCreateAccount(TestContext ctx) throws Exception {
        String newAccount = "ACCNEW" + TestPayloads.randomAccount().substring(3, 9);
        var response = ctx.getHttpClient().post(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts",
                TestPayloads.createAccountRequest(newAccount, "New Account Holder",
                        "SAVINGS", "INR", new BigDecimal("5000.00"), "BR002", "CUSTNEW001"));

        ctx.getLogger().info("ACC-001 create response: {} — {}", response.code(), response.body());
        assertThat(response.code()).isEqualTo(201);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("accountNumber").asText()).isEqualTo(newAccount);
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("availableBalance").asDouble()).isEqualTo(5000.00);
        ctx.recordMetadata("createdAccount", newAccount);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetAccount(TestContext ctx) throws Exception {
        String url = ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE;
        ctx.getLogger().info("ACC-002 GET URL: {}", url);
        var response = ctx.getHttpClient().get(url);
        ctx.getLogger().info("ACC-002 response: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        JsonNode body = response.bodyAsJson();
        assertThat(body.path("accountNumber").asText()).isEqualTo(ACC_ACTIVE);
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetBalance(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance");
        ctx.getLogger().info("ACC-003 balance response: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.bodyAsJson().path("availableBalance").asDouble()).isPositive();
        ctx.recordMetadata("balance", response.bodyAsJson().path("availableBalance").asText());
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testCreditBalance(TestContext ctx) throws Exception {
        var before = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance");
        double balanceBefore = before.bodyAsJson().path("availableBalance").asDouble();

        var update = ctx.getHttpClient().put(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance",
                TestPayloads.updateBalanceRequest(new BigDecimal("10000.00"), "Test credit"));
        ctx.getLogger().info("ACC-004 credit response: {} — {}", update.code(), update.body());
        assertThat(update.code()).isEqualTo(200);

        var after = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance");
        assertThat(after.bodyAsJson().path("availableBalance").asDouble())
                .isEqualTo(balanceBefore + 10000.00);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testDebitBalance(TestContext ctx) throws Exception {
        var before = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance");
        double balanceBefore = before.bodyAsJson().path("availableBalance").asDouble();

        var update = ctx.getHttpClient().put(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance",
                TestPayloads.updateBalanceRequest(new BigDecimal("-5000.00"), "Test debit"));
        assertThat(update.code()).isEqualTo(200);

        var after = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance");
        assertThat(after.bodyAsJson().path("availableBalance").asDouble())
                .isEqualTo(balanceBefore - 5000.00);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testDebitInsufficientFunds(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().put(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + ACC_ACTIVE + "/balance",
                TestPayloads.updateBalanceRequest(new BigDecimal("-9999999.00"), "Overdraft"));
        ctx.getLogger().info("ACC-006 overdraft response: {} — {}", response.code(), response.body());
        assertThat(response.code()).isEqualTo(422);
        assertThat(response.bodyAsJson().path("errorCode").asText()).isEqualTo("ACCOUNT_STATE_ERROR");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testAccountNotFound(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/NONEXISTENT99999");
        ctx.getLogger().info("ACC-007 not-found response: {} — {}", response.code(), response.body());
        assertThat(response.code()).isEqualTo(404);
        assertThat(response.bodyAsJson().path("errorCode").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testGetByCustomer(TestContext ctx) throws Exception {
        var response = ctx.getHttpClient().get(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/customer/" + CUSTOMER_ID);
        ctx.getLogger().info("ACC-008 customer response: {} — {}", response.code(), response.body());
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.bodyAsJson().isArray()).isTrue();
        assertThat(response.bodyAsJson().size()).isGreaterThanOrEqualTo(2);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testUpdateStatus(TestContext ctx) throws Exception {
        String freshAccount = "ACCDORMANT" + TestPayloads.randomAccount().substring(3, 7);
        ctx.getHttpClient().post(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts",
                TestPayloads.createAccountRequest(freshAccount, "Dormant Test",
                        "SAVINGS", "INR", new BigDecimal("1000.00"), "BR001", "CUSTDORMANT"));

        var response = ctx.getHttpClient().put(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/"
                + freshAccount + "/status?status=DORMANT", "{}");
        ctx.getLogger().info("ACC-009 status update response: {} — {}", response.code(), response.body());

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.bodyAsJson().path("status").asText()).isEqualTo("DORMANT");
        ctx.recordMetadata("dormantAccount", freshAccount);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testDebitDormantAccount(TestContext ctx) throws Exception {
        String dormantAcc = ctx.getTestMetadata().getOrDefault("dormantAccount", "ACCDORMANT0001");
        var response = ctx.getHttpClient().put(
                ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts/" + dormantAcc + "/balance",
                TestPayloads.updateBalanceRequest(new BigDecimal("-100.00"), "Debit dormant"));
        assertThat(response.code()).isEqualTo(422);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }
}
