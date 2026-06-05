package com.banking.tests;

import com.banking.testframework.lifecycle.IntegrationTestExtension;
import com.banking.testframework.reporting.SuiteRunReport;
import com.banking.testframework.test.TestResult;
import com.banking.testframework.test.TestStatus;
import com.banking.tests.suites.TransactionHistoryServiceSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(IntegrationTestExtension.class)
@IntegrationTestExtension.SuiteDefinitionClass(TransactionHistoryServiceSuite.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Transaction History Service Integration Tests")
class TransactionHistoryServiceIT {

    @Test @DisplayName("All history service tests pass")
    void allPass(SuiteRunReport r) {
        assertThat(r.failedCount() + r.errorCount())
                .as(failureSummary(r)).isZero();
    }

    @Test @DisplayName("TH-001: Record debit transaction")
    void th001(SuiteRunReport r) { assertPassed(r, "TH-001"); }

    @Test @DisplayName("TH-002: Record credit transaction")
    void th002(SuiteRunReport r) { assertPassed(r, "TH-002"); }

    @Test @DisplayName("TH-003: Get transaction by ref")
    void th003(SuiteRunReport r) { assertPassed(r, "TH-003"); }

    @Test @DisplayName("TH-004: Paginated transaction history")
    void th004(SuiteRunReport r) { assertPassed(r, "TH-004"); }

    @Test @DisplayName("TH-005: Idempotent record")
    void th005(SuiteRunReport r) { assertPassed(r, "TH-005"); }

    @Test @DisplayName("TH-006: Unknown transaction 500")
    void th006(SuiteRunReport r) { assertPassed(r, "TH-006"); }

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
