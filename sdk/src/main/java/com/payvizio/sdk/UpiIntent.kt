package com.payvizio.sdk

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * Helper for launching a UPI Intent (`upi://pay?...`) into the user's UPI app.
 * Server returns the deep link via `POST /api/payments/{sessionId}/upi/intent`;
 * pass it here to fire the OS chooser. Status updates flow through the
 * standard webhook + status-poll pipeline (use [Payvizio.checkout] for the
 * full UX, or wire your own status polling for a fully-native flow).
 */
object UpiIntent {

    /**
     * Launch [intentUrl] (a `upi://pay?...` string from the server). Returns
     * `true` when an app picked it up; `false` when no UPI app is installed.
     *
     * Use [requestCode] with [Activity.startActivityForResult] when you need
     * to detect the user's return — the result code from a UPI app is not
     * standardised, so always reconcile with the server status.
     */
    @JvmStatic
    @JvmOverloads
    fun launch(activity: Activity, intentUrl: String, requestCode: Int = REQUEST_CODE): Boolean {
        require(intentUrl.startsWith("upi://")) {
            "intentUrl must be a upi:// scheme; got: $intentUrl"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
        return try {
            activity.startActivityForResult(intent, requestCode)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Default request code used when caller doesn't supply one. */
    const val REQUEST_CODE = 0xBEEF
}
