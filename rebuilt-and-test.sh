#!/usr/bin/env bash
# No longer need to build the framework — it's pulled from GitHub Packages
set -euo pipefail

echo "=== Building service JARs ==="
mvn clean package -DskipTests \
    -pl account-service,funds-transfer-service,transaction-history-service,channel-service \
    -am -q

echo "=== Building Docker images ==="
docker build --no-cache -t banking/account-service:latest           ./account-service
docker build --no-cache -t banking/funds-transfer-service:latest    ./funds-transfer-service
docker build --no-cache -t banking/transaction-history-service:latest ./transaction-history-service
docker build --no-cache -t banking/channel-service:latest           ./channel-service

echo "=== Running functional tests ==="
mvn verify -pl banking-functional-tests -am