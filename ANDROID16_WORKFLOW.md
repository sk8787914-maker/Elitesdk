# Android 16 (API 36) Build + Runtime Permission Workflow

## 1) Build workflow
1. Install Android SDK Platform **36** and Build-Tools **36.0.0**.
2. Install NDK **27.2.12479018** (required by this module).
3. Build with Java 17 toolchain.
4. Run module checks/build:
   - `./gradlew :Elitesdk:lint`
   - `./gradlew :Elitesdk:assembleDebug`
   - `./gradlew :Elitesdk:assembleRelease`

> Note: this repository snapshot currently does not contain `gradlew`, so use project root wrapper where available.

## 2) Runtime permission flow (Android 12+ to Android 16)
Use `PermissionUtils.findDangerousPermissions(...)` + `PermissionUtils.startRequestPermissions(...)`.

### Microphone (required)
- `android.permission.RECORD_AUDIO`

### If app keeps mic in foreground service (voice/chat/game streaming)
- Manifest: `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- Also keep: `android.permission.FOREGROUND_SERVICE`

### Media/storage
- Android 13+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`
- Android 14+: optional `READ_MEDIA_VISUAL_USER_SELECTED`

### Nearby/Bluetooth
- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
- Android 13+: `NEARBY_WIFI_DEVICES`

## 3) Validation checklist
1. Open app first time and verify runtime prompt appears for microphone.
2. Deny microphone once and confirm app handles failure path without crash.
3. Grant microphone and verify voice/chat or mic capture path works.
4. On Android 13+ verify media permissions request uses READ_MEDIA_* (not legacy read/write external storage).
5. On Android 12+ verify nearby/bluetooth permission prompts appear when feature is used.
6. Run smoke test for gameplay session:
   - launch game
   - enable mic/voice
   - background/foreground app
   - verify no ANR/crash and stable audio route.
