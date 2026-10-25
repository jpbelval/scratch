# ScratchFade: GitHub Actions APK build fix

This patch matches the layout of `https://github.com/jpbelval/scratch`: the Android Gradle project lives in `ScratchFade/`, while GitHub Actions workflows live at the repository root in `.github/workflows/`.

## Apply this patch

Extract this ZIP into the root of your existing `jpbelval/scratch` checkout and allow the files to merge/overwrite. It adds/replaces `.github/workflows/build-apk.yml` and includes the Android project under `ScratchFade/`.

If your repository already contains the same `ScratchFade/` source, the only required change is `.github/workflows/build-apk.yml`.

## Build the APK on GitHub

1. Commit and push the changes to `main` (or `master`). A build also starts for pull requests targeting those branches.
2. In GitHub, open the repository's **Actions** tab and select **Build ScratchFade APK**.
3. Open the completed run and download the **ScratchFade-debug-apk** artifact.
4. Extract the artifact ZIP; it contains `app-debug.apk`. Transfer that APK to your Android phone and install it. If Android blocks the installation, allow your browser/file manager to install unknown apps for this one install.

You can also start a build manually using **Actions → Build ScratchFade APK → Run workflow**.

## What this workflow fixes

- Builds from the actual `ScratchFade/` subdirectory (rather than the repository root).
- Sets up Java 17, Gradle 8.9, and the Android SDK on the hosted runner.
- Installs Android platform 35 and build-tools 35.0.0.
- Fails if the APK wasn't produced, instead of publishing a misleading empty artifact.
- Uploads the debug APK as a downloadable workflow artifact.

The workflow builds an unsigned-for-Play-Store **debug APK** for direct testing. It does not publish a Play Store release or create a release-signed APK. The build must run on GitHub after these files are pushed; this offline workspace cannot execute GitHub Actions or download Android SDK packages, so the APK has not been compiled here.
