# AGENTS.md — SwissTrainSpottingApp

Guardrails for AI-assisted work in this Android Studio module only.  
Package: `com.tb.swisstrainspotting`. Plan reference: `../plans/01_architecture.md`.

---

## Hard rules (do not violate)

- **Java + XML only.** No Kotlin source, no Jetpack Compose, no `.kts` build scripts unless already present.
- **No backend, auth, cloud inference, or persistent storage.** No Room, SQLite, DataStore, Firebase, or network APIs for ML.
- **On-device only.** ONNX model and labels live in `app/src/main/assets/`.
- **Minimal permissions.** System photo picker for gallery; `CAMERA` only when capture is implemented. Do not add broad storage permissions.
- **OCR is optional.** ML Kit text recognition must not block classification. Wire OCR last; empty OCR is acceptable.
- **Do not redesign the app.** Follow existing structure; milestone-sized diffs only.

---

## Architecture

- Prefer **simple `AppCompatActivity` + XML layout** per screen.
- **Do not add** Navigation Component, fragments, ViewModel/LiveData stacks, Hilt/Dagger, or multi-module splits unless explicitly requested.
- **Do not add** custom toolbars, collapsing headers, CoordinatorLayout tricks, or Material motion unless explicitly requested.
- Existing activities: `MainActivity`, `ImageClassificationActivity`. 
- Do not introduce additional activities, fragments, or navigation structures unless explicitly requested in the task
- Inference and preprocessing run **off the main thread** (e.g. `ExecutorService` or `new Thread` + `runOnUiThread`). Close ONNX Runtime sessions/tensors when done.
- Hold the working `Bitmap` in memory for the session only; no saving to disk.

---

## UI & XML

- Layouts under `app/src/main/res/layout/`. Match naming: `activity_<name>.xml`.
- Prefer simple **LinearLayout** for basic screens
- **ConstraintLayout** is allowed but should not be introduced unnecessarily.
- Do not migrate to Compose or complex nesting.
- Use **Material / AppCompat widgets** consistently with the app theme (`Theme.SwissTrainSpotting`).
- For buttons, prefer `com.google.android.material.button.MaterialButton` or `Button` as already used — stay consistent within a screen.
- **Toolbar rule:** never use `android.widget.Toolbar`. If a toolbar is ever required, use `androidx.appcompat.widget.Toolbar` in XML **and** the matching Java import. If uncertain, **omit the toolbar**.
- XML widget types and Java casts/imports must match exactly (no `android.widget` in XML with `androidx` in Java, or vice versa).
- Do not substitute Android framework classes with alternatives unless explicitly requested
- Do not mix android.* and androidx.* classes for the same UI element
- For small layout edits, **rewrite the full XML file** when partial patches are likely to break structure.

---

## Strings

- **No hardcoded user-visible text** in Java or XML (`android:text`, `setText(...)`, toasts, dialogs).
- Add entries to `app/src/main/res/values/strings.xml` and reference `@string/...` or `getString(R.string....)`.
- Exception: non-user-facing log tags and debug keys.

---

## ML integration (when touching inference)

- Read contract from `model_metadata.json` in assets (224×224 RGB, stretch resize, ImageNet normalize, NCHW float32).
- Copy artifacts from `model/export/` after training; do not invent alternate preprocessing.
- Classification UI is required; OCR UI is labeled experimental and may be hidden when empty.
- Add Gradle deps sparingly: `onnxruntime-android` for inference; `text-recognition` only for OCR work.

---

## Change discipline

- Touch **only files required** for the requested milestone.
- Do not reformat unrelated code, rename packages, or bump dependency versions without cause.
- Do not edit generated/build outputs, `.gradle` caches, or launcher assets unless the task requires it.
- Keep `AndroidManifest.xml` changes minimal (activities, permissions, `FileProvider` if camera needs it).
- Run or suggest a compile check after non-trivial Java/XML changes.

---

## File map (common touch points)

| Area | Path |
|------|------|
| Launcher | `MainActivity.java`, `activity_main.xml` |
| Classification screen | `ImageClassificationActivity.java`, `activity_image_classification.xml` |
| Strings | `res/values/strings.xml` |
| Theme | `res/values/themes.xml` |
| Manifest | `app/src/main/AndroidManifest.xml` |
| Model assets | `app/src/main/assets/` |
| Unit tests | `app/src/test/java/com/tb/swisstrainspotting/` |

---

## UI interaction constraints

- Prefer large, easily tappable buttons
- Do not rely on small toolbar icons for core navigation
- Important actions (e.g., back navigation) should be represented by explicit UI buttons
- Design for low-precision interaction (e.g. including unfavorable situations like gloves, indirect viewing)

---

## Dependency closure (Android)
If you introduce a new AndroidX, test, or other non-project symbol, you must ensure dependency closure:
- add required imports
- add the required dependency in the project’s dependency management mechanism
- if the project uses `gradle/libs.versions.toml`, prefer adding dependencies there over pinning raw versions in module build files
- use the correct dependency scope (`implementation`, `androidTestImplementation`, `testImplementation`, etc.)
- do not leave unresolved symbols introduced by your own changes
- compile the relevant target before declaring completion

---

## Packed vs planar arrays

Packed arrays: some representations pack multiple components into a single value (e.g. ARGB int per pixel).

Planar arrays: components are stored separately, typically one value per element (e.g. one float per channel per pixel).

- Watch packed-vs-planar mistakes when converting image data.
- Re-derive buffer sizes and flat indexing from what each structure actually stores.
- Ask: "what does one element of this array represent?"
- When in doubt, consult the `packed-vs-planar` skill.

---

## Out of scope for agents

- `model/` Python training pipeline (separate folder).
- Replacing Java with Kotlin or XML with Compose.
- Feature creep: history, favorites, settings screens, onboarding, analytics.
