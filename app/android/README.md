# :android — status: source-complete, NOT compiled here

This module is the phone UI: a Compose chat screen (agent) and a server-admin
screen (SSH quick actions + custom commands), both driven by `:core` through
`CommandCenterViewModel`.

## Why it is not in the build

The build box has **no Android SDK** (no `ANDROID_HOME`, no platform
tooling), so `settings.gradle.kts` deliberately excludes `:android`.
Claiming it compiles here would be dishonest — it does not, and cannot,
until it runs under a real SDK.

## How to build it for real

1. Open `app/` in Android Studio (or set `ANDROID_HOME` on a machine with
   the SDK + `platforms;android-34`).
2. Add `include(":android")` to `settings.gradle.kts`.
3. Add to `gradle.properties` (or `local.properties`):
   `android.useAndroidX=true`
4. Sync and run — the module applies `com.android.application` and depends
   on `:core` like `:desktop` does.

## Before shipping to a phone

- **Secrets**: `MainActivity` currently calls `CommandCenterConfig.fromEnv()`
  as a placeholder. On device, store `APP_SECRET`, the Cloudflare service
  token, and the SSH key in **EncryptedSharedPreferences** (or the
  Keystore-backed equivalent) behind a settings screen — never in
  `BuildConfig`, never in plain prefs.
- **SSH key**: generate on-device or import via the system file picker;
  keep it out of app backups (`android:allowBackup="false"` for the key).
- **known_hosts**: ship a pinned entry for `ssh.yourdomain.com` (see the
  `SshClient` docs) and refuse to connect without it.
