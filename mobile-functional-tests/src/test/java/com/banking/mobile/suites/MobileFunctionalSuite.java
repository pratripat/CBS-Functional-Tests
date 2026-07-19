package com.banking.mobile.suites;

import com.banking.mobile.components.AndroidEmulatorComponent;
import com.banking.mobile.components.PortForwarder;
import com.banking.mobile.context.AppiumActions;
import com.banking.mobile.context.AppiumTestContext;
import com.banking.testframework.container.DeployableComponent;
import com.banking.testframework.container.TomcatServiceComponent;
import com.banking.testframework.mock.BankingMockBuilder;
import com.banking.testframework.test.*;
import com.banking.tests.util.PostgresComponent;
import com.banking.tests.util.ServiceImages;
import com.banking.tests.stubs.CbsStubs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mobile functional test suite for the Banking Platform Android app.
 *
 * Full component graph deployed by this suite:
 * <pre>
 *   postgres
 *   cbs-mock (WireMock — CBS simulator)
 *   transaction-history-service
 *   account-service
 *   funds-transfer-service  ← CBS_BASE_URL injected by cbs-mock
 *   channel-service         ← all downstream URLs injected
 *   android-emulator        ← host-based emulator + local Appium
 * </pre>
 *
 * The Android app APK is built before the test run via:
 *   ./gradlew assembleDebug
 *
 * Appium drives the app UI end-to-end, verifying that:
 *   - Tapping buttons navigates to the correct screen
 *   - Filling forms and submitting fires the real channel-service API
 *   - API responses are correctly rendered in the result cards
 *
 * UI interaction uses resource-ID based locators (By.id) wherever possible,
 * with text-based XPath as a fallback for assertion checks.
 */
public class MobileFunctionalSuite extends AbstractTestSuiteDefinition {

    private static final Logger log = LoggerFactory.getLogger(MobileFunctionalSuite.class);

    // Component aliases
    public static final String POSTGRES = PostgresComponent.ALIAS;
    public static final String CBS_MOCK = "cbsmock";
    public static final String HISTORY_SERVICE = "transactionhistorysvc";
    public static final String ACCOUNT_SERVICE = "accountsvc";
    public static final String TRANSFER_SERVICE = "fundstransfersvc";
    public static final String CHANNEL_SERVICE = "channelsvc";
    public static final String EMULATOR = "android-emulator";

    // Test data — seeded in beforeAll
    private static final String TEST_ACCOUNT = "MOBILEACC001";
    private static final String CREDIT_ACCOUNT = "MOBILEACC002";
    private static final String CUSTOMER_ID = "MOBILECUST001";

    // Path to the APK built by Gradle
    private static final String APK_PATH = System.getProperty("apk.path",
            "banking-mobile-app/app/build/outputs/apk/debug/app-debug.apk");

    private AndroidEmulatorComponent emulatorComponent;
    private AppiumTestContext appiumCtx;
    private PortForwarder portForwarder;

