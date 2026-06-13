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
14. Add minimal EXIF orientation correction only if the displayed image is visibly rotated.
15. Add lightweight state handling for rotation only if time permits.
16. Refactor decode logic into a helper class only if code duplication already justifies it.

**Outcome of Phase C:** improved robustness, but not required for the first milestone.

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
- Optional helper class for image loading / rotation *(only if needed after a first working version)*

### Modify
- `app/src/main/java/com/tb/swisstrainspotting/MainActivity.java`
- `app/src/main/java/com/tb/swisstrainspotting/ImageClassificationActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/layout/activity_image_classification.xml` *(only if IDs or placeholder handling need adjustment)*

---

## 7. Testing strategy

Testing should remain manual and milestone-based.

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

### Phase C tests
- Verify all visible strings come from `strings.xml`
- If EXIF correction is implemented, verify rotated images appear upright
- If state restoration is implemented, verify the image remains visible after rotation

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

### Nice-to-fix-later risks
- sideways preview because of EXIF
- image lost on rotation

---

## 9. Implementation notes

- Prefer the simplest standard Android mechanism over elegance.
- The photo picker should be treated as the primary low-risk path.
- Camera support should be added only after the picker path already works.
- Decode conservatively for display to reduce OOM risk on large photos.
- File descriptors and input streams must be closed after decode.
- EXIF correction and rotation persistence are useful, but they should not block the first milestone.
- This module should output a displayable image only; model-facing preparation must remain in Module 3.

---

## 10. Definition of done

Module 2 is complete when:

- the user can select an image from the system photo picker and see it in `ImageClassificationActivity`
- the user can capture an image with the camera and see it in the same screen
- the gallery path does not request storage permissions
- cancel and error paths do not crash the app
- referenced placeholder strings and drawable resources exist and work correctly
- no preprocessing, ONNX, OCR, persistence, or extra screens were added

---

## 11. Recommended implementation prompt split

Use separate prompts for each phase instead of asking an agent to implement all of Module 2 in one go.

### Prompt 1 — picker only
Implement Module 2 Phase A only: add AcquisitionMode, update MainActivity to pass GALLERY vs CAMERA extras, and wire ImageClassificationActivity with the system photo picker so the selected image is decoded and displayed. Do not add camera support yet. Follow AGENTS.md strictly.

### Prompt 2 — camera only
Implement Module 2 Phase B only: add camera permission, standard camera capture, and display of the captured image. Do not add CameraX, storage permissions, preprocessing, or OCR. Follow AGENTS.md strictly.

### Prompt 3 — hardening only
Implement Module 2 Phase C only: move all user-visible strings into strings.xml, optionally add EXIF correction if needed, add lightweight rotation persistence only if simple, and ensure decode resources are closed correctly. Do not modify unrelated files.

