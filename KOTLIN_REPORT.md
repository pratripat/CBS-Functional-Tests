# Kotlin Test Integration Report

## Objective
Restructure the mobile functional tests to use **Kotlin as the primary test language**, while preserving the existing Java tests as a fallback safety net.

---

## What Was Done

### 1. Naming Convention
- Kotlin test classes use the suffix **`KotlinIT`** — e.g. `SuiteValidationKotlinIT`
- Java test classes keep the suffix **`IT`** — e.g. `MobileFunctionalIT`
- This allows Maven to distinguish Kotlin from Java tests at the compiled-class level (since `.class` files don't carry source-language metadata)

### 2. Failsafe Include Patterns
**Default (no profile):** Only `**/*KotlinIT` is included — only Kotlin tests run.
```bash
mvn verify -pl mobile-functional-tests -am -Dapk.path=...
```

**With `-Prun-java-tests` profile:** Includes `**/*IT` — both Java (`*IT`) and Kotlin (`*KotlinIT`) tests run.
```bash
mvn verify -pl mobile-functional-tests -am -Prun-java-tests -Dapk.path=...
```

### 3. Maven Profiles
Added a `<profile>` with `id=run-java-tests` to `mobile-functional-tests/pom.xml` that overrides the failsafe includes:
```xml
<profile>
    <id>run-java-tests</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <includes combine.self="override">
                        <include>**/*IT</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

### 4. Test File: `SuiteValidationKotlinIT.kt`
Created a Kotlin integration test at `mobile-functional-tests/src/test/kotlin/com/banking/mobile/SuiteValidationKotlinIT.kt` with 3 tests:

| Test | Purpose |
|------|---------|
| `all seven MOB tests are registered in the suite` | Verifies `MobileFunctionalSuite` defines exactly MOB-001 through MOB-007 |
| `every test depends on MOB-001 directly or transitively` | Validates dependency graph consistency |
| `no duplicate test IDs exist in the suite` | Ensures all test case IDs are unique |

These tests validate the suite structure and run without an emulator (~0.5s).

### 5. Build Script: `rebuild-and-test.sh`
- Added `--java-tests` flag that passes `-Prun-java-tests` to Maven
- Default behavior: Kotlin-only (no flag needed)
- Help text updated to reflect both modes

### 6. AGENTS.md
Updated with Kotlin-first conventions and build commands.

### 7. Compilation
- Added `kotlin.version=1.9.22` property to parent `pom.xml`
- Added `kotlin-maven-plugin:test-compile` + `kotlin-stdlib` / `kotlin-test` dependencies to `mobile-functional-tests/pom.xml`
- Kotlin test sources are in `src/test/kotlin/`, Java in `src/test/java/`

---

## Verification Results

### Kotlin-only (default)
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
SuiteValidationKotlinIT — 3 tests passed
MobileFunctionalIT — NOT RUN
BUILD SUCCESS
```

### Java fallback (`-Prun-java-tests` with explicit Java test)
```
Tests run: 8, Failures: 8, Errors: 0, Skipped: 0
MobileFunctionalIT — 8 tests run (fail due to no emulator, as expected)
BUILD FAILURE (expected)
```

### Java fallback with Kotlin test (`-Prun-java-tests -Dit.test=SuiteValidationKotlinIT`)
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
SuiteValidationKotlinIT — 3 tests passed
BUILD SUCCESS
```

---

## Usage

```bash
# Default: Kotlin tests only
./rebuild-and-test.sh

# Include Java fallback tests
./rebuild-and-test.sh --java-tests

# Or using Maven directly:
mvn verify -pl mobile-functional-tests -am -Dapk.path=...              # Kotlin only
mvn verify -pl mobile-functional-tests -am -Prun-java-tests -Dapk.path=...  # + Java
```

---

## File Manifest

| File | Purpose |
|------|---------|
| `mobile-functional-tests/src/test/kotlin/com/banking/mobile/SuiteValidationKotlinIT.kt` | Kotlin test (validates suite structure) |
| `mobile-functional-tests/pom.xml` | Kotlin plugin + dependencies + failsafe includes + profile |
| `pom.xml` (parent) | `kotlin.version` property |
| `rebuild-and-test.sh` | `--java-tests` flag support |
| `AGENTS.md` | Kotlin-first conventions documented |
