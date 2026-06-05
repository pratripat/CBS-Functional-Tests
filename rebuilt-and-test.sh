#!/usr/bin/env bash
# Full rebuild + test script.
# Run from the banking-platform directory.
set -euo pipefail

echo "=========================================="
echo " Step 1: Clone and install Automation-Framework"
echo "=========================================="
FRAMEWORK_REPO="https://github.com/pratripat/Automation-Framework.git"
FRAMEWORK_DIR="temp-automation-framework"

rm -rf "$FRAMEWORK_DIR"
git clone "$FRAMEWORK_REPO" "$FRAMEWORK_DIR" --quiet
cd "$FRAMEWORK_DIR"
mvn clean install -DskipTests -q
cd ..
rm -rf "$FRAMEWORK_DIR"

echo ""
echo "=========================================="
echo " Step 2: Build service JARs"
echo "=========================================="
mvn clean package -DskipTests \
    -pl account-service,funds-transfer-service,transaction-history-service,channel-service \
    -am -q

echo ""
echo "=========================================="
echo " Step 3: Force-rebuild Docker images"
echo "=========================================="
docker build --no-cache -t banking/account-service:latest           ./account-service
docker build --no-cache -t banking/funds-transfer-service:latest    ./funds-transfer-service
docker build --no-cache -t banking/transaction-history-service:latest ./transaction-history-service
docker build --no-cache -t banking/channel-service:latest           ./channel-service

echo ""
echo "=========================================="
echo " Step 4: Verify image timestamps are fresh"
echo "=========================================="
docker images | grep "banking/" | awk '{printf "%-45s %-10s %s %s %s\n", $1, $2, $4, $5, $6}'

echo ""
echo "=========================================="
echo " Step 5: Run functional tests"
echo "=========================================="
mvn verify -pl banking-functional-tests -am
