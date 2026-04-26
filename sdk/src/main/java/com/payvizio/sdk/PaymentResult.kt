package com.payvizio.sdk

/**
 * Final-state result delivered to [PaymentCallback]. Mirrors the server-side
 * status enum on `GET /api/payments/{sessionId}`.
 */
data class PaymentResult(
    val sessionId: String,
    val status: Status,
    val acquirer: String?      = null,
    val gatewayReference: String? = null,
    val amount: String?        = null,
    val currency: String?      = null,
    /** Filled when status is in [Status.failureStates]. */
    val failureReason: String? = null,
) {
    enum class Status {
        AUTHORIZED, CAPTURED,
        AUTH_FAILED, FAILED, VOIDED, EXPIRED, CANCELLED;

        companion object {
            val successStates = setOf(AUTHORIZED, CAPTURED)
            val failureStates = setOf(AUTH_FAILED, FAILED, VOIDED, EXPIRED, CANCELLED)
            fun fromOrNull(raw: String?): Status? =
                runCatching { raw?.let { valueOf(it) } }.getOrNull()
        }
    }
}
