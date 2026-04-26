package com.payvizio.sdk.internal

import com.payvizio.sdk.PayvizioConfig
import com.payvizio.sdk.PaymentResult
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin wrapper around `GET /api/payments/{sessionId}`. Uses the platform
 * HttpURLConnection — zero third-party deps so the SDK adds <100 KB to the
 * merchant's APK.
 */
internal class SessionFetcher(private val config: PayvizioConfig) {

    fun fetchStatus(sessionId: String): PaymentResult? {
        val url = URL("${config.apiBaseUrl.trimEnd('/')}/api/payments/$sessionId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body, sessionId)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Best-effort warm-up — opens a TCP/TLS connection to the API host. */
    fun warmUp() {
        val url = URL("${config.apiBaseUrl.trimEnd('/')}/actuator/health")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try { conn.responseCode } catch (_: Exception) { /* swallow */ } finally { conn.disconnect() }
    }

    private fun parse(body: String, sessionId: String): PaymentResult? {
        val json = JSONObject(body)
        val statusRaw = json.optString("status").takeIf { it.isNotBlank() } ?: return null
        val mapped = PaymentResult.Status.fromOrNull(statusRaw) ?: return PaymentResult(
            sessionId = sessionId,
            status = PaymentResult.Status.FAILED,
            failureReason = "Unrecognised status: $statusRaw",
        )
        return PaymentResult(
            sessionId        = json.optString("sessionId").ifBlank { sessionId },
            status           = mapped,
            acquirer         = json.optString("acquirer").ifBlank { null },
            gatewayReference = json.optString("gatewayReference").ifBlank { null },
            amount           = json.optString("amount").ifBlank { null },
            currency         = json.optString("currency").ifBlank { null },
        )
    }
}
