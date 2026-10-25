# Magic Wipe for Android

A phone-sized re-creation of **Simon Pierro's Magic Wipe**. Choose a "screen" image and a hidden "message", press **START**, hand the phone over, and the audience wipes the display with a finger to reveal the message under soft wooden-heart strokes.

The Android Gradle project lives in `ScratchFade/`; the module and package keep that original name. GitHub Actions workflows live at the repository root in `.github/workflows/`.

See [`ScratchFade/README.md`](ScratchFade/README.md) for the full feature list and performing tips.

## Features

- Setup screen with a live preview of the wipe, **START**, **Set Screen.**, **Set Message.** and **INFO**.
- Full-screen performance mode with immersive system bars and Keep Screen On.
- Finger wiping erases heart-shaped strokes and reveals the chosen message.
- Automatic fade to the full message after roughly a third of the screen is wiped.
- Tap after the reveal to perform again; long-press (or Back) to return to setup.
- Two chosen images are copied into app-private storage and survive restarts.
- Procedurally drawn default beach screen and wooden-heart *I love you.* message.
- No network permission, no storage permission, and no uploads.

## Build the APK on GitHub

1. Commit and push the changes to `main` (or `master`). A build also starts for pull requests targeting those branches.
2. In GitHub, open the repository's **Actions** tab and select **Build Magic Wipe APK**.
3. Open the completed run and download the **MagicWipe-debug-apk** artifact.
4. Extract the artifact ZIP; it contains `app-debug.apk`. Transfer that APK to your Android phone and install it. If Android blocks the installation, allow your browser/file manager to install unknown apps for this one install.

You can also start a build manually using **Actions → Build Magic Wipe APK → Run workflow**.

## What the workflow does

- Builds from the actual `ScratchFade/` subdirectory.
- Sets up Java 17, Gradle 8.9, and the Android SDK on the hosted runner.
- Installs Android platform 35 and build-tools 35.0.0.
- Runs the Robolectric unit tests and `lintDebug`.
- Fails if the APK wasn't produced, instead of publishing a misleading empty artifact.
- Uploads the debug APK as a downloadable workflow artifact.

The workflow builds an unsigned-for-Play-Store **debug APK** for direct testing. It does not publish a Play Store release or create a release-signed APK. The workflow itself must run on GitHub after these files are pushed.

## Local command-line build

The project includes a Gradle wrapper, so you only need JDK 17 and the Android SDK (platform 35 and build-tools 35.0.0):

```sh
cd ScratchFade
ANDROID_HOME=/path/to/android-sdk ./gradlew assembleDebug
```

The APK is written to `ScratchFade/app/build/outputs/apk/debug/app-debug.apk`.
