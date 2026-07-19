package com.banking.mobile;

import com.banking.testframework.lifecycle.IntegrationTestExtension;
import com.banking.testframework.reporting.SuiteRunReport;
import com.banking.testframework.test.TestResult;
import com.banking.testframework.test.TestStatus;
import com.banking.mobile.suites.MobileFunctionalSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit 5 runner for the mobile functional test suite.
 *
 * Prerequisites before running:
 *   1. Build Docker images:        cd banking-platform && ./rebuild-and-test.sh (build only)
 *   2. Build the Android APK:      cd banking-mobile-app && ./gradlew assembleDebug \
 *                                    -PBASE_URL=http://host.docker.internal:8080
 *   3. Run this suite:             mvn verify -pl mobile-functional-tests \
 *                                    -Dit.test=MobileFunctionalIT \
 *                                    -Dapk.path=banking-mobile-app/app/build/outputs/apk/debug/app-debug.apk
 *
 * Note: The emulator container takes 3-6 minutes to boot on first run.
 * Subsequent runs are faster if Docker caches the image.
 */
@ExtendWith(IntegrationTestExtension.class)
@IntegrationTestExtension.SuiteDefinitionClass(MobileFunctionalSuite.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Mobile Functional Tests — Banking Platform Android App")
class MobileFunctionalIT {

    @Test
    @DisplayName("All mobile tests pass")
    void allPass(SuiteRunReport report) {
        assertThat(report.failedCount() + report.errorCount())
                .as(failureSummary(report))
                .isZero();
    }

    @Test @DisplayName("MOB-001: Dashboard loads with all 5 operation buttons")
    void mob001(SuiteRunReport r) { assertPassed(r, "MOB-001"); }

    @Test @DisplayName("MOB-002: Balance Check displays balance from API")
    void mob002(SuiteRunReport r) { assertPassed(r, "MOB-002"); }

    @Test @DisplayName("MOB-003: Account Inquiry displays account details")
    void mob003(SuiteRunReport r) { assertPassed(r, "MOB-003"); }

    @Test @DisplayName("MOB-004: Funds Transfer success shows txn reference")
    void mob004(SuiteRunReport r) { assertPassed(r, "MOB-004"); }

    @Test @DisplayName("MOB-005: Insufficient funds shows error card")
    void mob005(SuiteRunReport r) { assertPassed(r, "MOB-005"); }

    @Test @DisplayName("MOB-006: Deposit success shows confirmation")
    void mob006(SuiteRunReport r) { assertPassed(r, "MOB-006"); }

    @Test @DisplayName("MOB-007: Transaction history shows mini statement")
    void mob007(SuiteRunReport r) { assertPassed(r, "MOB-007"); }

    private void assertPassed(SuiteRunReport r, String id) {
        TestResult result = r.getResults().stream()
                .filter(t -> t.getTestId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Test not found: " + id));
        assertThat(result.getStatus())
                .as("[%s] %s — %s", id, result.getTestName(), result.getFailureMessage())
                .isEqualTo(TestStatus.PASSED);
    }

    private String failureSummary(SuiteRunReport r) {
        return r.getResults().stream().filter(t -> t.isNotPassed())
                .map(t -> "\n  " + t.getTestId() + " [" + t.getStatus() + "] " + t.getFailureMessage())
                .reduce("", String::concat);
    }
}
