# ScratchFade (Android)

A small open-source Android app project: choose a photo from your device, scratch the silver layer to reveal it, then the photo fades out and the cover resets automatically.

## Build the APK

### From the command line

1. Install a JDK 17 and the Android SDK (platform 35 and build-tools 35.0.0).
2. Point the build at the SDK, either with `ANDROID_HOME` or by creating
   `local.properties` with `sdk.dir=/path/to/android-sdk`.
3. Run `./gradlew assembleDebug` from this directory.
4. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### With Android Studio

1. Install Android Studio on a computer.
2. Open this `ScratchFade` folder in Android Studio.
3. Let Gradle sync and install the Android SDK platform 35 if prompted.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will show the location of `app-debug.apk`.

The project uses Kotlin and Android's built-in photo picker/document chooser. Photos are processed locally; the app has no network permission and does not upload them.

## Behavior

- Select a photo using **Choose a photo**.
- Once a photo is loaded, the picker button is hidden so the trick can be performed without it on screen.
- Scratch with your finger to reveal the photo.
- After enough of the cover is scratched, wait briefly; the photo fades out and the cover resets with the same photo.
- **Reset scratch card** resets it immediately.
- To choose a different photo while the picker is hidden, long-press the **ScratchFade** title.
- The selected photo is kept when the device rotates.

License: MIT (see `LICENSE`).

## Build in GitHub Actions (without installing Android Studio locally)

The repository includes `.github/workflows/build-apk.yml`. Upload/push this project to a GitHub repository, then open **Actions → Build Android APK → Run workflow**. When it finishes, download the `ScratchFade-debug-apk` artifact. This produces a debug APK, not a Play Store release-signed APK.
