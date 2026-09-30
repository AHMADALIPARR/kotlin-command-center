// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SnapKitty Collective

plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("cc.desktop.MainKt")
}
