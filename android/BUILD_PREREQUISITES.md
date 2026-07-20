## Prerequisites

- **JDK 17** (Android Studio JBR or Temurin 17)
- **Android SDK 34** via Android Studio SDK Manager

Set `JAVA_HOME` before building:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat test assembleDebug
```

If Android Studio is installed, open the `android/` folder and use **Build → Make Project**.
