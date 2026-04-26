package com.payvizio.sdk.internal

import com.payvizio.sdk.PaymentCallback
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges merchant-supplied callbacks across the process boundary into
 * CheckoutActivity. Activities can't carry Kotlin lambdas in their Intent
 * extras, so we hand over an opaque token and look the callback up here.
 *
 * Tokens are claimed exactly once — once the activity drains them, the entry
 * is removed so the lambda graph doesn't leak.
 */
internal object CallbackRegistry {

    private val callbacks = ConcurrentHashMap<String, PaymentCallback>()
    private val cancelRequests = ConcurrentHashMap.newKeySet<String>()

    fun register(callback: PaymentCallback): String {
        val token = "PV_" + UUID.randomUUID().toString()
        callbacks[token] = callback
        return token
    }

    fun claim(token: String): PaymentCallback? = callbacks.remove(token)

    fun requestCancel(token: String) {
        cancelRequests.add(token)
    }

    fun consumeCancel(token: String): Boolean = cancelRequests.remove(token)
}
