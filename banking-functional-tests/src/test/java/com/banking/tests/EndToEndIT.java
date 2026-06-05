package com.banking.tests;

import com.banking.testframework.lifecycle.IntegrationTestExtension;
import com.banking.testframework.reporting.SuiteRunReport;
import com.banking.testframework.test.TestResult;
import com.banking.testframework.test.TestStatus;
import com.banking.tests.suites.EndToEndSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end JUnit 5 runner.
 *
 * Starts all 4 microservices + Postgres + CBS mock, runs the full test flow
 * through the Channel Service, then tears everything down.
 *
 * Run with:
 *   mvn verify -pl banking-functional-tests -Dit.test=EndToEndIT
 */
@ExtendWith(IntegrationTestExtension.class)
@IntegrationTestExtension.SuiteDefinitionClass(EndToEndSuite.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("End-to-End Platform Integration Tests")
class EndToEndIT {

    @Test @DisplayName("All end-to-end tests pass")
    void allPass(SuiteRunReport r) {
        assertThat(r.failedCount() + r.errorCount())
                .as(failureSummary(r)).isZero();
    }

    @Test @DisplayName("web-console-001: Create accounts")
    void e2e001(SuiteRunReport r) { assertPassed(r, "web-console-001"); }

    @Test @DisplayName("web-console-002: Account inquiry via channel")
    void e2e002(SuiteRunReport r) { assertPassed(r, "web-console-002"); }

    @Test @DisplayName("web-console-003: Balance check via channel")
    void e2e003(SuiteRunReport r) { assertPassed(r, "web-console-003"); }

    @Test @DisplayName("web-console-004: Successful funds transfer via channel")
    void e2e004(SuiteRunReport r) { assertPassed(r, "web-console-004"); }

    @Test @DisplayName("web-console-005: Mini-statement shows transfer")
    void e2e005(SuiteRunReport r) { assertPassed(r, "web-console-005"); }

    @Test @DisplayName("web-console-006: Insufficient funds via channel")
    void e2e006(SuiteRunReport r) { assertPassed(r, "web-console-006"); }

    @Test @DisplayName("web-console-007: Idempotency via channel")
    void e2e007(SuiteRunReport r) { assertPassed(r, "web-console-007"); }

    @Test @DisplayName("web-console-008: Get customer accounts via channel")
    void e2e008(SuiteRunReport r) { assertPassed(r, "web-console-008"); }

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
                .map(t -> t.getTestId() + " [" + t.getStatus() + "] " + t.getFailureMessage())
                .reduce("", (a, b) -> a + "\n  " + b);
    }
}
