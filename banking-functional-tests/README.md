# Banking Functional Test Suite

This repository contains the functional and end-to-end integration tests for the **Banking Platform**. These tests are built using the [Functional Test Framework](https://github.com/pratripat/Automation-Framework) and provide comprehensive coverage of the platform's core business flows.

## 🏗️ Architecture Under Test

The test suite orchestrates a full platform environment using **Docker** and **Testcontainers**. It validates the interactions between the following components:

- **Channel Service**: The entry point for client applications.
- **Account Service**: Manages customer accounts and balances.
- **Funds Transfer Service**: Handles complex money movement logic and idempotency.
- **Transaction History Service**: Maintains an audit trail of all operations.
- **Postgres Database**: Shared or isolated database instances for persistence.
- **CBS Mock (WireMock)**: A high-fidelity simulator for the Core Banking System.

## 🧪 Test Coverage

The suite is divided into several logical "Suites," each covering a specific domain:

### 1. Account Service Suite
- Account creation and status management.
- Balance inquiry and updates (Credit/Debit).
- Insufficient funds validation.
- Customer-level account aggregation.

### 2. Funds Transfer Suite
- Successful transfers across accounts.
- Idempotency validation (preventing duplicate transfers).
- Upstream resilience (handling CBS timeouts and unavailability).
- Business rule validation (transaction limits).

### 3. End-to-End Suite
- Full flow validation: **Channel ⮕ Funds Transfer ⮕ CBS ⮕ History**.
- Cross-service data consistency (ensuring a transfer updates both account balances and history).

## 🚀 Getting Started

### Prerequisites
- **Java 17** or higher.
- **Maven 3.8+**.
- **Docker** (must be running and accessible).

### Running the Tests

To run the full suite of functional tests:

```bash
mvn verify
```

To run a specific test suite (e.g., End-to-End):

```bash
mvn verify -Dit.test=EndToEndIT
```

### Viewing Reports

After the tests complete, the framework generates a detailed report in the console. For CI/CD environments, detailed logs and WireMock journals are available in:
`target/functional-test-logs/`

## 🛠️ Infrastructure Management

The tests automatically manage their own infrastructure. For every suite:
1. A dedicated Docker network is created.
2. Required microservices and infrastructure (Postgres, WireMock) are deployed as containers.
3. Health checks are performed to ensure the environment is ready.
4. Tests are executed via a custom JUnit 5 extension.
5. All resources are surgically torn down upon completion.

## 📈 Reporting to S3 (Optional)

You can enable automatic upload of test reports to an S3-compatible bucket by setting the following environment variables:

- `TEST_S3_BUCKET`: The name of your bucket.
- `TEST_S3_REGION`: The AWS region.
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`: Your credentials.

---
*Built with ❤️ using the Integration Test Framework.*
