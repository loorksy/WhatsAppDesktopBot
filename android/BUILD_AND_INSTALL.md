# Build & Install (Personal APK)

## Prerequisites

- Android Studio Ladybug+ or JDK 17
- Android SDK 34
- USB debugging enabled on phone

## Build debug APK

```bash
cd android
./gradlew assembleDebug
```

Windows:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Run tests

```bash
./gradlew test
```

## Install on device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it (allow unknown sources).

## Release build (Phase 6)

1. Create keystore:
   ```bash
   keytool -genkey -v -keystore whatsapp-bot.jks -keyalg RSA -keysize 2048 -validity 10000 -alias whatsappbot
   ```
2. Add signing config to `app/build.gradle.kts`
3. `./gradlew assembleRelease`

## Battery optimization

For reliable background operation (Phase 4+):

1. Settings → Apps → WhatsApp Bot → Battery → **Unrestricted**
2. Disable manufacturer “auto-start” restrictions (Xiaomi, Samsung, Huawei, etc.)
3. Keep the foreground notification visible when the bot is running

## Phase 1 note

The debug APK uses **FakeWhatsAppEngine** only. No real WhatsApp connection yet.
