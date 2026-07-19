# CBS Functional Tests — Banking Platform

A dummy banking platform built with **Spring Boot** and **Java 17**, designed to
demonstrate complex microservice interactions, idempotency, and distributed
transaction history. This platform serves as the primary target for the
[Automation Framework](https://github.com/pratripat/Automation-Framework).

The project includes **7 mobile functional tests** (MOB-001 through MOB-007)
that validate an Android banking app against the same backend microservices
using a host-based emulator and Appium.

---

## 🏗️ Architecture

The platform consists of four core microservices interacting over HTTP/REST,
plus an Android testing layer:

```
                          ┌── Mobile Functional Tests
                          │    ├── Android Emulator (host, KVM)
                          │    │   └── App: com.banking.mobile
                          │    ├── Appium Server (host, :4723)
                          │    └── PortForwarder (8080→channelsvc, 8081→acctsvc)
                          │
Client ─→ Channel Service (:8080) — API gateway
                ├── Account Service         (:8081) — CRUD, balance updates
                ├── Funds Transfer Service  (:8082) — CBS orchestration, idempotency
                │       └── CBS Mock        (WireMock) — simulates Flexcube/Finacle
                │       └── Transaction History Service
                └── Transaction History Service (:8083) — audit ledger
```

1. **Channel Service** (`:8080`) — The API gateway that aggregates data from
   downstream services and provides a unified API for client applications.
2. **Account Service** (`:8081`) — Manages the lifecycle of customer accounts,
   including balance updates and status (Active, Dormant, etc.). Backed by Postgres.
3. **Funds Transfer Service** (`:8082`) — Orchestrates money movement between
   accounts. Features strict idempotency keys to prevent duplicate transfers and
   calls the Core Banking System mock for transaction processing.
4. **Transaction History Service** (`:8083`) — An audit log that records every
   credit and debit operation across the platform.
5. **Mobile Testing Layer** — A host-based Android emulator and Appium server
   drive a Kotlin Android app that makes real HTTP calls to the services above.
   A TCP port forwarder bridges the app's hardcoded ports (8080, 8081) to the
   dynamic container ports assigned by Testcontainers.

---

## 🛠️ Technology Stack

- **Java 17** — microservices
- **Kotlin 1.9.22** — Android app
- **Spring Boot 3.2** — service framework
- **PostgreSQL 15** — persistence
- **Docker & Testcontainers** — service containerisation
- **Maven** — build management
- **OkHttp 4.12 + Gson 2.10** — Android HTTP client
- **Appium 2.x** — mobile UI automation
- **Automation Framework v1.0.12** — functional testing orchestration

---

## 🚀 Getting Started

### Prerequisites

- Docker & Docker Desktop
- Maven 3.8+
- Java 17+
- A GitHub Personal Access Token with `read:packages` scope (for pulling the
  framework JAR from GitHub Packages)

**Mobile testing prerequisites** (optional, only if running mobile tests):

- Linux with KVM support (`/dev/kvm`)
- Android SDK (API 35), environment variable `ANDROID_HOME`
- An emulator AVD named `test_device` (see setup below)
- Node.js with `appium` installed globally and the `uiautomator2` driver
- Emulator GPU backend set to `host` (native OpenGL/Vulkan)

### Setting up GitHub Packages authentication

Add this to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_PERSONAL_ACCESS_TOKEN</password>
        </server>
    </servers>
</settings>
```

### One-time AVD creation (mobile tests only)

```bash
echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager \
  create avd -n test_device -k "system-images;android-35;google_apis;x86_64" \
  -d pixel_6_pro
```

### Build and Test (Automated)

The easiest way to build the entire platform and run all functional tests
(API + mobile, if prerequisites are met):

```bash
./rebuild-and-test.sh
```

This script will:
1. Compile all four microservices into JARs
2. Build fresh Docker images for each service
3. Build the Android APK (if mobile tests are enabled)
4. Execute the API functional test suite against live containers
5. Execute the 7 mobile functional tests (if mobile tests are enabled)

**Flags:**
- `SKIP_MOBILE=true` — skip mobile tests (if emulator/Appium aren't available)
- `SKIP_API=true` — skip API tests (run mobile tests only)

### Manual Build

To build the service JARs without running tests:

```bash
mvn clean package -DskipTests
```

### Docker Deployment (local dev)

Spin up the full platform using Docker Compose:

```bash
docker-compose up -d
```

---

## 🧪 Functional Testing — API Suites

Tests live in the `banking-functional-tests` module and use the
[Automation Framework](https://github.com/pratripat/Automation-Framework)
to deploy real Docker containers — Postgres, WireMock CBS mock, and all four
microservices — for every test suite run.

### Run all API suites

```bash
mvn verify -pl banking-functional-tests -am
```

### Run a specific suite

```bash
# Account service tests
mvn verify -pl banking-functional-tests -am -Dit.test=AccountServiceIT

# Funds transfer tests (CBS mock + idempotency)
mvn verify -pl banking-functional-tests -am -Dit.test=FundsTransferServiceIT

# Transaction history tests
mvn verify -pl banking-functional-tests -am -Dit.test=TransactionHistoryServiceIT

# Full end-to-end (all 6 containers)
mvn verify -pl banking-functional-tests -am -Dit.test=EndToEndIT
```

### API test suites at a glance

| Suite | Tests | What it covers |
|-------|-------|----------------|
| `AccountServiceIT` | 10 | Account CRUD, balance credit/debit, dormant account rules |
| `FundsTransferServiceIT` | 8 | Happy path, CBS error codes, idempotency, timeout, validation |
| `TransactionHistoryServiceIT` | 6 | Record, query, paginate, idempotency |
| `EndToEndIT` | 8 | Full stack via channel service — real service-to-service calls |

---

## 📱 Mobile Functional Testing

7 end-to-end tests (MOB-001 through MOB-007) validate a Kotlin Android app
against the same backend microservices used by the API suites.

### How it works

The emulator and Appium run directly on the host (not inside Docker) for
stability and performance. This decision was made because running QEMU inside
Docker (`budtmo/docker-android`) caused GPU segfaults on Mesa Intel hardware,
added 8-15 minutes to boot time, and provided no ADB recovery mechanism.

The Android APK hardcodes service URLs as `http://10.0.2.2:8080` and
`http://10.0.2.2:8081` (the emulator's host loopback alias). Since
Testcontainers assigns random host ports, a **PortForwarder** Java TCP proxy
listens on the fixed ports and forwards to the actual dynamic ports.

UI interactions use **resource-ID based locators** (`By.id`) instead of
fragile XPath text matching, with XPath fallback only for fields whose hint
text is unique across screens.

A **background health-check daemon** polls ADB every 15 seconds during test
execution. If the ADB bridge stalls, it restarts both ADB and Appium
automatically (up to 3 attempts).

### Test descriptions

| ID | Test | What it validates |
|----|------|-------------------|
| MOB-001 | Dashboard loads | All 5 operation buttons visible |
| MOB-002 | Balance Check | `GET /api/v1/accounts/{id}/balance` via channel-service |
| MOB-003 | Account Inquiry | `GET /api/v1/accounts/{id}` via channel-service |
| MOB-004 | Funds Transfer (success) | `POST /api/v1/funds-transfer` via channel-service, CBS returns SUCCESS |
| MOB-005 | Funds Transfer (insufficient funds) | Error card with ❌ icon shown |
| MOB-006 | Deposit | `PUT /api/v1/accounts/{id}/balance` via account-service |
| MOB-007 | Transaction History | `GET /api/v1/accounts/{id}/mini-statement` via channel-service |

### Run all mobile tests

```bash
SKIP_API=true ./rebuild-and-test.sh
```

Or directly with Maven:

```bash
mvn verify -pl mobile-functional-tests -am \
  -Dit.test=MobileFunctionalIT \
  -Dapk.path=$(pwd)/banking-mobile-app/app/build/outputs/apk/debug/app-debug.apk \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

### How to add a new mobile test

Adding a test involves two steps: write the test method, then register it in the suite.

**Step 1 — Write the test method** in `MobileFunctionalSuite.java`:

```java
private TestResult testMyNewFeature(TestContext ctx) throws Exception {
    // (Optional) Stub CBS mock if your test involves funds-transfer
    ctx.getMockServer(CBS_MOCK).stubFor(
        post(urlEqualTo("/funds-transfer"))
            .willReturn(okJson(CbsStubs.fundsTransferSuccess("REF123"))));

    AppiumActions ui = new AppiumActions(appiumCtx.getDriver());

    // Navigate to screen (uses resource-ID internally)
    ui.tapDashboardButton("Balance Check");

    // Fill input field (falls back to XPath by hint for "Account Number")
    ui.typeInField("Account Number", TEST_ACCOUNT);

    // Click submit button (uses resource-ID map)
    ui.tapButton("Check Balance");

    // Assert result appears
    ui.waitForText("Available Balance");
    assertThat(ui.getTextByResourceId("tvBalanceResult"))
        .as("Balance result should show INR")
        .contains("INR");

    return TestResult.builder().status(TestStatus.PASSED).build();
}
```

**Step 2 — Register the test** in the `MobileFunctionalSuite()` constructor:

```java
addTest(TestCaseDefinition.builder()
    .id("MOB-008")
    .name("My New Feature — description of what it validates")
    .tags(List.of("regression", "my-feature"))
    .dependsOn(List.of("MOB-001"))  // dashboard must load first
    .testCase(this::testMyNewFeature)
    .build());
```

**If you need a new screen or button in the APK:**

1. Add the resource ID in `banking-mobile-app/app/src/main/res/layout/activity_main.xml`
2. Add the screen logic in `MainActivity.kt` (button click → API call → result display)
3. Add entries to the `DASHBOARD_BUTTONS`, `SUBMIT_BUTTONS`, or `INPUT_FIELDS` maps in `AppiumActions.java`
4. Rebuild the APK: `cd banking-mobile-app && ./gradlew assembleDebug`

The infrastructure (emulator boot, Appium, port forwarding, ADB health checks) requires no changes.

### Key mobile components

| File | Role |
|------|------|
| `banking-mobile-app/` | Android app with OkHttp API calls, runtime URL injection |
| `AndroidEmulatorComponent.java` | Host emulator + Appium lifecycle, ADB health monitoring |
| `PortForwarder.java` | TCP proxy: 8080→channelsvc, 8081→accountsvc |
| `AppiumActions.java` | Resource-ID based UI interaction helpers |
| `MobileFunctionalSuite.java` | Test orchestration, data seeding, port forwarding setup |

---

## 📁 Repository Structure

```
CBS-Functional-Tests/
├── account-service/                  ← account management microservice
├── channel-service/                  ← API gateway microservice
├── funds-transfer-service/           ← transfer orchestration microservice
├── transaction-history-service/      ← audit ledger microservice
├── banking-functional-tests/         ← API functional test suites
│   └── src/test/java/com/banking/tests/
│       ├── suites/
│       │   ├── AccountServiceSuite.java
│       │   ├── FundsTransferServiceSuite.java
│       │   ├── TransactionHistoryServiceSuite.java
│       │   └── EndToEndSuite.java
│       ├── stubs/
│       │   └── CbsStubs.java
│       └── util/
│           ├── PostgresComponent.java
│           ├── ServiceImages.java
│           └── TestPayloads.java
├── banking-integration-tests/        ← lower-level integration tests
├── mobile-functional-tests/          ← mobile functional test suites
│   └── src/test/java/com/banking/mobile/
│       ├── suites/
│       │   └── MobileFunctionalSuite.java
│       ├── components/
│       │   ├── AndroidEmulatorComponent.java
│       │   ├── PortForwarder.java
│       │   └── AppiumTestContext.java
│       └── context/
│           ├── AppiumActions.java
│           └── AppiumTestContext.java
├── banking-mobile-app/               ← Android app source (Kotlin + OkHttp)
│   └── app/src/main/java/com/banking/mobile/
│       └── MainActivity.kt
├── docker-compose.yml                ← local development stack
├── rebuild-and-test.sh               ← full build + test orchestration script
├── MOBILE_TESTS_REPORT.md            ← detailed mobile testing documentation
├── AGENTS.md                         ← LLM agent context file
└── pom.xml
```

---

## 🔗 Related

- [Automation Framework](https://github.com/pratripat/Automation-Framework) — the
  testing framework this project is built on
- `MOBILE_TESTS_REPORT.md` — detailed mobile testing architecture, benefits, and
  future improvements

---

*This project is part of the Automation Framework demonstration suite.*
