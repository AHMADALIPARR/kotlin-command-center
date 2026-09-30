// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SnapKitty Collective

plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    application apply false
}

// Shared dependency versions for all modules.
ext["ktorVersion"] = "2.3.12"
ext["coroutinesVersion"] = "1.8.1"
ext["serializationVersion"] = "1.7.3"
ext["jschVersion"] = "0.2.21"
ext["junitVersion"] = "5.10.3"
