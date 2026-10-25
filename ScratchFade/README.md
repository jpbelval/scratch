# ScratchFade (Android)

A small open-source Android app project: choose a photo from your device, scratch the silver layer to reveal it, then the photo fades out and the cover resets automatically.

## Build the APK

This workspace does not include the Android SDK or Gradle, so an APK could not be compiled here. To build it:

1. Install Android Studio on a computer.
2. Open this `ScratchFade` folder in Android Studio.
3. Let Gradle sync and install the Android SDK platform 35 if prompted.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will show the location of `app-debug.apk`.

The project uses Kotlin and Android's built-in photo picker/document chooser. Photos are processed locally; the app has no network permission and does not upload them.

## Behavior

- Select a photo using **Choose a photo**.
- Scratch with your finger.
- After enough of the cover is scratched, wait briefly; the photo fades out and the cover resets.
- **Reset scratch card** resets it immediately.

License: MIT (see `LICENSE`).

## Build in GitHub Actions (without installing Android Studio locally)

The repository includes `.github/workflows/build-apk.yml`. Upload/push this project to a GitHub repository, then open **Actions → Build Android APK → Run workflow**. When it finishes, download the `ScratchFade-debug-apk` artifact. This produces a debug APK, not a Play Store release-signed APK.
