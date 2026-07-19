package com.banking.mobile.context;

import com.banking.mobile.components.AndroidEmulatorComponent;
import com.banking.testframework.test.HttpTestClient;
import com.banking.testframework.test.TestContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.util.Map;

public class AppiumTestContext extends TestContext {

    private static final Logger log = LoggerFactory.getLogger(AppiumTestContext.class);
    private static final int MAX_DRIVER_RETRIES = 3;

    private final AndroidEmulatorComponent emulatorComponent;
    private final String apkPath;
    private AndroidDriver driver;

    public AppiumTestContext(Map<String, String> serviceUrls,
            Map<String, WireMockServer> mockServers,
            HttpTestClient httpClient,
            Map<String, String> suiteProperties,
            AndroidEmulatorComponent emulatorComponent,
            String apkPath) {
        super(serviceUrls, mockServers, httpClient, suiteProperties);
        this.emulatorComponent = emulatorComponent;
        this.apkPath = apkPath;
    }

    public AndroidDriver getDriver() {
        if (driver == null) {
            driver = createDriverWithRetry();
        }
        return driver;
    }

    public WebDriverWait wait(int seconds) {
        return new WebDriverWait(getDriver(), Duration.ofSeconds(seconds));
    }

    public void restartApp() {
        if (driver != null) {
            try {
                driver.terminateApp("com.banking.mobile");
                Thread.sleep(1000);
                driver.activateApp("com.banking.mobile");
                Thread.sleep(2000);
            } catch (Exception e) {
                log.warn("Failed to restart app: {}", e.getMessage());
            }
        }
    }

    public void quitDriver() {
        if (driver != null) {
            try {
                driver.quit();
                driver = null;
                log.info("Appium driver quit successfully");
            } catch (Exception e) {
                log.warn("Error quitting Appium driver: {}", e.getMessage());
            }
        }
    }

    private AndroidDriver createDriverWithRetry() {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_DRIVER_RETRIES; attempt++) {
            try {
                log.info("Creating Appium driver (attempt {}/{})", attempt, MAX_DRIVER_RETRIES);
                AndroidDriver d = createDriver();
                log.info("Appium driver created successfully on attempt {}", attempt);
                return d;
            } catch (Exception e) {
                lastException = e;
                log.warn("Driver creation failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt < MAX_DRIVER_RETRIES) {
                    log.info("Waiting 15s before retry...");
                    sleep(15_000);
                }
            }
        }
        throw new RuntimeException(
                "Failed to create Appium driver after " + MAX_DRIVER_RETRIES + " attempts",
                lastException);
    }

    private AndroidDriver createDriver() throws Exception {
        String appiumUrl = emulatorComponent.getBaseUrl();
        log.info("Connecting to Appium at {}", appiumUrl);

        UiAutomator2Options options = new UiAutomator2Options()
                .setApp(apkPath)
                .setAppPackage("com.banking.mobile")
                .setAppActivity(".MainActivity")
                .setNoReset(false)
                .setAutoGrantPermissions(true)
                .setNewCommandTimeout(Duration.ofSeconds(300))
                .setAndroidInstallTimeout(Duration.ofMillis(120000));

        AndroidDriver d = new AndroidDriver(new URL(appiumUrl), options);
        sleep(4000); // Wait for app to fully launch
        return d;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
