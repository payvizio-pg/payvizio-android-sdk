# JavascriptInterface methods are invoked via reflection from the WebView —
# Proguard would otherwise strip postMessage(...). Keep the bridge intact.
-keepclassmembers class com.payvizio.sdk.internal.CheckoutActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Public API consumed by integrators — stable surface, don't rename.
-keep class com.payvizio.sdk.Payvizio { *; }
-keep class com.payvizio.sdk.PayvizioConfig { *; }
-keep class com.payvizio.sdk.PaymentCallback { *; }
-keep class com.payvizio.sdk.PaymentResult { *; }
-keep class com.payvizio.sdk.PaymentResult$Status { *; }
-keep class com.payvizio.sdk.UpiIntent { *; }
