package com.payvizio.sdk

/**
 * Configuration handed to [Payvizio.init]. Designed to be set once in the
 * Application's onCreate so the first checkout is fast (no cold-network
 * overhead — see [Payvizio.prefetch]).
 */
data class PayvizioConfig(
    /** Public API base, e.g. `https://api.payvizio.com`. No trailing slash required. */
    val apiBaseUrl: String,

    /** Optional override for the hosted-checkout URL. Defaults to `${apiBaseUrl}/checkout`. */
    val checkoutUrl: String? = null,

    /** Status-poll interval. Set to 0 to disable polling and rely solely on the checkout postMessage bridge. */
    val pollIntervalMs: Long = 2500L,

    /**
     * Whether to allow back-button dismissal. When true, pressing back on the
     * checkout activity fires `PaymentCallback.onClose()`. Default true.
     */
    val backButtonDismissible: Boolean = true,
)
