package com.payvizio.sdk

/**
 * Sink for checkout lifecycle events. Methods run on the main thread.
 *
 * Exactly one of [onSuccess] / [onFailure] / [onClose] fires per invocation of
 * [Payvizio.checkout]; [onUpdate] may fire any number of times before that.
 */
interface PaymentCallback {
    fun onUpdate(result: PaymentResult) {}
    fun onSuccess(result: PaymentResult)
    fun onFailure(result: PaymentResult)
    fun onClose() {}
}
