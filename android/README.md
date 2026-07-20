# WhatsApp Bot — Android Local

Personal Android app that runs the WhatsApp bot entirely on-device (no VPS).

## Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Project skeleton, Fake Engine, Compose UI | ✅ |
| 2 | Business logic port from `bot.js` | Pending |
| 3 | Real WhatsApp (Baileys) | Pending |
| 4 | Foreground service + network | Pending |
| 5 | Forward, bulk, backup | Pending |
| 6 | Signed release APK | Pending |

See [MIGRATION_PLAN.md](./MIGRATION_PLAN.md) for full architecture.

## Modules

- **`app`** — Jetpack Compose UI, ViewModels, Navigation
- **`domain`** — Business rules, models, engine contract (pure Kotlin)
- **`data`** — Room database, repositories
- **`engine`** — `FakeWhatsAppEngine` (Phase 1), real engine later

## Build

```bash
cd android
./gradlew test
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

See [BUILD_AND_INSTALL.md](./BUILD_AND_INSTALL.md) for device installation.

## Desktop version

The original Node.js / VPS project at the repo root is **unchanged** on branch `android-local`.
