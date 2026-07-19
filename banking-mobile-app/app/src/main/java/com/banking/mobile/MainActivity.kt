package com.banking.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.banking.mobile.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private var baseUrl: String = BuildConfig.BASE_URL
    private var accountBaseUrl: String = BuildConfig.ACCOUNT_BASE_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cfg = resolveConfig(intent)
        baseUrl = cfg.first
        accountBaseUrl = cfg.second

        findViewById<Button>(R.id.btnBalanceCheck).setOnClickListener {
            toggleScreen(R.id.screenBalanceCheck)
        }
        findViewById<Button>(R.id.btnAccountInquiry).setOnClickListener {
            toggleScreen(R.id.screenAccountInquiry)
        }
        findViewById<Button>(R.id.btnFundsTransfer).setOnClickListener {
            toggleScreen(R.id.screenFundsTransfer)
        }
        findViewById<Button>(R.id.btnDeposit).setOnClickListener {
            toggleScreen(R.id.screenDeposit)
        }
        findViewById<Button>(R.id.btnTransactionHistory).setOnClickListener {
            toggleScreen(R.id.screenTransactionHistory)
        }

        findViewById<Button>(R.id.btnCheckBalance).setOnClickListener {
            val account = inputText(R.id.inputAccountNumber)
            if (account.isNotBlank()) fetchBalance(account)
        }
        findViewById<Button>(R.id.btnInquire).setOnClickListener {
            val account = inputText(R.id.inputInquiryAccount)
            if (account.isNotBlank()) fetchAccountInquiry(account)
        }
        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            val debit = inputText(R.id.inputDebitAccount)
            val credit = inputText(R.id.inputCreditAccount)
            val amount = inputText(R.id.inputAmount)
            val currency = inputText(R.id.inputCurrency)
            if (debit.isNotBlank() && credit.isNotBlank() && amount.isNotBlank()) {
                transferFunds(debit, credit, amount, currency)
            }
        }
        findViewById<Button>(R.id.btnDepositSubmit).setOnClickListener {
            val account = inputText(R.id.inputDepositAccount)
            val amount = inputText(R.id.inputDepositAmount)
            val desc = inputText(R.id.inputDepositDescription)
            if (account.isNotBlank() && amount.isNotBlank()) {
                deposit(account, amount, desc)
            }
        }
        findViewById<Button>(R.id.btnGetMiniStatement).setOnClickListener {
            val account = inputText(R.id.inputHistoryAccount)
            if (account.isNotBlank()) fetchMiniStatement(account)
        }
    }

    private fun toggleScreen(screenId: Int) {
        val screen = findViewById<LinearLayout>(screenId)
        screen.visibility = if (screen.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun inputText(editTextId: Int): String =
        findViewById<EditText>(editTextId).text.toString()

    private fun showResult(textViewId: Int, text: String) {
        val tv = findViewById<TextView>(textViewId)
        tv.text = text
        tv.visibility = View.VISIBLE
    }

    /**
     * Resolves service URLs from intent extras, falling back to BuildConfig.
     * Supports runtime injection via:
     *   adb shell am start -e BASE_URL http://10.0.2.2:8080 ...
     */
    private fun resolveConfig(intent: Intent?): Pair<String, String> {
        var base = BuildConfig.BASE_URL
        var acct = BuildConfig.ACCOUNT_BASE_URL
        intent?.extras?.let { extras ->
            extras.getString("BASE_URL")?.let { base = it }
            extras.getString("ACCOUNT_BASE_URL")?.let { acct = it }
        }
        return Pair(base, acct)
    }

    // ── API ──────────────────────────────────────────────────────────────────

    private fun fetchBalance(accountNumber: String) {
        val url = "$baseUrl/api/v1/accounts/$accountNumber/balance"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(callback(R.id.tvBalanceResult) { body ->
            try {
                val resp = gson.fromJson(body, BalanceResponse::class.java)
                showResult(
                    R.id.tvBalanceResult,
                    "Account: ${resp.accountNumber}\n" +
                            "Available Balance: ${resp.currency} ${formatAmount(resp.availableBalance)}"
                )
            } catch (e: Exception) {
                showResult(R.id.tvBalanceResult, "❌ Parse error: ${e.message}")
            }
        })
    }

    private fun fetchAccountInquiry(accountNumber: String) {
        val url = "$baseUrl/api/v1/accounts/$accountNumber"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(callback(R.id.tvInquiryResult) { body ->
            try {
                val resp = gson.fromJson(body, AccountResponse::class.java)
                showResult(
                    R.id.tvInquiryResult,
                    "Account: ${resp.accountNumber}\n" +
                            "Name: ${resp.accountName}\n" +
                            "Type: ${resp.accountType}\n" +
                            "Status: ${resp.status}"
                )
            } catch (e: Exception) {
                showResult(R.id.tvInquiryResult, "❌ Parse error: ${e.message}")
            }
        })
    }

    private fun transferFunds(debit: String, credit: String, amount: String, currency: String) {
        val idempotencyKey = UUID.randomUUID().toString()
        val payload = TransferRequest(
            debitAccountNumber = debit,
            creditAccountNumber = credit,
            amount = amount.toBigDecimalOrNull() ?: return,
            currency = currency.ifBlank { "INR" },
            idempotencyKey = idempotencyKey,
            remarks = "Mobile transfer"
        )
        val json = gson.toJson(payload)
        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/funds-transfer")
            .post(body)
            .build()

        client.newCall(request).enqueue(callback(R.id.tvTransferResult) { responseBody ->
            try {
                val resp = gson.fromJson(responseBody, TransferResponse::class.java)
                if (resp.status == "SUCCESS") {
                    showResult(
                        R.id.tvTransferResult,
                        "Status: ${resp.status}\n" +
                                "Txn Ref: ${resp.txnReferenceNumber}\n" +
                                "Amount: ${resp.currency} ${formatAmount(resp.amount)}"
                    )
                } else {
                    showResult(
                        R.id.tvTransferResult,
                        "❌ Transfer failed: ${resp.status}"
                    )
                }
            } catch (e: Exception) {
                showResult(R.id.tvTransferResult, "❌ Parse error: ${e.message}")
            }
        })
    }

    private fun deposit(accountNumber: String, amount: String, description: String) {
        val payload = DepositRequest(
            amount = amount.toBigDecimalOrNull() ?: return,
            description = description.ifBlank { "Mobile deposit" }
        )
        val json = gson.toJson(payload)
        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$accountBaseUrl/api/v1/accounts/$accountNumber/balance")
            .put(body)
            .build()

        client.newCall(request).enqueue(callback(R.id.tvDepositResult) {
            showResult(
                R.id.tvDepositResult,
                "Status: Deposit successful\nAmount: $amount ✅"
            )
        })
    }

    private fun fetchMiniStatement(accountNumber: String) {
        val url = "$baseUrl/api/v1/accounts/$accountNumber/mini-statement"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(callback(R.id.tvHistoryResult) { body ->
            try {
                val resp = gson.fromJson(body, MiniStatementResponse::class.java)
                val lines = mutableListOf("${resp.totalCount} transactions found")
                for (txn in resp.transactions) {
                    lines.add("${txn.txnType} - ${txn.currency} ${formatAmount(txn.amount)}")
                }
                showResult(R.id.tvHistoryResult, lines.joinToString("\n"))
            } catch (e: Exception) {
                showResult(R.id.tvHistoryResult, "❌ Parse error: ${e.message}")
            }
        })
    }

    // ── Callback helper ──────────────────────────────────────────────────────

    private fun callback(textViewId: Int, onSuccess: (String) -> Unit): okhttp3.Callback {
        return object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    showResult(textViewId, "❌ Error: ${e.message}")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    runOnUiThread { onSuccess(body) }
                } else {
                    runOnUiThread {
                        showResult(textViewId, "❌ Error ${response.code}: $body")
                    }
                }
            }
        }
    }

    private fun formatAmount(value: Double): String = "%.2f".format(value)

    // ── API Models ───────────────────────────────────────────────────────────

    data class BalanceResponse(
        @SerializedName("accountNumber") val accountNumber: String,
        @SerializedName("availableBalance") val availableBalance: Double,
        @SerializedName("currency") val currency: String
    )

    data class AccountResponse(
        @SerializedName("accountNumber") val accountNumber: String,
        @SerializedName("accountName") val accountName: String,
        @SerializedName("accountType") val accountType: String,
        @SerializedName("status") val status: String
    )

    data class TransferRequest(
        @SerializedName("debitAccountNumber") val debitAccountNumber: String,
        @SerializedName("creditAccountNumber") val creditAccountNumber: String,
        @SerializedName("amount") val amount: Number,
        @SerializedName("currency") val currency: String,
        @SerializedName("idempotencyKey") val idempotencyKey: String,
        @SerializedName("remarks") val remarks: String
    )

    data class TransferResponse(
        @SerializedName("txnReferenceNumber") val txnReferenceNumber: String,
        @SerializedName("status") val status: String,
        @SerializedName("amount") val amount: Double,
        @SerializedName("currency") val currency: String
    )

    data class DepositRequest(
        @SerializedName("amount") val amount: Number,
        @SerializedName("description") val description: String
    )

    data class MiniStatementResponse(
        @SerializedName("totalCount") val totalCount: Int,
        @SerializedName("transactions") val transactions: List<TransactionItem>
    )

    data class TransactionItem(
        @SerializedName("txnType") val txnType: String,
        @SerializedName("amount") val amount: Double,
        @SerializedName("currency") val currency: String
    )
}
