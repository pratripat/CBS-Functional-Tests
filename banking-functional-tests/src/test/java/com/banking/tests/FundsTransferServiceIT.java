package com.banking.tests;

import com.banking.testframework.lifecycle.IntegrationTestExtension;
import com.banking.testframework.reporting.SuiteRunReport;
import com.banking.testframework.test.TestResult;
import com.banking.testframework.test.TestStatus;
import com.banking.tests.suites.FundsTransferServiceSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(IntegrationTestExtension.class)
@IntegrationTestExtension.SuiteDefinitionClass(FundsTransferServiceSuite.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Funds Transfer Service Integration Tests")
class FundsTransferServiceIT {

    @Test @DisplayName("All funds transfer tests pass")
    void allPass(SuiteRunReport r) {
        assertThat(r.failedCount() + r.errorCount())
                .as(failureSummary(r)).isZero();
    }

    @Test @DisplayName("FT-001: Successful transfer")
    void ft001(SuiteRunReport r) { assertPassed(r, "FT-001"); }

    @Test @DisplayName("FT-002: Insufficient funds 422")
    void ft002(SuiteRunReport r) { assertPassed(r, "FT-002"); }

    @Test @DisplayName("FT-003: Idempotency")
    void ft003(SuiteRunReport r) { assertPassed(r, "FT-003"); }

    @Test @DisplayName("FT-004: CBS timeout 504")
    void ft004(SuiteRunReport r) { assertPassed(r, "FT-004"); }

    @Test @DisplayName("FT-005: Missing amount 400")
    void ft005(SuiteRunReport r) { assertPassed(r, "FT-005"); }

    @Test @DisplayName("FT-006: Over-limit 400")
    void ft006(SuiteRunReport r) { assertPassed(r, "FT-006"); }

    @Test @DisplayName("FT-007: CBS unavailable 422")
    void ft007(SuiteRunReport r) { assertPassed(r, "FT-007"); }

    @Test @DisplayName("FT-008: History service called after transfer")
    void ft008(SuiteRunReport r) { assertPassed(r, "FT-008"); }

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
