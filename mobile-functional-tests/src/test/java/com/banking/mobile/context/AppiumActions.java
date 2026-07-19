package com.banking.mobile.context;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

public class AppiumActions {

    private static final Logger log = LoggerFactory.getLogger(AppiumActions.class);
    private static final int DEFAULT_WAIT_SECONDS = 15;

    private static final String PKG = "com.banking.mobile";
    private static final Map<String, String> DASHBOARD_BUTTONS = Map.of(
            "Balance Check", "btnBalanceCheck",
            "Account Inquiry", "btnAccountInquiry",
            "Funds Transfer", "btnFundsTransfer",
            "Deposit", "btnDeposit",
            "Transaction History", "btnTransactionHistory"
    );
    private static final Map<String, String> SUBMIT_BUTTONS = Map.of(
            "Check Balance", "btnCheckBalance",
            "Inquire", "btnInquire",
            "Transfer", "btnTransfer",
            "Submit Deposit", "btnDepositSubmit",
            "Get Mini Statement", "btnGetMiniStatement"
    );
    private static final Map<String, String> INPUT_FIELDS = Map.of(
            "Debit Account Number", "inputDebitAccount",
            "Credit Account Number", "inputCreditAccount",
            "Currency (INR/USD)", "inputCurrency",
            "Description", "inputDepositDescription"
    );

    private final AndroidDriver driver;

    public AppiumActions(AndroidDriver driver) {
        this.driver = driver;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void tapDashboardButton(String label) {
        String id = DASHBOARD_BUTTONS.get(label);
        if (id == null) {
            log.warn("No resource-ID mapping for dashboard button '{}', falling back to XPath", label);
            waitAndClick(By.xpath("//android.widget.Button[contains(@text,'" + label + "')]"));
            return;
        }
        log.debug("Tapping dashboard button: {} ({})", label, id);
        waitAndClick(resId(id));
    }

    public void tapBack() {
        driver.navigate().back();
        sleep(500);
    }

    // ── Form interactions ─────────────────────────────────────────────────────

    public void typeInField(String label, String value) {
        String id = INPUT_FIELDS.get(label);
        if (id != null) {
            typeInFieldById(id, value);
            return;
        }
        log.debug("Typing '{}' into field '{}' (by hint)", value, label);
        WebElement field = waitForElement(
                By.xpath("//android.widget.EditText[@hint='" + label + "']"));
        field.clear();
        field.sendKeys(value);
    }

    public void typeInFieldById(String resourceId, String value) {
        log.debug("Typing '{}' into field {}", value, resourceId);
        WebElement field = waitForElement(resId(resourceId));
        field.clear();
        field.sendKeys(value);
    }

    public void tapButton(String text) {
        String id = SUBMIT_BUTTONS.get(text);
        if (id != null) {
            tapByResourceId(id);
            return;
        }
        log.debug("Tapping button: {} (by text)", text);
        waitAndClick(By.xpath("//android.widget.Button[@text='" + text + "']"));
        sleep(1500);
    }

    public void tapByResourceId(String resourceId) {
        log.debug("Tapping button: {}", resourceId);
        waitAndClick(resId(resourceId));
        sleep(1500);
    }

    // ── Assertions ─────────────────────────────────────────────────────────────

    public WebElement waitForText(String text) {
        log.debug("Waiting for text: {}", text);
        return waitForElement(By.xpath("//*[contains(@text,'" + text + "')]"));
    }

    public WebElement waitForTextInView(String resourceId, String text) {
        log.debug("Waiting for text '{}' in view {}", text, resourceId);
        return waitForElement(
                By.xpath("//*[@resource-id='" + PKG + ":id/" + resourceId + "' and contains(@text,'" + text + "')]"));
    }

    public boolean isTextVisible(String text) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//*[contains(@text,'" + text + "')]")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTextByResourceId(String resourceId) {
        return waitForElement(resId(resourceId)).getText();
    }

    public String getTextByXpath(String xpath) {
        return waitForElement(By.xpath(xpath)).getText();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void waitAndClick(By locator) {
        waitForElement(locator).click();
        sleep(300);
    }

    private WebElement waitForElement(By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_SECONDS))
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    private static By resId(String id) {
        return By.id(PKG + ":id/" + id);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
