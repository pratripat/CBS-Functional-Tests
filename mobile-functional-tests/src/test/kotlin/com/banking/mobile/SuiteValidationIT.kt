package com.banking.mobile

import com.banking.mobile.suites.MobileFunctionalSuite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kotlin integration test that validates the mobile test suite structure.
 *
 * This test verifies that MobileFunctionalSuite defines exactly the 7 expected
 * test cases (MOB-001 through MOB-007) and that their dependency graph is
 * internally consistent. It runs without an emulator or Appium — it's a pure
 * configuration validation.
 */
class SuiteValidationIT {

    @Test
    fun `all seven MOB tests are registered in the suite`() {
        val suite = MobileFunctionalSuite()
        val definitions = suite.testCases

        val ids = definitions.map { def -> def.id }.toSet()
        val expected = setOf("MOB-001", "MOB-002", "MOB-003", "MOB-004",
                             "MOB-005", "MOB-006", "MOB-007")

        assertEquals(expected, ids, "Suite must define all 7 MOB test cases")
    }

    @Test
    fun `every test depends on MOB-001 directly or transitively`() {
        val suite = MobileFunctionalSuite()
        val definitions = suite.testCases

        val dependsOnMap = definitions.associate { def -> def.id to def.dependsOn }

        for ((id, deps) in dependsOnMap) {
            if (id == "MOB-001") {
                assertTrue(deps.isEmpty(), "MOB-001 should have no dependencies")
                continue
            }
            // Check direct dependency on MOB-001
            if (deps.contains("MOB-001")) continue
            // Check transitive dependency: one of our dependencies depends on MOB-001
            val transitive = deps.any { depId ->
                dependsOnMap[depId]?.contains("MOB-001") == true
            }
            assertTrue(transitive,
                "$id should depend on MOB-001 directly or transitively. " +
                "Current deps: $deps")
        }
    }

    @Test
    fun `no duplicate test IDs exist in the suite`() {
        val suite = MobileFunctionalSuite()
        val ids = suite.testCases.map { def -> def.id }
        val uniqueIds = ids.toSet()

        assertEquals(ids.size, uniqueIds.size,
            "Test case IDs must be unique. Duplicates: " +
            ids.groupBy { it }.filter { it.value.size > 1 }.keys)
    }
}
