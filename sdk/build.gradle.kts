plugins {
    id("com.android.library") version "8.4.0"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("maven-publish")
}

android {
    namespace = "com.payvizio.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24                // Android 7.0 — covers >97% of devices
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId    = "com.payvizio"
            artifactId = "payvizio-android-sdk"
            version    = "0.1.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
