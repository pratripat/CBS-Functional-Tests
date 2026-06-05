package com.banking.tests;

import com.banking.testframework.lifecycle.IntegrationTestExtension;
import com.banking.testframework.reporting.SuiteRunReport;
import com.banking.testframework.test.TestResult;
import com.banking.testframework.test.TestStatus;
import com.banking.tests.suites.AccountServiceSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(IntegrationTestExtension.class)
@IntegrationTestExtension.SuiteDefinitionClass(AccountServiceSuite.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Account Service Integration Tests")
class AccountServiceIT {

    @Test @DisplayName("All account service tests pass")
    void allPass(SuiteRunReport r) {
        assertThat(r.failedCount() + r.errorCount())
                .as(failureSummary(r)).isZero();
    }

    @Test @DisplayName("ACC-001: Create account")
    void acc001(SuiteRunReport r) { assertPassed(r, "ACC-001"); }

    @Test @DisplayName("ACC-002: Get account")
    void acc002(SuiteRunReport r) { assertPassed(r, "ACC-002"); }

    @Test @DisplayName("ACC-003: Get balance")
    void acc003(SuiteRunReport r) { assertPassed(r, "ACC-003"); }

    @Test @DisplayName("ACC-004: Credit balance")
    void acc004(SuiteRunReport r) { assertPassed(r, "ACC-004"); }

    @Test @DisplayName("ACC-005: Debit balance")
    void acc005(SuiteRunReport r) { assertPassed(r, "ACC-005"); }

    @Test @DisplayName("ACC-006: Insufficient funds debit")
    void acc006(SuiteRunReport r) { assertPassed(r, "ACC-006"); }

    @Test @DisplayName("ACC-007: Account not found")
    void acc007(SuiteRunReport r) { assertPassed(r, "ACC-007"); }

    @Test @DisplayName("ACC-008: Get accounts by customer")
    void acc008(SuiteRunReport r) { assertPassed(r, "ACC-008"); }

    @Test @DisplayName("ACC-009: Update status to DORMANT")
    void acc009(SuiteRunReport r) { assertPassed(r, "ACC-009"); }

    @Test @DisplayName("ACC-010: Debit dormant account fails")
    void acc010(SuiteRunReport r) { assertPassed(r, "ACC-010"); }

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
