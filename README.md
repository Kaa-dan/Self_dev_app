# WorkTrack

Standalone native **Kotlin** Android app. Personal-use planner + device utilities,
to be branched into an employee version later.

Features (built in phases):
- **Reminders** — scheduled local notifications.
- **Files** — browse / import / view files on the device.
- **Monitor** — screen capture via MediaProjection (Android shows a visible
  capture indicator while recording — this cannot be removed).

On-device only; no backend. Data exists only while the app is installed.

## Stack
- Gradle 9.4.1 · AGP 9.2.1 · Kotlin 2.2.21 · compileSdk 37 · minSdk 26
- Classic AndroidX Views (appcompat + Material 3)

## Build (no-sudo local toolchain)
```bash
cd ~/dev/worktrack
JAVA_HOME=/home/wac/.local/jdk/jdk-17.0.20.1+1 \
ANDROID_HOME=/home/wac/.local/android-sdk \
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
Install: `~/.local/bin/adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Phases
- [x] Phase 1: buildable skeleton (bottom nav: Reminders / Files / Monitor)
- [x] Phase 2: Reminders (AlarmManager exact alarms + notifications, local JSON store)
- [x] Phase 3: Files (SAF import into app storage, list, text/video preview, delete)
- [ ] Phase 5: Monitor (MediaProjection screen capture to local file)
