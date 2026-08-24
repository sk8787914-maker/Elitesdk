# EliteSDK

Android app virtualization engine with GMS / Facebook / Twitter-X login support inside the virtual environment. Target: Android 16 (API 36), minSdk 24.

## Features

- Full app virtualization (activities, services, providers, receivers)
- **AuthCore**: Google Play Services + Facebook + Twitter/X login fix
  - Pass-through mode: host pe real package installed ho to wahi use hota hai
  - Dummy fallback: crash-free dummy info taaki login UI khule
  - Safe mode: koi signature forgery nahi
- Native login detection via `resolveIntent` / `resolveService` / `queryIntentActivities`
- ABIs: arm64-v8a, armeabi-v7a

## Integration

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sk8787914-maker:Elitesdk:aar-v23'
}
```

Ya latest release se AAR directly download karke `libs/` me daalo:

```
https://github.com/sk8787914-maker/Elitesdk/releases/latest
```

## Requirements

| Item | Value |
|------|-------|
| minSdk | 24 |
| compileSdk | 36 |
| Java | 17 |
| NDK | 27.2+ |

## ProGuard

Consumer rules (`proguard.txt`) AAR ke saath bundled hain — host app me extra rules ki zaroorat nahi.

## Build

```bash
gradle clean assembleRelease
```

CI: har push pe build hota hai, `main` push pe AAR automatically GitHub Releases me publish ho jata hai (`aar-v<run_number>` tag).

## Notes

- Android 14+ (API 34) pe dummy components `exported=true` ke saath resolve hote hain
- Android 16 (API 36) pe `longVersionCode` + compileSdk fields set kiye jate hain
