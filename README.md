# Banking Platform Microservices

A dummy banking platform built with **Spring Boot** and **Java 17**, designed to demonstrate complex microservice interactions, idempotency, and distributed transaction history. This platform serves as the primary target for the [Functional Test Framework](https://github.com/pratripat/Automation-Framework).

## 🏗️ Architecture

The platform consists of four core microservices interacting over HTTP/REST:

1.  **Channel Service** (`:8080`): The API Gateway/Edge service that aggregates data from downstream services and provides a unified API for client applications.
2.  **Account Service** (`:8081`): Manages the lifecycle of customer accounts, including balance updates and status (Active, Dormant, etc.). Backed by Postgres.
3.  **Funds Transfer Service** (`:8082`): Orchestrates money movement between accounts. Features strict idempotency keys to prevent duplicate transfers.
4.  **Transaction History Service** (`:8083`): An asynchronous audit log that records every credit and debit operation across the platform.

## 🛠️ Technology Stack

- **Java 17**
- **Spring Boot 3.x**
- **PostgreSQL** (for persistence)
- **Docker & Docker Compose** (for containerization)
- **Maven** (for build management)

## 🚀 Getting Started

### Prerequisites
- **Docker & Docker Desktop**
- **Maven 3.8+**
- **Java 17**

### Build and Test (Automated)
The easiest way to build the entire platform and run tests is using the provided orchestration script:

```bash
./rebuilt-and-test.sh
```

This script will:
1. Rebuild the core test framework.
2. Compile all microservices.
3. Build fresh Docker images for each service.
4. Execute the full functional test suite.

### Manual Build
To build the service JARs without running tests:

```bash
mvn clean package -DskipTests
```

### Docker Deployment
You can spin up the infrastructure using Docker Compose:

```bash
docker-compose up -d
```

## 🧪 Functional Testing

The platform includes a robust set of tests in the `banking-functional-tests` module. These tests use **Testcontainers** to spin up a "real" environment (Postgres, WireMock, and the Services) for every test suite.

To run the functional tests specifically:

```bash
mvn verify -pl banking-functional-tests -am
```

## 📁 Repository Structure

- `account-service/`: Source code and Dockerfile for account management.
- `channel-service/`: Source code and Dockerfile for the edge service.
- `funds-transfer-service/`: Source code and Dockerfile for transfer logic.
- `transaction-history-service/`: Source code and Dockerfile for the audit log.
- `banking-functional-tests/`: High-level integration and E2E tests.
- `banking-integration-tests/`: Lower-level integration tests.

---
*This project is part of the Integration Testing Framework demonstration suite.*
