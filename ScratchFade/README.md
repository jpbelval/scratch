# Magic Wipe (Android)

A phone-sized re-creation of **Simon Pierro's Magic Wipe** illusion. Choose the image your audience sees, choose the message the wipe reveals, hand the phone over and let them wipe the screen with a finger. The screen is cleared with soft dot-shaped strokes; once enough of it is gone, the rest fades away and the hidden message takes over.

The Android module and package still use the original `ScratchFade` name, but the installed app is **Magic Wipe**.

## The trick

- **Set Screen.** picks the image shown first — a photo, a lock screen, a picture of the magician, anything.
- **Set Message.** picks the reveal — a note, a photo, or the built-in wooden heart and *I love you.* artwork.
- **START** hides the setup screen, goes edge-to-edge and keeps the display awake.
- Wipe with a finger. Each stroke erases soft dots; when about a third of the screen has been cleared, the rest fades away automatically.
- Once the message is showing, **tap** to set the trick up again, or **long-press the message** (or press Back) to return to the setup screen.
- **INFO** explains the routine and offers **Reset images**.
- Both cards are copied into app-private storage, so the setup survives restarts. Everything is processed locally: no network permission, no uploads, no storage permission.

The app ships with procedurally drawn defaults: a bright beach "screen" and the wooden-heart *I love you.* message. There are no bundled photographs, so the trick works immediately on a fresh install.

## Run the checks

```sh
./gradlew testDebugUnitTest lintDebug
```

The Robolectric tests drive the setup → performance → wipe → replay flow and exercise
card persistence. `lintDebug` should finish with zero errors.

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

## Build in GitHub Actions (without installing Android Studio locally)

The repository includes `.github/workflows/build-apk.yml`. Push the project to GitHub, then open **Actions → Build Magic Wipe APK → Run workflow**. When it finishes, download the `MagicWipe-debug-apk` artifact. This produces a debug APK, not a Play Store release-signed APK.

## Notes for performing

- Use a screen image that the audience has not already seen up close; the reveal is more surprising.
- Wipe with broad, overlapping strokes. The auto-reveal needs roughly a third of the screen cleared.
- Choose a message image with a dark or simple edge: it fills the screen with a centre crop, just like the screen image.
- Lock the phone to portrait (the app already does) and turn on Do Not Disturb before handing it over. Keep Screen On is enabled automatically during the trick.
- Tap once after the reveal to reset instantly. Long-press to exit the performance.

License: MIT (see `LICENSE`).