    public MobileFunctionalSuite() {

        // ── 1. Postgres ──────────────────────────────────────────────────────
        addComponent(PostgresComponent.create());

        // ── 2. CBS Mock ──────────────────────────────────────────────────────
        addComponent(BankingMockBuilder.forCBS(CBS_MOCK)
                .withGlobalLatency(20, TimeUnit.MILLISECONDS)
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
        addComponent(TomcatServiceComponent.builder(TRANSFER_SERVICE,
                ServiceImages.FUNDS_TRANSFER_SERVICE)
                .port(8082)
                .healthCheckPath("/actuator/health")
                .dependsOn(POSTGRES, CBS_MOCK, HISTORY_SERVICE)
                .env("HISTORY_SERVICE_URL", "http://" + HISTORY_SERVICE + ":8083")
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

        // ── 7. Android Emulator (with bundled Appium) ────────────────────────
        emulatorComponent = AndroidEmulatorComponent.withDefaults(APK_PATH);
        addComponent(emulatorComponent);

        // ── Test cases ────────────────────────────────────────────────────────
        addTest(TestCaseDefinition.builder()
                .id("MOB-001")
                .name("Dashboard loads with all 5 operation buttons visible")
                .tags(List.of("smoke", "navigation"))
                .abortOnFailure(true)
                .testCase(this::testDashboardLoads)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("MOB-002")
                .name("Balance Check — tapping button fetches and displays balance")
                .tags(List.of("smoke", "balance"))
                .dependsOn(List.of("MOB-001"))
                .testCase(this::testBalanceCheck)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("MOB-003")
                .name("Account Inquiry — tapping button fetches account details")
                .tags(List.of("smoke", "account-inquiry"))
                .dependsOn(List.of("MOB-001"))
                .testCase(this::testAccountInquiry)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("MOB-004")
                .name("Funds Transfer — happy path displays SUCCESS status")
                .tags(List.of("smoke", "transfer", "happy-path"))
                .dependsOn(List.of("MOB-001"))
                .testCase(this::testFundsTransferSuccess)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("MOB-005")
                .name("Funds Transfer — insufficient funds shows error card")
                .tags(List.of("regression", "transfer", "error-handling"))
                .dependsOn(List.of("MOB-001"))
                .testCase(this::testFundsTransferInsufficientFunds)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("MOB-006")
                .name("Deposit — successful credit shows success message")
                .tags(List.of("regression", "deposit"))
                .dependsOn(List.of("MOB-001"))
                .testCase(this::testDeposit)
                .build());

        addTest(TestCaseDefinition.builder()
                .id("MOB-007")
                .name("Transaction History — mini statement displays transactions")
                .tags(List.of("regression", "history"))
                .dependsOn(List.of("MOB-004")) // Needs a transfer to have happened
                .testCase(this::testTransactionHistory)
                .build());
    }

    @Override
    public String getSuiteName() {
        return "mobile-functional-suite";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void beforeAll(TestContext ctx) throws Exception {
        // Verify APK exists
        File apk = new File(APK_PATH);
        if (!apk.exists()) {
            throw new IllegalStateException(
                    "APK not found at: " + apk.getAbsolutePath()
                    + "\nBuild it first: cd banking-mobile-app && "
                    + "./gradlew assembleDebug -PBASE_URL=http://host.docker.internal:8080");
        }
        log.info("APK found at: {} ({} KB)", apk.getAbsolutePath(), apk.length() / 1024);

        // Seed test accounts in account-service directly via HTTP
        String accountBase = ctx.getServiceUrl(ACCOUNT_SERVICE) + "/api/v1/accounts";

        ctx.getHttpClient().post(accountBase, """
                {"accountNumber":"%s","accountName":"Mobile Test Account",
                 "accountType":"SAVINGS","currency":"INR",
                 "initialBalance":100000.00,"branchCode":"BR001","customerId":"%s"}
                """.formatted(TEST_ACCOUNT, CUSTOMER_ID));

        ctx.getHttpClient().post(accountBase, """
                {"accountNumber":"%s","accountName":"Mobile Credit Account",
                 "accountType":"SAVINGS","currency":"INR",
                 "initialBalance":0.00,"branchCode":"BR001","customerId":"%s"}
                """.formatted(CREDIT_ACCOUNT, CUSTOMER_ID));

        log.info("Test accounts seeded: {} and {}", TEST_ACCOUNT, CREDIT_ACCOUNT);

        // Set up TCP port forwarding from fixed ports (8080, 8081) to
        // dynamic service ports, so the APK's hardcoded http://10.0.2.2:8080
        // reaches the dynamically-mapped channel-service port.
        portForwarder = new PortForwarder();
        String channelUrl = ctx.getServiceUrl(CHANNEL_SERVICE);
        String accountUrl = ctx.getServiceUrl(ACCOUNT_SERVICE);
        int channelPort = Integer.parseInt(channelUrl.substring(channelUrl.lastIndexOf(':') + 1));
        int accountPort = Integer.parseInt(accountUrl.substring(accountUrl.lastIndexOf(':') + 1));
        portForwarder.startForward("channel", 8080, "127.0.0.1", channelPort);
        portForwarder.startForward("account", 8081, "127.0.0.1", accountPort);
        log.info("Port forwarding active: 8080 → {}:{}, 8081 → {}:{}",
                "127.0.0.1", channelPort, "127.0.0.1", accountPort);

        // Wrap the standard TestContext in an AppiumTestContext
        appiumCtx = new AppiumTestContext(
                ctx.getAllServiceUrls(),
                java.util.Collections.emptyMap(),
                ctx.getHttpClient(),
                ctx.getAllServiceUrls(), // suite properties
                emulatorComponent,
                apk.getAbsolutePath());
    }

    @Override
    public void beforeEach(TestContext ctx, TestCaseDefinition def) throws Exception {
        // Reset CBS stubs and restart app for each test
        ctx.getMockServer(CBS_MOCK).resetAll();
        if (appiumCtx != null && appiumCtx.getDriver() != null) {
            appiumCtx.restartApp();
        }
    }

    @Override
    public void afterAll(TestContext ctx, List<TestResult> results) throws Exception {
        if (appiumCtx != null) {
            appiumCtx.quitDriver();
        }
        if (portForwarder != null) {
            portForwarder.stopAll();
        }
    }

    // ── Test implementations ──────────────────────────────────────────────────
    private TestResult testDashboardLoads(TestContext ctx) throws Exception {
        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        assertThat(ui.isTextVisible("Banking Platform"))
                .as("App title should be visible on dashboard")
                .isTrue();
        assertThat(ui.isTextVisible("Funds Transfer"))
                .as("Funds Transfer button should be on dashboard")
                .isTrue();
        assertThat(ui.isTextVisible("Deposit"))
                .as("Deposit button should be on dashboard")
                .isTrue();
        assertThat(ui.isTextVisible("Balance Check"))
                .as("Balance Check button should be on dashboard")
                .isTrue();
        assertThat(ui.isTextVisible("Account Inquiry"))
                .as("Account Inquiry button should be on dashboard")
                .isTrue();
        assertThat(ui.isTextVisible("Transaction History"))
                .as("Transaction History button should be on dashboard")
                .isTrue();

        ctx.recordMetadata("screen", "Dashboard");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testBalanceCheck(TestContext ctx) throws Exception {
        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        // Navigate to Balance Check screen
        ui.tapDashboardButton("Balance Check");
        assertThat(ui.isTextVisible("Balance Check")).isTrue();

        // Enter account number and submit
        ui.typeInField("Account Number", TEST_ACCOUNT);
        ui.tapButton("Check Balance");

        // Assert balance is displayed
        ui.waitForText(TEST_ACCOUNT);
        assertThat(ui.isTextVisible("Available Balance")).isTrue();
        assertThat(ui.isTextVisible("INR")).isTrue();

        ctx.recordMetadata("accountNumber", TEST_ACCOUNT);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testAccountInquiry(TestContext ctx) throws Exception {
        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        ui.tapDashboardButton("Account Inquiry");
        ui.typeInField("Account Number", TEST_ACCOUNT);
        ui.tapButton("Inquire");

        ui.waitForText("Mobile Test Account");
        assertThat(ui.isTextVisible("SAVINGS")).isTrue();
        assertThat(ui.isTextVisible("ACTIVE")).isTrue();

        ctx.recordMetadata("accountName", "Mobile Test Account");
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testFundsTransferSuccess(TestContext ctx) throws Exception {
        String txnRef = "CBSMOB" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        // Stub CBS to return success
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferSuccess(txnRef))));

        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        ui.tapDashboardButton("Funds Transfer");
        ui.typeInField("Debit Account Number", TEST_ACCOUNT);
        ui.typeInField("Credit Account Number", CREDIT_ACCOUNT);
        ui.typeInField("Amount", "10000");
        ui.typeInField("Currency (INR/USD)", "INR");
        ui.tapButton("Transfer");

        // Assert success result card is shown
        ui.waitForText("SUCCESS");
        assertThat(ui.isTextVisible("SUCCESS")).isTrue();
        assertThat(ui.isTextVisible("Txn Ref")).isTrue();

        // Verify CBS was called exactly once
        ctx.getMockServer(CBS_MOCK).verify(1,
                postRequestedFor(urlEqualTo("/funds-transfer")));

        ctx.recordMetadata("cbsTxnRef", txnRef);
        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testFundsTransferInsufficientFunds(TestContext ctx) throws Exception {
        ctx.getMockServer(CBS_MOCK).stubFor(
                post(urlEqualTo("/funds-transfer"))
                        .willReturn(okJson(CbsStubs.fundsTransferInsufficientFunds())));

        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        ui.tapDashboardButton("Funds Transfer");
        ui.typeInField("Debit Account Number", TEST_ACCOUNT);
        ui.typeInField("Credit Account Number", CREDIT_ACCOUNT);
        ui.typeInField("Amount", "750000");
        ui.typeInField("Currency (INR/USD)", "INR");
        ui.tapButton("Transfer");

        // Assert error card is shown
        assertThat(ui.isTextVisible("❌")).as("Error icon should be visible").isTrue();

        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testDeposit(TestContext ctx) throws Exception {
        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        ui.tapDashboardButton("Deposit");
        ui.typeInField("Account Number", TEST_ACCOUNT);
        ui.typeInField("Amount", "5000");
        ui.typeInField("Description", "Mobile test deposit");
        ui.tapButton("Submit Deposit");

        ui.waitForText("Deposit successful");
        assertThat(ui.isTextVisible("✅")).isTrue();

        return TestResult.builder().status(TestStatus.PASSED).build();
    }

    private TestResult testTransactionHistory(TestContext ctx) throws Exception {
        AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

        ui.tapDashboardButton("Transaction History");
        ui.typeInField("Account Number", TEST_ACCOUNT);
        ui.tapButton("Get Mini Statement");

        // After a successful transfer in MOB-004, at least 1 transaction should appear
        ui.waitForText("transactions found");
        assertThat(ui.isTextVisible("DR")).as("Debit transaction should appear").isTrue();

        return TestResult.builder().status(TestStatus.PASSED).build();
    }
}
