# AGENTS.md — Mobile Functional Tests

## Objective
Pass all 7 mobile functional tests (MOB-001 through MOB-007) by making the Android app call the real banking backend services.

## Important Details
- Emulator runs on host with `-gpu host` (Mesa Intel GPU)
- Service containers (Testcontainers) use **random host ports** — the APK hardcodes `http://10.0.2.2:8080` (channel-service) and `http://10.0.2.2:8081` (account-service)
- Fixed with a `PortForwarder` Java TCP proxy: listens on 8080/8081 and forwards to the actual dynamic ports; started/stopped in `MobileFunctionalSuite.beforeAll()`/`afterAll()`
- Channel-service is the single gateway; all API calls should go through `BuildConfig.BASE_URL` (port 8080), except deposit which goes directly to account-service via `BuildConfig.ACCOUNT_BASE_URL`
- Funds-transfer-service calls CBS mock at `POST /funds-transfer`, not `/cbs/operations/funds-transfer`
- Funds-transfer-service has a per-transaction limit of 1,000,000
- `tapButton("Deposit")` and `tapDashboardButton("Deposit")` both match the dashboard toggle's text; the submit button was renamed to "Submit Deposit" to disambiguate
- All tests use `mvn verify -pl mobile-functional-tests -am -Dit.test=MobileFunctionalIT -Dapk.path=... -Dfailsafe.failIfNoSpecifiedTests=false`

## Results
All 7 tests pass. Build command:
```bash
./gradlew assembleDebug  # in banking-mobile-app/
mvn verify -pl mobile-functional-tests -am -Dit.test=MobileFunctionalIT -Dapk.path=$(pwd)/banking-mobile-app/app/build/outputs/apk/debug/app-debug.apk -Dfailsafe.failIfNoSpecifiedTests=false
```

## File Manifest
- `banking-mobile-app/app/src/main/java/com/banking/mobile/MainActivity.kt` — Kotlin activity with OkHttp calls
- `banking-mobile-app/app/build.gradle` — BuildConfig fields for BASE_URL (port 8080) and ACCOUNT_BASE_URL (port 8081)
- `mobile-functional-tests/src/test/java/com/banking/mobile/components/PortForwarder.java` — TCP proxy utility
- `mobile-functional-tests/src/test/java/com/banking/mobile/components/AndroidEmulatorComponent.java` — Host-based emulator manager
- `mobile-functional-tests/src/test/java/com/banking/mobile/suites/MobileFunctionalSuite.java` — Test suite with CBS stubs, port forwarding, test data
- `banking-functional-tests/src/test/java/com/banking/tests/stubs/CbsStubs.java` — CBS mock response builders
