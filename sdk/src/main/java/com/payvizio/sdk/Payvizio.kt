package com.payvizio.sdk

import android.app.Activity
import android.content.Intent
import com.payvizio.sdk.internal.CallbackRegistry
import com.payvizio.sdk.internal.CheckoutActivity
import com.payvizio.sdk.internal.SessionFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Entry point. Singleton — call [init] once (typically in your Application's
 * onCreate), then [checkout] from any Activity.
 *
 * Headless by design: the SDK does not render a payment-method picker. The
 * hosted checkout (loaded inside [CheckoutActivity]) renders the appropriate
 * UI for the session — UPI Intent buttons, native QR, BNPL options, and the
 * acquirer's iframe-embedded card form. PCI-out-of-scope for integrators.
 *
 * Pre-warm the network path with [prefetch] for snappier first-use.
 */
object Payvizio {

    private val configRef = AtomicReference<PayvizioConfig?>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic
    fun init(config: PayvizioConfig) {
        require(config.apiBaseUrl.isNotBlank()) { "apiBaseUrl is required" }
        configRef.set(config)
    }

    /**
     * Best-effort warm-up: open a connection to the API host so the first
     * real checkout doesn't pay TCP/TLS handshake costs. Safe to call
     * repeatedly. No-op when [init] hasn't been called.
     */
    @JvmStatic
    fun prefetch() {
        val config = configRef.get() ?: return
        scope.launch { runCatching { SessionFetcher(config).warmUp() } }
    }

    /**
     * Open the hosted checkout for [sessionId]. Result is delivered to
     * [callback] on the main thread. The returned token can be used to
     * dismiss the activity programmatically.
     */
    @JvmStatic
    fun checkout(activity: Activity, sessionId: String, callback: PaymentCallback): CheckoutHandle {
        val config = checkNotNull(configRef.get()) {
            "Payvizio.init() must be called before checkout()"
        }
        require(sessionId.isNotBlank()) { "sessionId is required" }

        val token = CallbackRegistry.register(callback)
        val intent = Intent(activity, CheckoutActivity::class.java).apply {
            putExtra(CheckoutActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(CheckoutActivity.EXTRA_API_BASE_URL, config.apiBaseUrl)
            putExtra(CheckoutActivity.EXTRA_CHECKOUT_URL, config.checkoutUrl)
            putExtra(CheckoutActivity.EXTRA_POLL_INTERVAL_MS, config.pollIntervalMs)
            putExtra(CheckoutActivity.EXTRA_BACK_DISMISSIBLE, config.backButtonDismissible)
            putExtra(CheckoutActivity.EXTRA_CALLBACK_TOKEN, token)
        }
        activity.startActivity(intent)
        return CheckoutHandle(token)
    }

    /** Returns the active config, or null if [init] hasn't been called. Tests/internals only. */
    internal fun activeConfig(): PayvizioConfig? = configRef.get()

    class CheckoutHandle internal constructor(private val token: String) {
        /** Best-effort cancellation: if the checkout activity is still in front, it will close itself. */
        fun cancel() = CallbackRegistry.requestCancel(token)
    }
}
