package com.payvizio.sdk.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.payvizio.sdk.PaymentCallback
import com.payvizio.sdk.PaymentResult
import com.payvizio.sdk.PayvizioConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Hosts the merchant-checkout WebView and bridges status updates back to the
 * SDK consumer. Exactly one terminal callback fires per instance.
 *
 *   `onUpdate` — every status change (poll or postMessage)
 *   `onSuccess` — AUTHORIZED / CAPTURED → activity finishes
 *   `onFailure` — AUTH_FAILED / FAILED / VOIDED / EXPIRED / CANCELLED → activity finishes
 *   `onClose`   — back press or programmatic cancel without a terminal status
 */
@SuppressLint("SetJavaScriptEnabled")
internal class CheckoutActivity : Activity() {

    companion object {
        const val EXTRA_SESSION_ID         = "payvizio.sessionId"
        const val EXTRA_API_BASE_URL       = "payvizio.apiBaseUrl"
        const val EXTRA_CHECKOUT_URL       = "payvizio.checkoutUrl"
        const val EXTRA_POLL_INTERVAL_MS   = "payvizio.pollIntervalMs"
        const val EXTRA_BACK_DISMISSIBLE   = "payvizio.backDismissible"
        const val EXTRA_CALLBACK_TOKEN     = "payvizio.callbackToken"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var cancelWatcherJob: Job? = null
    private var callback: PaymentCallback? = null
    private var sessionId: String = ""
    private var token: String = ""
    private var pollIntervalMs: Long = 2500L
    private var backDismissible: Boolean = true
    private var terminalDelivered: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionId       = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        token           = intent.getStringExtra(EXTRA_CALLBACK_TOKEN).orEmpty()
        pollIntervalMs  = intent.getLongExtra(EXTRA_POLL_INTERVAL_MS, 2500L)
        backDismissible = intent.getBooleanExtra(EXTRA_BACK_DISMISSIBLE, true)
        val apiBase     = intent.getStringExtra(EXTRA_API_BASE_URL).orEmpty()
        val checkoutUrl = intent.getStringExtra(EXTRA_CHECKOUT_URL)
            ?: "${apiBase.trimEnd('/')}/checkout"

        callback = CallbackRegistry.claim(token)
        if (callback == null || sessionId.isBlank() || apiBase.isBlank()) {
            finish()
            return
        }

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF000000.toInt())  // opaque while WebView paints
        }
        // Lock the JS bridge to the checkout/API origin. Without this, any page
        // the WebView navigates to (open redirect, ACS challenge, third-party
        // script) could call `PayvizioBridge.postMessage(...)` from JS.
        val allowedHosts = buildAllowedHosts(checkoutUrl, apiBase)

        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webViewClient = OriginGuardingClient(allowedHosts) { trusted ->
                if (trusted) {
                    addJavascriptInterface(Bridge(), "PayvizioBridge")
                } else {
                    removeJavascriptInterface("PayvizioBridge")
                }
            }
        }
        container.addView(webView)
        setContentView(container)

        val url = "${checkoutUrl.trimEnd('/')}?session_id=" + java.net.URLEncoder.encode(sessionId, "UTF-8")
        webView.loadUrl(url)

        if (pollIntervalMs > 0) startPolling(apiBase)
        startCancelWatcher()
    }

    override fun onBackPressed() {
        if (backDismissible) {
            finishWithClose()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (!terminalDelivered) {
            // Activity destroyed without a terminal — treat as user-close.
            mainHandler.post { callback?.onClose() }
        }
    }

    // ── Polling ────────────────────────────────────────────────────────────

    private fun startPolling(apiBase: String) {
        val fetcher = SessionFetcher(PayvizioConfig(apiBaseUrl = apiBase, pollIntervalMs = pollIntervalMs))
        pollJob = scope.launch {
            while (isActive) {
                val result = fetcher.fetchStatus(sessionId)
                if (result != null) deliver(result)
                delay(pollIntervalMs)
            }
        }
    }

    private fun startCancelWatcher() {
        cancelWatcherJob = scope.launch {
            while (isActive) {
                if (CallbackRegistry.consumeCancel(token)) {
                    withContext(Dispatchers.Main) { finishWithClose() }
                    return@launch
                }
                delay(500L)
            }
        }
    }

    private fun deliver(result: PaymentResult) {
        if (terminalDelivered) return
        mainHandler.post {
            if (terminalDelivered) return@post
            val cb = callback ?: return@post
            cb.onUpdate(result)
            when {
                result.status in PaymentResult.Status.successStates -> {
                    terminalDelivered = true
                    cb.onSuccess(result)
                    finish()
                }
                result.status in PaymentResult.Status.failureStates -> {
                    terminalDelivered = true
                    cb.onFailure(result)
                    finish()
                }
                else -> { /* non-terminal — keep polling */ }
            }
        }
    }

    private fun finishWithClose() {
        if (terminalDelivered) { finish(); return }
        terminalDelivered = true
        callback?.onClose()
        finish()
    }

    // ── Origin guard ───────────────────────────────────────────────────────

    private fun buildAllowedHosts(vararg urls: String): Set<String> {
        val out = HashSet<String>()
        for (u in urls) {
            try {
                val host = Uri.parse(u).host
                if (!host.isNullOrBlank()) out.add(host.lowercase())
            } catch (_: Exception) { /* skip malformed config */ }
        }
        return out
    }

    /**
     * Cancels any navigation outside the configured checkout/API hosts and
     * detaches the JS bridge while not on a trusted page. The bridge is only
     * (re-)attached after a trusted page has finished loading.
     */
    private class OriginGuardingClient(
        private val allowedHosts: Set<String>,
        private val onTrustedChange: (Boolean) -> Unit,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val host = request?.url?.host?.lowercase() ?: return true
            if (host !in allowedHosts) {
                onTrustedChange(false)
                return true  // cancel navigation
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            val host = url?.let { Uri.parse(it).host?.lowercase() }
            onTrustedChange(host != null && host in allowedHosts)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            val host = url?.let { Uri.parse(it).host?.lowercase() }
            onTrustedChange(host != null && host in allowedHosts)
        }
    }

    // ── JS bridge ──────────────────────────────────────────────────────────

    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(json: String) {
            try {
                val obj = JSONObject(json)
                val sid = obj.optString("sessionId")
                if (sid != sessionId) return
                val statusRaw = obj.optString("status")
                val mapped = PaymentResult.Status.fromOrNull(statusRaw) ?: return
                deliver(PaymentResult(
                    sessionId        = sid,
                    status           = mapped,
                    acquirer         = obj.optString("acquirer").ifBlank { null },
                    gatewayReference = obj.optString("gatewayReference").ifBlank { null },
                    amount           = obj.optString("amount").ifBlank { null },
                    currency         = obj.optString("currency").ifBlank { null },
                    failureReason    = obj.optString("reason").ifBlank { null },
                ))
            } catch (_: Exception) { /* ignore malformed bridge calls */ }
        }
    }
}
