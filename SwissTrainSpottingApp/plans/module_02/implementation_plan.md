# Module 2 — Image Acquisition & Permissions Implementation Plan

**Status:** Working implementation plan  
**Depends on:** Module 1 (two-activity UI shell)  
**Reference:** `../plans/01_architecture.md`, `AGENTS.md`  
**Target:** Java + XML, `minSdk 24`, no backend, no persistence

---

## 1. Module purpose

Enable the user to acquire a train or locomotive image from either:
1. the **system photo picker**
2. the **device camera**

and display that image on `ImageClassificationActivity`.

**Module 2 deliverable:** a reliably loaded and displayable in-memory image (`Bitmap` and optionally `Uri`) on the classification screen for the current session.

**Milestone:** the user taps **Image** or **Camera** on the home screen, completes the flow, and sees the chosen or captured image in the preview area.

---

## 2. Scope boundaries

### In scope
- system photo picker
- simple camera capture
- minimal permissions (`CAMERA` only for the camera path)
- image display in `ImageClassificationActivity`
- passing acquisition mode from `MainActivity` to `ImageClassificationActivity`
- temporary cache file for camera capture if required
- basic decode from `Uri` to `Bitmap`
- conservative image-size limiting for display to reduce memory risk on large photos
- graceful cancel / failure handling

