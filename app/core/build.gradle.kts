// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SnapKitty Collective

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val ktorVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra
val serializationVersion: String by rootProject.extra
val jschVersion: String by rootProject.extra
val junitVersion: String by rootProject.extra

dependencies {
    // HTTP client (Worker gateway) + WebSocket client (SSH bridge)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    // SSH — maintained fork of JSch
    implementation("com.github.mwiede:jsch:$jschVersion")

    // Test-only: embedded HTTP + WebSocket servers to prove the client and
    // the SSH-over-WebSocket bridge against real sockets on localhost.
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-server-websockets:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
