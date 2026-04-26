# Payvizio Android SDK

Headless, pre-warmable Android SDK for embedding Payvizio checkout. Card
collection happens inside the acquirer's iframe inside our hosted checkout —
integrating apps stay **PCI-out-of-scope**. UPI Intent flow is exposed
separately for fully-native UX where you want to drive the deeplink yourself.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // maven { url = uri("https://repo.payvizio.com") } // until published to Central
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.payvizio:payvizio-android-sdk:0.1.0")
}
```

Min SDK: **24** (Android 7.0). compileSdk: **34**.

## Initialize once, in your Application

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Payvizio.init(PayvizioConfig(apiBaseUrl = "https://api.payvizio.com"))
        Payvizio.prefetch()                       // optional: warm the TLS path
    }
}
```

Register the Application in your manifest:

```xml
<application android:name=".MyApp" ... />
```

## Open checkout

```kotlin
val handle = Payvizio.checkout(activity, sessionId = "sess_xxx", object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) { /* show success */ }
    override fun onFailure(result: PaymentResult) { /* show failure */ }
    override fun onClose()                         { /* user dismissed */ }
})
// handle.cancel()  // optional: programmatic dismiss
```

`Payvizio.checkout(...)` opens a translucent activity with a WebView pointed
at the hosted checkout. The merchant page never sees card data; the activity
polls `GET /api/payments/{sessionId}` and bridges `postMessage` events from
inside the WebView back to your callback.

## Native UPI Intent

If your app already has its own checkout UI and you only want the UPI Intent
deeplink to fire into a UPI app:

```kotlin
val intentUrl = /* fetched from POST /api/payments/{sessionId}/upi/intent */
val launched = UpiIntent.launch(activity, intentUrl)
if (!launched) {
    // No UPI app installed — fall back to QR or VPA collect form.
}
```

Always reconcile the final state with the server (status poll or webhook) —
UPI apps don't return a deterministic result code.

## Lifecycle

Exactly **one** of `onSuccess` / `onFailure` / `onClose` fires per
`Payvizio.checkout(...)` invocation. `onUpdate` may fire any number of times
before the terminal callback.

```
checkout()
  → activity opens
    → onUpdate (poll/postMessage)…
    → AUTHORIZED/CAPTURED  → onSuccess → activity finishes
    → AUTH_FAILED/FAILED…  → onFailure → activity finishes
    → user back-press      → onClose   → activity finishes
```

## What this SDK does *not* include

- Native card form (use the acquirer's drop-in inside our hosted checkout)
- 3DS challenge UI (acquirer hosts the ACS page; the WebView navigates through it)
- Saved-card management (use the dashboard or your own UI before opening checkout)

## Versioning

Pre-1.0 — API may change between minor versions. Pin to a specific version in production.
