#!/usr/bin/env bash
# Builds all service JARs and Docker images required by the functional tests.
# Run this before: mvn verify -pl banking-functional-tests
set -euo pipefail

echo "==> Building all service JARs..."
mvn clean package -DskipTests \
    -pl account-service,funds-transfer-service,transaction-history-service,channel-service \
    -am

echo ""
echo "==> Building Docker images..."
docker build -t banking/account-service:latest           ./account-service
docker build -t banking/funds-transfer-service:latest    ./funds-transfer-service
docker build -t banking/transaction-history-service:latest ./transaction-history-service
docker build -t banking/channel-service:latest           ./channel-service

echo ""
echo "==> Images built:"
docker images | grep "banking/"
echo ""
echo "==> Ready to run functional tests:"
echo "    mvn verify -pl banking-functional-tests -am"
echo "    # Or run a specific suite:"
echo "    mvn verify -pl banking-functional-tests -Dit.test=EndToEndIT"
