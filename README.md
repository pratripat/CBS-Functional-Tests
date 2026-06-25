# CBS Functional Tests — Banking Platform

A dummy banking platform built with **Spring Boot** and **Java 17**, designed to
demonstrate complex microservice interactions, idempotency, and distributed
transaction history. This platform serves as the primary target for the
[Automation Framework](https://github.com/pratripat/Automation-Framework).

---

## 🏗️ Architecture

The platform consists of four core microservices interacting over HTTP/REST:

```
Client
  └─→ Channel Service        (:8080)  — API gateway, aggregates downstream services
          ├─→ Account Service         (:8081)  — account CRUD, balance updates
          ├─→ Funds Transfer Service  (:8082)  — orchestrates CBS calls, idempotency
          │         └─→ CBS Mock      (WireMock) — simulates Flexcube/Finacle
          │         └─→ Transaction History Service
          └─→ Transaction History Service (:8083) — audit ledger
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

---

## 🛠️ Technology Stack

- **Java 17**
- **Spring Boot 3.2**
- **PostgreSQL 15** (persistence)
- **Docker & Docker Compose** (containerisation)
- **Maven** (build management)
- **Automation Framework v1.0.12** (functional testing)

---

## 🚀 Getting Started

### Prerequisites

- Docker & Docker Desktop
- Maven 3.8+
- Java 17+
- A GitHub Personal Access Token with `read:packages` scope (for pulling the
  framework JAR from GitHub Packages)

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

### Build and Test (Automated)

The easiest way to build the entire platform and run all functional tests:

```bash
./rebuild-and-test.sh
```

This script will:
1. Compile all four microservices into JARs
2. Build fresh Docker images for each service
3. Execute the full functional test suite against live containers

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

## 🧪 Functional Testing

Tests live in the `banking-functional-tests` module and use the
[Automation Framework](https://github.com/pratripat/Automation-Framework)
to deploy real Docker containers — Postgres, WireMock CBS mock, and all four
microservices — for every test suite run.

### Run all suites

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

### Test suites at a glance

| Suite | Tests | What it covers |
|---|---|---|
| `AccountServiceIT` | 10 | Account CRUD, balance credit/debit, dormant account rules |
| `FundsTransferServiceIT` | 8 | Happy path, CBS error codes, idempotency, timeout, validation |
| `TransactionHistoryServiceIT` | 6 | Record, query, paginate, idempotency |
| `EndToEndIT` | 8 | Full stack via channel service — real service-to-service calls |

---

## 📁 Repository Structure

```
CBS-Functional-Tests/
├── account-service/               ← account management microservice
├── channel-service/               ← API gateway microservice
├── funds-transfer-service/        ← transfer orchestration microservice
├── transaction-history-service/   ← audit ledger microservice
├── banking-functional-tests/      ← functional test suites (main test module)
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
├── banking-integration-tests/     ← lower-level integration tests
├── docker-compose.yml             ← local development stack
├── rebuild-and-test.sh            ← full build + test orchestration script
└── pom.xml
```

---

## 🔗 Related

- [Automation Framework](https://github.com/pratripat/Automation-Framework) — the
  testing framework this project is built on

---

*This project is part of the Automation Framework demonstration suite.*