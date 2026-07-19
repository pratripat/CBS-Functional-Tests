package com.banking.mobile.components;

import com.banking.testframework.container.DeployableComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AndroidEmulatorComponent implements DeployableComponent {

    private static final Logger log = LoggerFactory.getLogger(AndroidEmulatorComponent.class);

    public static final String APK_PATH_IN_CONTAINER = "/tmp/app.apk";
    private static final int APPIUM_PORT = 4723;
    private static final String AVD_NAME = "test_device";
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(10);

    private static final int HEALTH_CHECK_INTERVAL_MS = 15_000;
    private static final int APPIUM_RESTART_WAIT_MS = 5_000;
    private static final int MAX_RECOVERY_ATTEMPTS = 3;

    private final String alias;
    private final List<String> dependsOn;
    private final String apkPath;

    private Process emulatorProcess;
    private Process appiumProcess;
    private volatile boolean started;

    private final AtomicBoolean adbHealthy = new AtomicBoolean(true);
    private final AtomicInteger recoveryCount = new AtomicInteger(0);
    private volatile Thread healthCheckThread;

    public AndroidEmulatorComponent(String alias, List<String> dependsOn, String apkPath) {
        this.alias = alias;
        this.dependsOn = Collections.unmodifiableList(dependsOn);
        this.apkPath = apkPath;
    }

    public static AndroidEmulatorComponent withDefaults(String apkPath) {
        return new AndroidEmulatorComponent("android-emulator", List.of(), apkPath);
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public List<String> getDependsOn() {
        return dependsOn;
    }

    @Override
    public String getComponentType() {
        return "android-emulator";
    }

    @Override
    public void build(Network network, Map<String, String> extraEnv) {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome == null || androidHome.isEmpty()) {
            androidHome = System.getProperty("user.home") + "/Android/Sdk";
        }
        log.info("[{}] Using ANDROID_HOME={}", alias, androidHome);

        if (!new File("/dev/kvm").exists()) {
            log.warn("[{}] /dev/kvm not found — emulator will be VERY slow", alias);
        } else {
            log.info("[{}] /dev/kvm found — KVM acceleration enabled", alias);
        }

        if (!new File(apkPath).exists()) {
            log.warn("[{}] APK not found at {}", alias, apkPath);
        } else {
            log.info("[{}] APK found at {} ({} bytes)", alias, apkPath, new File(apkPath).length());
        }
    }

    @Override
    public void start() {
        log.info("[{}] Starting Android emulator on host...", alias);

        startEmulator();
        waitForAdbDevice();
        waitForBootCompleted();
        installApk();
        startAppium();
        waitForAppium();
        startHealthCheck();

        started = true;
        log.info("[{}] Android emulator fully ready. Appium at {}", alias, getBaseUrl());
    }

    private void startEmulator() {
        log.info("[{}] Launching emulator '{}'...", alias, AVD_NAME);
        try {
            String androidHome = getAndroidHome();

            String emulatorBin = androidHome + "/emulator/emulator";
            List<String> cmd = List.of(
                    emulatorBin,
                    "-avd", AVD_NAME,
                    "-no-window",
                    "-gpu", "host",
                    "-no-audio",
                    "-no-boot-anim",
                    "-no-snapshot",
                    "-memory", "2048",
                    "-cores", "4"
            );

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(new File("/tmp/emulator-" + alias + ".log")));
            emulatorProcess = pb.start();
            log.info("[{}] Emulator PID: {}", alias, emulatorProcess.pid());
        } catch (IOException e) {
            throw new RuntimeException("[" + alias + "] Failed to start emulator", e);
        }
    }

    private void startAppium() {
        log.info("[{}] Starting Appium server on port {}...", alias, APPIUM_PORT);
        try {
            List<String> cmd = List.of(
                    "appium",
                    "--address", "0.0.0.0",
                    "--port", String.valueOf(APPIUM_PORT),
                    "--log", "/tmp/appium-" + alias + ".log",
                    "--log-level", "info"
            );

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(new File("/tmp/appium-" + alias + ".log")));
            appiumProcess = pb.start();
            log.info("[{}] Appium PID: {}", alias, appiumProcess.pid());
        } catch (IOException e) {
            throw new RuntimeException("[" + alias + "] Failed to start Appium", e);
        }
    }

    private void waitForAdbDevice() {
        log.info("[{}] Waiting for ADB device (timeout {})...", alias, BOOT_TIMEOUT);
        long deadline = System.currentTimeMillis() + BOOT_TIMEOUT.toMillis();
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                String output = runAdb("devices");
                long deviceLines = output.lines()
                        .filter(l -> l.contains("device") && !l.contains("List"))
                        .count();
                if (deviceLines > 0) {
                    log.info("[{}] ADB device connected (attempt {})", alias, attempt);
                    return;
                }
            } catch (Exception e) {
                log.debug("[{}] adb devices error (attempt {}): {}", alias, attempt, e.getMessage());
            }
            sleep(10_000);
        }
        throw new RuntimeException("[" + alias + "] ADB device never appeared after " + BOOT_TIMEOUT);
    }

    private void waitForBootCompleted() {
        log.info("[{}] Waiting for boot_completed (timeout {})...", alias, BOOT_TIMEOUT);
        long deadline = System.currentTimeMillis() + BOOT_TIMEOUT.toMillis();
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                String output = runAdb("shell", "getprop", "sys.boot_completed");
                if ("1".equals(output.trim())) {
                    log.info("[{}] Boot completed (attempt {})", alias, attempt);
                    return;
                }
            } catch (Exception e) {
                log.warn("[{}] getprop error (attempt {}): {} — restarting ADB", alias, attempt, e.getMessage());
                restartAdb();
            }
            sleep(15_000);
        }
        throw new RuntimeException("[" + alias + "] Boot never completed after " + BOOT_TIMEOUT);
    }

    private void restartAdb() {
        try {
            String adbBin = getAdbBin();

            new ProcessBuilder(adbBin, "kill-server").start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            sleep(2_000);
            new ProcessBuilder(adbBin, "start-server").start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            sleep(5_000);
        } catch (Exception e) {
            log.warn("[{}] ADB restart failed: {}", alias, e.getMessage());
        }
    }

    private void installApk() {
        log.info("[{}] Installing APK...", alias);
        try {
            String output = runAdb("install", "-r", apkPath);
            log.info("[{}] APK install result: {}", alias, output.trim());
        } catch (Exception e) {
            log.warn("[{}] APK install failed: {}", alias, e.getMessage());
        }
    }

    // ── Health check ──────────────────────────────────────────────────────────

    private void startHealthCheck() {
        healthCheckThread = new Thread(() -> {
            log.info("[{}] Health-check thread started (interval: {}s)", alias, HEALTH_CHECK_INTERVAL_MS / 1000);
            while (started && !Thread.currentThread().isInterrupted()) {
                sleep(HEALTH_CHECK_INTERVAL_MS);
                try {
                    String output = runAdb("shell", "echo", "ok");
                    if (!output.trim().equals("ok")) {
                        throw new RuntimeException("ADB response mismatch: " + output.trim());
                    }
                    adbHealthy.set(true);
                } catch (Exception e) {
                    log.warn("[{}] ADB health-check failed: {}", alias, e.getMessage());
                    adbHealthy.set(false);
                    recoverAdb();
                }
            }
        }, "adb-health-" + alias);
        healthCheckThread.setDaemon(true);
        healthCheckThread.start();
    }

    private void recoverAdb() {
        int attempts = 0;
        while (attempts < MAX_RECOVERY_ATTEMPTS) {
            attempts++;
            log.warn("[{}] ADB recovery attempt {}/{}", alias, attempts, MAX_RECOVERY_ATTEMPTS);
            try {
                restartAdb();
                // Verify ADB works after restart
                String output = runAdb("shell", "echo", "ok");
                if (output.trim().equals("ok")) {
                    log.info("[{}] ADB recovered on attempt {}", alias, attempts);
                    recoveryCount.incrementAndGet();

                    // Restart Appium since the ADB bridge was re-established
                    restartAppiumProcess();
                    adbHealthy.set(true);
                    return;
                }
            } catch (Exception e) {
                log.warn("[{}] Recovery attempt {} failed: {}", alias, attempts, e.getMessage());
            }
            sleep(10_000);
        }
        log.error("[{}] ADB recovery failed after {} attempts — tests will likely fail", alias, MAX_RECOVERY_ATTEMPTS);
    }

    private void restartAppiumProcess() {
        try {
            if (appiumProcess != null && appiumProcess.isAlive()) {
                log.info("[{}] Stopping Appium (PID {}) for restart", alias, appiumProcess.pid());
                appiumProcess.destroyForcibly();
                appiumProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            sleep(APPIUM_RESTART_WAIT_MS);
            startAppium();
            waitForAppium();
            log.info("[{}] Appium restarted successfully", alias);
        } catch (Exception e) {
            log.warn("[{}] Appium restart failed: {}", alias, e.getMessage());
        }
    }

    public boolean isAdbHealthy() {
        return adbHealthy.get();
    }

    public int getRecoveryCount() {
        return recoveryCount.get();
    }

    // ── ADB helper ────────────────────────────────────────────────────────────

    private String runAdb(String... args) throws Exception {
        String adbBin = getAdbBin();

        List<String> cmd = new ArrayList<>();
        cmd.add(adbBin);
        cmd.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        return output.trim();
    }

    private String getAdbBin() {
        return getAndroidHome() + "/platform-tools/adb";
    }

    private String getAndroidHome() {
        String home = System.getenv("ANDROID_HOME");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("user.home") + "/Android/Sdk";
        }
        return home;
    }

    private void waitForAppium() {
        log.info("[{}] Waiting for Appium on port {}...", alias, APPIUM_PORT);
        long deadline = System.currentTimeMillis() + 120_000;

        while (System.currentTimeMillis() < deadline) {
            try {
                Process p = new ProcessBuilder("sh", "-c",
                        "nc -z localhost " + APPIUM_PORT + " && echo OK")
                        .redirectErrorStream(true).start();
                String output = new String(p.getInputStream().readAllBytes());
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if ("OK\n".equals(output)) {
                    log.info("[{}] Appium ready on port {}", alias, APPIUM_PORT);
                    sleep(2_000);
                    return;
                }
            } catch (Exception e) {
                log.debug("[{}] Appium not ready yet: {}", alias, e.getMessage());
            }
            sleep(5_000);
        }
        log.warn("[{}] Appium did not confirm ready within 120s — proceeding anyway", alias);
    }

    @Override
    public void stop() {
        started = false;

        if (healthCheckThread != null) {
            healthCheckThread.interrupt();
            healthCheckThread = null;
        }

        if (appiumProcess != null && appiumProcess.isAlive()) {
            log.info("[{}] Stopping Appium (PID {})", alias, appiumProcess.pid());
            appiumProcess.destroyForcibly();
        }
        if (emulatorProcess != null && emulatorProcess.isAlive()) {
            log.info("[{}] Stopping emulator (PID {})", alias, emulatorProcess.pid());
            emulatorProcess.destroyForcibly();
        }
        started = false;
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + APPIUM_PORT;
    }

    public int getAppiumPort() {
        return APPIUM_PORT;
    }

    @Override
    public String getRecentLogs(int maxLines) {
        return "(host-based emulator — logs at /tmp/emulator-*.log)";
    }

    @Override
    public Map<String, String> getExposedEnvironment() {
        if (!started) {
            return Map.of();
        }
        return Map.of(
                "APPIUM_URL", getBaseUrl(),
                "APPIUM_PORT", String.valueOf(APPIUM_PORT)
        );
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
