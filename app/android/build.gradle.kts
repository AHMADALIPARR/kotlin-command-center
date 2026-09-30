// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SnapKitty Collective
//
// Android module. Requires the Android SDK — NOT compiled on the build box
// (see android/README.md). On a machine with the SDK, add `include(":android")`
// to settings.gradle.kts and sync in Android Studio.

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "cc.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "cc.android.commandcenter"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
