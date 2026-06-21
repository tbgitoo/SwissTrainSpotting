# Manual APK build and install checklist

Use this checklist to produce and sideload an installable APK for **SwissTrainSpottingApp** using **Android Studio** as the primary workflow. All steps are manual; no CI/CD or Play Store publishing is involved.

**Project context:** single-module Android app (`applicationId` `com.tb.swisstrainspotting`), Java + XML, on-device ONNX assets under `app/src/main/assets/`. See `AGENTS.md` for scope and constraints.

---

## Before you build

- [ ] Open the project in Android Studio from the **Android project root** (the directory that contains `settings.gradle` and `gradlew`).
- [ ] Let Gradle sync finish without errors.
- [ ] Confirm required model assets and labels are present under `app/src/main/assets/` (the app expects bundled ONNX artifacts at runtime).
- [ ] Connect a physical device with USB debugging enabled **or** start an emulator with API level ≥ `minSdk` (24).
- [ ] Optional sanity check: **Run** the app once from Android Studio to confirm it launches before packaging an APK.

---

## 1. Build options overview

| Variant | Purpose | Signing | Recommended for |
|---------|---------|---------|-----------------|
| **Debug APK** | Fast local build with debuggable flag | Auto-signed with the local debug certificate | **Testing, review, smoke tests** |
| **Release APK** | Production-style build type (`minifyEnabled` is `false` in this project) | release APK requires a signing configuration to be installable outside Android Studio; **this project does not define release signing in `app/build.gradle`** | Distribution beyond casual sideload testing |

**Recommendation:** use a **debug APK** for manual install and review. It is installable on a connected device or emulator without creating or configuring a release keystore.

Do **not** commit keystores, passwords, or signing credentials to the repository.

---

## 2. Primary method: Android Studio GUI (recommended)

This is the simplest and safest way to produce an APK.

1. [ ] In Android Studio, select the **app** run configuration and a connected device or emulator (optional but helps verify the project is healthy).
2. [ ] From the menu bar: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. [ ] Wait for the build to finish. Check the **Build** tool window for errors.
4. [ ] When the **notification** appears (“APK(s) generated successfully”), click **Locate** to open the output folder in the file browser.
5. [ ] Identify the generated file:
   - Debug build (default for this menu when no release variant is selected): `app-debug.apk`
   - If you explicitly built the release variant via **Build Variants**, look for `app-release.apk` instead.
6. [ ] Copy or share the APK only through appropriate channels; do not embed signing material in documentation or chat.

If the notification is dismissed, use **Build → Analyze APK…** or browse the generic output paths in section 4.

---

## 3. Alternative method: Gradle CLI (optional)

Run from the **Android project root** in a terminal. This is secondary to the Android Studio workflow.

**Debug (recommended for manual testing):**

```bash
./gradlew assembleDebug
```

**Release (only if you have already configured release signing outside this checklist):**

```bash
./gradlew assembleRelease
```

- [ ] Confirm the task completes with `BUILD SUCCESSFUL`.
- [ ] Locate the APK using the output paths in section 4.

On Windows, use `gradlew.bat` instead of `./gradlew`.

---

## 4. Output location (generic only)

After a successful build, APKs are written under the module build outputs tree:

| Build type | Typical relative path | Typical filename |
|------------|----------------------|------------------|
| Debug | `app/build/outputs/apk/debug/` | `app-debug.apk` |
| Release | `app/build/outputs/apk/release/` | `app-release.apk` |

These paths are **generated build artifacts**; they are not source files and should not be committed to version control.

---

## 5. Install the APK on a device

Choose one method.

### Option A — Run from Android Studio (no separate APK step)

- [ ] Click **Run** (green triangle) with a device selected.
- [ ] Android Studio builds, installs, and launches the app. Useful for development; skip sections 2–4 if this meets your goal.

### Option B — Install a built APK with adb

- [ ] Enable **USB debugging** on the device and authorize the workstation when prompted.
- [ ] Verify the device is visible: `adb devices`
- [ ] Install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use `-r` to replace an existing install with the same `applicationId`.

### Option C — Manual sideload (file transfer)

- [ ] Copy `app-debug.apk` to the device (USB, cloud storage, or email — use a channel your organization allows).
- [ ] On the device, open the APK and approve installation from unknown sources if the OS prompts you.
- [ ] Complete the install wizard.

**Note:** Release APKs that are unsigned or signed with a certificate the device does not trust will fail to install; prefer debug for manual review.

---

## 6. Post-install smoke test

- [ ] Launch **SwissTrainSpotting** from the app drawer.
- [ ] On the main screen, tap **Image** (gallery) or **Camera** and complete acquisition.
- [ ] Confirm the classification screen shows a preview and a classification result (or a clear error string from resources, not a crash).
- [ ] Optional: verify OCR section appears when text is detected, or stays hidden when empty (OCR is experimental and non-blocking).
- [ ] Tap **Back** and confirm navigation returns to the main screen without crashing.

Record pass/fail for your own notes; no automated reporting is required.

---

## 7. Troubleshooting (common manual issues)

- [ ] **Gradle sync or build fails:** fix compile errors in the **Build** window before retrying; do not change signing or Gradle files unless you intentionally own that maintenance.
- [ ] **Install failed / app not installed:** uninstall an older build with the same `applicationId`, or use `adb install -r`. Ensure you are installing a **debug** APK for local testing.
- [ ] **Device not detected:** re-enable USB debugging, try another cable/port, or accept the RSA fingerprint prompt on the device.
- [ ] **Classification fails at runtime:** verify ONNX model and label assets exist under `app/src/main/assets/` and match the expected profile layout.
- [ ] **Release build not installable:** expected if release signing is not configured; use `assembleDebug` / **Build APK(s)** debug output instead.

---

## 8. Out of scope for this checklist

- Creating or modifying keystores or `signingConfigs`
- Play Store publishing or internal track uploads
- CI/CD pipelines or automated release jobs
- Changing `app/build.gradle`, Gradle wrapper, or project dependencies

---

## Quick reference

| Goal | Recommended action |
|------|-------------------|
| Fastest path to installable APK | **Build → Build Bundle(s) / APK(s) → Build APK(s)** → notification **Locate** → `app-debug.apk` |
| CLI equivalent | `./gradlew assembleDebug` |
| Install | `adb install -r app/build/outputs/apk/debug/app-debug.apk` or **Run** from Android Studio |
| Verify | Launch app → acquire image → see classification result |
