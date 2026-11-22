# Agent HUD for Android

Native Kotlin + Jetpack Compose companion app for Agent HUD, connecting directly to your desktop over local Wi-Fi or Tailscale with pinned self-signed TLS.

## Features

- **Pinned LAN Pairing**: Scan the desktop QR code (`Settings → Local Network → Share with paired phones → Create invitation`), open an `agenthud://pair?payload=…` link, or paste it manually. Credentials are stored encrypted with Android Keystore AES-256-GCM.
- **Live Quotas & Balances**: Real-time SSE sync (`/v1/events`) with incremental `/v1/changes` updates, deduplicated window labels, Obsidian / Sage / Terracotta progress bars, reset countdowns, burn-rate outlooks, and API balances.
- **30-Day Token Usage & Costs**: `Today`, `7d`, and `30d` breakdowns with daily bar chart, cache hit rate, estimated list cost, and per-agent / per-model stats.
- **Live Sessions, Approvals & Messaging**: View active sessions and inter-agent messages, answer Permission / Question / Plan approval cards remotely, read transcripts, send follow-up prompts (with voice dictation), or start a new session in any desktop project folder.
- **Home Screen Widget & Live Notification**: Jetpack Glance widget pinned to your Android home screen, plus a foreground monitor notification with active session count, top quota %, and inline **Allow / Deny** buttons.

## Build & Test

```bash
cd src/apps/android
./gradlew testDebugUnitTest assembleDebug
```

Debug APK output: `src/apps/android/app/build/outputs/apk/debug/app-debug.apk`.