### Explicitly out of scope
- image preprocessing, resizing to model input, normalization, tensor creation
- ONNX inference
- OCR
- persistent storage, gallery history, favorites, caching beyond the session
- CameraX, custom camera preview screen, cropping UI
- storage permissions (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`)
- new activities, fragments, Navigation Component, ViewModel stacks

---

## 3. Recommended implementation strategy

### Primary path: Photo picker
Use the **system photo picker** as the primary and lowest-risk acquisition path.

- launch from `ImageClassificationActivity`
- decode the returned `Uri`
- display the image in the existing preview area
- no storage permission should be requested

### Secondary path: Camera
Add camera capture as a second path, but keep it intentionally simple.

- request `CAMERA` permission only on the camera path
- use a standard capture intent / activity result contract
- store output only in app cache if a temporary file is needed
- decode and display the resulting image

### Architectural boundary
This module must stop at **“image acquired and displayed”**.
Everything related to model preparation remains in Module 3 or later.

---

## 4. Prioritized action plan

### Phase A — First working milestone (must do first)
1. Add an `AcquisitionMode` mechanism (`GALLERY`, `CAMERA`).
2. Update `MainActivity` so the two buttons open `ImageClassificationActivity` with the appropriate mode.
3. Implement the **photo picker path only**.
4. Decode the returned image conservatively for display and show it in the preview.
5. Handle cancel safely (no crash, placeholder remains visible).
6. Ensure all referenced placeholder strings and image resources actually exist before testing.

**Outcome of Phase A:** Module 2 already has a successful, testable image acquisition path.

### Phase B — Camera support (second priority)
7. Add `CAMERA` permission to the manifest.
8. Implement camera capture using the simplest standard Android path.
9. If required, add `FileProvider` with a cache file.
10. Decode captured image conservatively and display it.
11. Close streams / file descriptors after decode.
12. Handle permission denial safely.

**Outcome of Phase B:** both Image and Camera paths work.

### Phase C — Hardening (only after A+B work)
13. Move visible messages into `strings.xml`.
14. Harden decode paths so all `InputStream` and file-descriptor based resources are closed safely.
15. Add minimal EXIF orientation correction for decoded images:
    - read EXIF metadata using `ExifInterface`
    - if orientation is not `ORIENTATION_NORMAL`, rotate the `Bitmap` accordingly
    - apply correction only for sources where EXIF metadata is available
    - if `androidx.exifinterface.media.ExifInterface` is unresolved, add the AndroidX ExifInterface dependency using the current project-compatible stable version and the repository’s dependency-management conventions
16. Add lightweight configuration-change restoration by persisting the current image `Uri` and re-decoding on activity recreation.
17. Refactor decode logic only if duplication is already present and extraction remains minimal.

**Outcome of Phase C:** improved robustness with safe resource handling, correct image orientation, and basic rotation resilience.

**Important:** testing is **not** part of Phase C implementation. Phase C ends when the hardening behavior is implemented and compiles cleanly. Validation of that behavior belongs to Phase D.

### Phase D — Validation and testing (after Phase C)
18. Add focused instrumentation tests for Phase C behavior that can be validated inside the app process.
19. Use repository-controlled EXIF fixtures for automated validation rather than relying on emulator gallery state.
20. Store the authoritative automated test fixtures under Android test assets:
    - `Landscape_0.jpg`
    - `Landscape_1.jpg`
21. Feed one fixture at a time into the image display machinery through a deterministic in-app path that represents the picker result boundary, without automating the external picker UI itself.
22. Assert that the preview `ImageView` contains a drawable after loading.
23. Assert EXIF-sensitive output for the 90° test case:
    - use `Landscape_1.jpg` as the automated EXIF-rotation fixture
    - assert the corrected rendered drawable/bitmap orientation through a deterministic property such as the final rendered bitmap dimensions when width/height should swap
    - do **not** use the `ImageView` layout size itself as proof of EXIF handling
24. Recreate the activity during the test and verify that the displayed image is restored from persisted state.
25. Keep one manual sanity pass for full picker and camera integration after automated validation, because the external picker/camera UI itself is outside the app process.
26. Treat 180° EXIF validation as a manual sanity check rather than a required automated assertion for this phase.

**Outcome of Phase D:** hardening behavior is validated with controlled automated checks and a final manual integration sanity pass.

---

## 5. Activity responsibilities

### MainActivity
- keep its role as the simple navigation hub
- pass only the acquisition mode to `ImageClassificationActivity`
- do not perform acquisition logic here
- do not request camera permission here

### ImageClassificationActivity
- read the acquisition mode from the intent
- register result launchers
- trigger either picker or camera depending on mode
- decode the result to a `Bitmap`
- display the image
- keep the image only for the current session
- handle cancel / error paths without crash

---

## 6. Files to create or modify

### Create
- `app/src/main/java/com/tb/swisstrainspotting/AcquisitionMode.java`
- `app/src/main/res/xml/file_paths.xml` *(only if required for camera cache Uri)*
- `app/src/androidTest/java/com/tb/swisstrainspotting/...` *(Phase D instrumentation tests only)*
- `app/src/androidTest/assets/Landscape_0.jpg` *(Phase D automated fixture)*
- `app/src/androidTest/assets/Landscape_1.jpg` *(Phase D automated fixture)*

### Modify
- `app/src/main/java/com/tb/swisstrainspotting/MainActivity.java`
- `app/src/main/java/com/tb/swisstrainspotting/ImageClassificationActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/layout/activity_image_classification.xml` *(only if IDs or placeholder handling need adjustment)*
- `app/build.gradle` *(only if Phase C or Phase D requires additional dependencies, using the repository’s dependency-management conventions)*
- `gradle/libs.versions.toml` *(only if this repository uses a version catalog and Phase C or Phase D requires new entries)*

---

## 7. Testing strategy

Testing is split between milestone checks during implementation and a dedicated validation phase.

### Phase A tests
- Tap **Image** → picker opens
- Select image → image appears in preview
- Cancel picker → no crash, placeholder remains
- Confirm all referenced placeholder strings and image resources exist and load correctly

### Phase B tests
- Tap **Camera** → permission request appears if needed
- Grant permission → camera opens
- Capture image → image appears in preview
- Deny permission → no crash, clear user feedback
- Confirm no storage permission is requested on the gallery path

### Phase C checks
- Confirm all visible strings are moved into `strings.xml`
- Confirm decode resources are closed safely
- Confirm EXIF handling and state restoration compile and behave plausibly
- Do **not** treat Phase C as the dedicated testing phase; formal validation belongs to Phase D

### Phase D automated tests
- Use Espresso and standard instrumentation support for UI assertions inside the app process
- Use repository-controlled fixtures from `app/src/androidTest/assets/`
- Do **not** assume that test images already exist in emulator gallery / MediaStore
- Do **not** depend on adb push or manual `is_pending=0` fixes for automated tests
- Feed `Landscape_0.jpg` and `Landscape_1.jpg` into the app through a deterministic in-app URI/result path rather than automating the real picker UI
- Assert that the preview drawable exists after loading
- For the 90° EXIF case, assert a deterministic rendered-image property such as swapped bitmap/drawable dimensions when orientation correction should change aspect
- Recreate the activity and assert that the image remains displayed after restore

### Phase D manual sanity checks
- Perform one full gallery integration pass using the real photo picker
- Perform one full camera integration pass using emulator or physical device as available
- Visually confirm the 180° EXIF case manually if needed
- Confirm no regressions in Phase A or Phase B behavior after hardening and tests

---

## 8. Risks / likely mistakes

### High-priority risks
- adding storage permissions unnecessarily
- moving preprocessing logic into Module 2
- overengineering the camera path with CameraX
- passing `Bitmap` objects through intents
- making the camera permission flow global instead of camera-only
- decoding large images without size limits, causing memory issues

### Medium-priority risks
- incorrect `FileProvider` setup
- decode failure not handled safely
- visible hardcoded strings left in XML or Java
- forgetting to close input streams or file descriptors after decode
- referencing placeholder strings or drawable resources that do not yet exist
- wrong `ExifInterface` import (`android.util.ExifInterface` instead of `androidx.exifinterface.media.ExifInterface`)
- agent guesses a dependency version instead of using the project-consistent stable version or IDE resolution

### Phase D testing risks
- trying to automate the external picker UI instead of stubbing or controlling the result
- relying only on visual inspection when deterministic assertions are possible
- asserting `ImageView` layout size instead of the rendered drawable/bitmap state
- depending on emulator gallery state instead of repository-controlled test fixtures
- trying to automate 180° EXIF validation with a width/height assertion even though that case does not change aspect

---

## 9. Implementation notes

- Prefer the simplest standard Android mechanism over elegance.
- The photo picker should be treated as the primary low-risk path.
- Camera support should be added only after the picker path already works.
- Decode conservatively for display to reduce OOM risk on large photos.
- File descriptors and input streams must be closed after decode.
- EXIF correction and rotation persistence belong to hardening, not to the first working milestone.
- EXIF correction requires AndroidX `ExifInterface`; do not assume the dependency is already present.
- Do not require the agent to invent the exact dependency version from memory.
- If the dependency is missing, resolve it using Android Studio / Gradle against the current project-compatible stable AndroidX ExifInterface version rather than relying on an LLM guess.
- Phase D may use Espresso, ActivityScenario, and other standard instrumentation helpers for automated validation.
- Automated tests should focus on app-process behavior; external picker/camera UI should remain a minimal manual sanity check.
- Repository-controlled Android test assets are the authoritative automated EXIF fixtures for Phase D.
- For automated validation, proving that a 90° EXIF case is handled is sufficient for this phase; 180° remains a manual sanity check.
- This module should output a displayable image only; model-facing preparation must remain in Module 3.

---

## 10. Definition of done

Module 2 is complete when:

- the user can select an image from the system photo picker and see it in `ImageClassificationActivity`
- the user can capture an image with the camera and see it in the same screen
- the gallery path does not request storage permissions
- cancel and error paths do not crash the app
- referenced placeholder strings and drawable resources exist and work correctly
- decode resources are closed safely
- EXIF orientation is handled correctly for supported sources
- the displayed image survives activity recreation through lightweight `Uri`-based restoration
- Phase D automated checks pass for the controlled fixture and recreation scenarios
- no preprocessing, ONNX, OCR, persistence, or extra screens were added

---

## 11. Recommended implementation prompt split

Use separate prompts for each phase instead of asking an agent to implement all of Module 2 in one go.

### Prompt 1 — picker only
Implement Module 2 Phase A only: add AcquisitionMode, update MainActivity to pass GALLERY vs CAMERA extras, and wire ImageClassificationActivity with the system photo picker so the selected image is decoded and displayed. Do not add camera support yet. Follow AGENTS.md strictly.

### Prompt 2 — camera only
Implement Module 2 Phase B only: add camera permission, standard camera capture, and display of the captured image. Do not add CameraX, storage permissions, preprocessing, or OCR. Follow AGENTS.md strictly.

### Prompt 3 — hardening only
Implement Module 2 Phase C only: move all user-visible strings into `strings.xml`, add resource-safe decode handling, add minimal EXIF correction based on EXIF metadata, and add lightweight `Uri`-based rotation persistence. If `androidx.exifinterface.media.ExifInterface` is unresolved, add the AndroidX ExifInterface dependency using the current project-compatible stable version and the repository’s dependency-management conventions. Do not modify unrelated files. Testing is not part of this prompt.

### Prompt 4 — automated validation only
Implement Module 2 Phase D automated tests only: add instrumentation tests for drawable presence, 90° EXIF-corrected image display, and activity recreation persistence using repository-controlled fixtures `Landscape_0.jpg` and `Landscape_1.jpg` from Android test assets. Prefer deterministic in-app URI/result injection over automating the external picker UI. Keep manual full-flow sanity checks outside this prompt.
