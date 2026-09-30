// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SnapKitty Collective

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// NOTE: the :android module is intentionally NOT included here. It needs the
// Android SDK, which this build box does not have (see app/android/README.md).
// Add `include(":android")` on a machine with the SDK / Android Studio.
rootProject.name = "kotlin-command-center"
include(":core", ":desktop")
