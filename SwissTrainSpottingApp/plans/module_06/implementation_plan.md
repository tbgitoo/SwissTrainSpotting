# Module 6 — OCR Implementation Plan

**Status:** Working implementation plan  
**Depends on:** Module 2 (upright display `Bitmap`), Module 5 (classification result display on `ImageClassificationActivity`)  
**Reference:** `../plans/01_architecture.md`, `AGENTS.md`, `plans/module_02/implementation_plan.md`, `plans/module_05/implementation_plan.md`  
**Skills:** android-testing, android-dependency-closure  
**Target:** Java + XML, `minSdk 24`, on-device ML Kit Text Recognition only

---

## Purpose

Add **best-effort, local-only OCR** as an auxiliary feature on the existing classification screen.

Module 6 extracts visible text from the same acquired/imported image already used for ONNX inference and displays it **only when non-empty**, without blocking, coupling to, or altering the classification critical path.

**Milestone:** user selects or captures an image, classification result appears as today, and OCR text appears optionally below it when ML Kit returns readable text. Empty OCR, low-quality OCR, and OCR failure must never crash the app or prevent classification from completing.

---

## Inputs / outputs

### Inputs
- **Primary:** the orientation-corrected display `Bitmap` already produced by Module 2 inside `ImageClassificationActivity` (after decode + EXIF correction, before Module 3 resize).
- **Implicit:** current session generation token (same pattern as Module 5) to discard stale async OCR callbacks after a newer image load or activity teardown.

### Outputs
- **`OcrResult`** (new small immutable type): normalized recognized text string, or empty when nothing usable was found.
- **UI:** optional experimental OCR section on `ImageClassificationActivity`:
  - hidden (`View.GONE`) when OCR text is empty or OCR failed
  - visible with label + extracted text when OCR succeeds with non-blank content
- **No disk persistence, no backend payload, no routing influence.**

---

## Dependencies

| Dependency | Role |
|------------|------|
| Module 2 | Provides upright in-memory `Bitmap` and image-load lifecycle on `ImageClassificationActivity` |
| Module 3 | Unchanged; OCR does **not** consume the 224×224 tensor |
| Module 5 | Classification/routing display must remain authoritative and independent |
| ML Kit Text Recognition | On-device Latin OCR (`com.google.mlkit:text-recognition`) |
| Existing executor pattern | Reuse Module 5 async discipline: background work off main thread, UI updates via `runOnUiThread` |

### Gradle / library additions (planning level)

Add to `gradle/libs.versions.toml` and reference from `app/build.gradle`:

```toml
[versions]
mlkitTextRecognition = "16.0.1"   # resolve against current stable if IDE suggests newer compatible pin

[libraries]
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
```

```gradle
implementation libs.mlkit.text.recognition
```

**Scope:** `implementation` only (production OCR seam). No test-only ML Kit dependency unless a future mock wrapper requires it.

**Manifest:** no new permissions for OCR. No cloud endpoints. No additional activities.

---

## Architectural placement

OCR is layered onto the **existing image/result flow** in `ImageClassificationActivity` only.

```
User image acquired (Module 2)
        │
        ├─► Display preview (existing ImageView)
        │
        ├─► [Critical path] preprocess → ONNX inference → routing → classification UI
        │         (Module 3 + 5 — unchanged semantics)
        │
        └─► [Auxiliary path] ML Kit OCR on full Bitmap (Module 6 — parallel, best-effort)
                  └─► optional OCR TextView (shown only if non-empty)
```

**Placement rules:**
- Do **not** add activities, fragments, navigation, or ViewModel stacks.
- Do **not** move acquisition or preprocessing ownership out of existing classes.
- Do **not** make classification wait for OCR.
- Do **not** feed OCR text into `ClassificationRouter`, allowed-set logic, or model selection.
- OCR is **display-only auxiliary output**.

---

## Step-by-step workflow

### Phase 6A — OCR seam + dependency
1. Add ML Kit Text Recognition dependency via version catalog + `app/build.gradle`.
2. Create `OcrResult` (immutable text holder; empty allowed).
3. Create `TextRecognitionReader` (or `OcrReader`) wrapping ML Kit:
   - lazy-create / reuse one `TextRecognizer` for the activity lifetime
   - accept upright `Bitmap`
   - return normalized `OcrResult`
   - expose `close()` for lifecycle cleanup
4. Add pure helper for text normalization (trim, collapse excessive whitespace, join block lines with spaces).

**Outcome:** OCR can be invoked programmatically from a background thread and returns stable empty/non-empty results.

### Phase 6B — Parallel invocation (non-blocking)
5. In `ImageClassificationActivity`, after a valid display `Bitmap` is ready in `loadImageFromUri(...)`, trigger OCR **in parallel** with classification:
   - reuse the existing session `classificationGeneration` counter (increment once per new image)
   - launch classification on existing `inferenceExecutor` (unchanged)
   - launch OCR on a separate single-thread `ocrExecutor` (recommended) or an equivalent clearly decoupled background task
6. Classification UI updates as soon as inference completes — **do not await OCR**.
7. OCR UI updates later on main thread only if:
   - generation still matches
   - activity not finishing/destroyed
   - recognized text is non-empty after normalization

**Outcome:** classification behavior is identical to Module 5; OCR runs concurrently and cannot delay classification text.

### Phase 6C — UI integration
8. Extend `activity_image_classification.xml`:
   - add `tv_ocr_label` (experimental section label)
   - add `tv_ocr_result` (recognized text)
   - place both **below** `tv_classification_result`, above preview image
   - default both to `android:visibility="gone"`
9. Add strings in `strings.xml`:
   - `ocr_experimental_label` (e.g. “Detected text (experimental)”)
   - `ocr_result_format` if needed (e.g. `%1$s`)
   - no hardcoded user-visible OCR copy in Java
10. On empty OCR or OCR failure: keep OCR views hidden; leave classification text untouched.

**Outcome:** OCR is visible only when useful; experimental labeling is explicit per AGENTS.md.

### Phase 6D — Validation
11. Add focused unit tests for text normalization / empty handling.
12. Add instrumentation tests for UI visibility rules and non-blocking coexistence with classification.
13. Manual sanity pass with at least one image containing large readable text (locomotive number, station sign, etc.).

---

## Classes / responsibilities

| Class | Responsibility |
|-------|----------------|
| `OcrResult` | Immutable OCR output; `getText()`, `isEmpty()` |
| `TextRecognitionReader` | ML Kit wrapper: `recognize(Bitmap) → OcrResult`, resource cleanup via `close()` |
| `OcrTextNormalizer` *(optional tiny helper)* | Trim/join ML Kit block text into one display string; return empty for whitespace-only input |
| `ImageClassificationActivity` | Orchestration only: start OCR task in parallel, apply generation guards, update OCR TextViews, close OCR resources in `onDestroy` |
| `RoutedResultFormatter` / Module 5 classes | **Unchanged** — OCR must not modify routing or inference seams |

**Explicit non-responsibilities for new classes:**
- no URI decode, no EXIF handling (Module 2)
- no tensor creation (Module 3)
- no ONNX session management (Module 5)

---

## UI integration

### Layout (`activity_image_classification.xml`)
Insert between classification result and image preview:

```xml
<TextView android:id="@+id/tv_ocr_label" ... android:visibility="gone" />
<TextView android:id="@+id/tv_ocr_result" ... android:visibility="gone" />
```

### Update rules
| Event | `tv_classification_result` | OCR views |
|-------|------------------------------|-----------|
| New image loading starts | show `@string/classifying` | hide OCR section |
| Classification completes | show routed/direct/conditional text | unchanged until OCR completes |
| OCR returns non-empty text | unchanged | show label + text |
| OCR returns empty / fails | unchanged | remain hidden |
| Activity destroyed / stale generation | discard pending updates | discard pending updates |

### UX constraints (AGENTS.md)
- OCR section labeled **experimental**
- Large readable text sizes consistent with existing screen
- No toasts for OCR failure (classification toasts/errors remain as-is)
- No second screen

---

## Threading / lifecycle considerations

### Bitmap choice (justify)
Use the **full-resolution, EXIF-corrected display `Bitmap`** already held in memory for the session.

**Do not** OCR the 224×224 preprocessing resize:
- OCR needs native pixel detail for character boundaries
- Module 3 resize is model-contract specific and destroys small text
- Module 2 already guarantees upright orientation; pass rotation `0` to ML Kit (`InputImage.fromBitmap(bitmap, 0)`)

**Concurrency note:** treat the display `Bitmap` as **read-only** during OCR and inference. Module 3 reads pixels without mutating the source bitmap; ML Kit reads asynchronously — do not recycle the bitmap until both tasks complete or are cancelled via generation discard.

### Executors
- Keep existing `inferenceExecutor` for ONNX path unchanged.
- Add `ocrExecutor = Executors.newSingleThreadExecutor()` for OCR tasks.
- In `onDestroy`:
  - increment generation to invalidate callbacks
  - `shutdownNow()` both executors (existing + OCR)
  - `textRecognitionReader.close()`

### Generation guard
Reuse the same `classificationGeneration` int bumped at each new image classification request. Both classification and OCR callbacks must call the existing `shouldApplyClassificationResult(generation)` guard (rename locally to a neutral `shouldApplyResult(generation)` if helpful, but avoid broad refactors).

### Activity recreation
- Do **not** persist OCR text across process death beyond the current session bitmap flow.
- If Module 2 restores image via saved `Uri` and re-runs classification, OCR re-runs on the restored bitmap like a fresh load.
- Accept that OCR may re-run after recreation; no OCR-specific saved state required for Module 6.

---

## Failure handling / fallback behavior

| Condition | Behavior |
|-----------|----------|
| Empty ML Kit result | Hide OCR UI; no error message |
| Whitespace-only after normalization | Treat as empty; hide OCR UI |
| Low-quality / partial text | Display whatever ML Kit returns after normalization (best-effort); no confidence gating in Module 6 |
| ML Kit task failure / exception | Swallow in OCR seam, log debug-only if desired, hide OCR UI; classification unaffected |
| Null / recycled bitmap | Skip OCR silently |
| OCR slower than classification | Expected; classification shown first |
| OCR faster than classification | Wait to show OCR only after generation check; never overwrite classification area |
| Activity destroyed mid-OCR | Discard result via generation guard |
| Missing ML Kit model (first-run download edge case) | Fail gracefully to empty OCR; do not crash; classification still shown |

**Hard rule:** OCR exceptions must **never** propagate into the classification executor or UI path.

---

## Testing strategy

Follow `android-testing` and existing project conventions: JVM tests for pure logic; instrumentation for Android/ML Kit/UI behavior.

### A. Unit tests (`app/src/test/java/...`)
- `OcrTextNormalizer` (or equivalent):
  - blank → empty
  - mixed whitespace → trimmed
  - multi-block join behavior is stable
- `OcrResult` empty/non-empty semantics

### B. Instrumentation tests (`app/src/androidTest/java/...`)
1. **Non-blocking coexistence**
   - inject/stub OCR reader to delay/sleep
   - assert classification TextView updates without waiting for OCR
2. **Empty OCR hidden**
   - stub returns empty → OCR views remain `GONE`
3. **Non-empty OCR shown**
   - stub returns `"Re 420"` → OCR label + text visible, classification area unchanged
4. **Failure isolation**
   - stub throws → app stable, OCR hidden, classification still displayed via existing test seam
5. **Keystone UI phrase test (Module 6)**
   - drive `applyOcrResult(...)` or package-visible OCR binding method
   - assert experimental label visible and extracted text matches stub
   - assert OCR text does not appear inside classification result view

### C. Optional manual checks
- Photo with visible train number / station board text
- Photo with no text (landscape) → OCR hidden
- Rapid back navigation during OCR → no crash

### Explicitly excluded
- OCR accuracy benchmarking
- cloud OCR / Play Services network dependency testing beyond graceful empty fallback
- text-driven routing tests (out of scope)

---

## Milestone / definition of done

Module 6 is complete when:

- [ ] ML Kit Text Recognition dependency is declared via version catalog + `app/build.gradle` (`implementation` scope)
- [ ] `TextRecognitionReader` performs on-device OCR from the upright full-resolution session `Bitmap`
- [ ] OCR runs asynchronously and **does not block** classification display
- [ ] Classification/routing behavior from Modules 5A–5E is unchanged
- [ ] OCR UI appears **only** when normalized text is non-empty
- [ ] OCR UI is labeled experimental via `strings.xml`
- [ ] Empty OCR, low-quality partial text, and OCR failure do not crash the app
- [ ] OCR resources and executors are cleaned up in `onDestroy`
- [ ] Unit tests cover normalization/empty handling
- [ ] Instrumentation tests cover non-blocking behavior, empty hidden state, and successful OCR display
- [ ] No new activities, persistence, backend calls, or OCR-dependent business logic were added

---

## Files to create or modify

### Create
- `app/src/main/java/com/tb/swisstrainspotting/OcrResult.java`
- `app/src/main/java/com/tb/swisstrainspotting/TextRecognitionReader.java`
- `app/src/main/java/com/tb/swisstrainspotting/OcrTextNormalizer.java` *(if not inlined)*
- `app/src/test/java/com/tb/swisstrainspotting/OcrTextNormalizerTest.java`
- `app/src/androidTest/java/com/tb/swisstrainspotting/OcrIntegrationInstrumentedTest.java`

### Modify
- `app/src/main/java/com/tb/swisstrainspotting/ImageClassificationActivity.java` *(parallel OCR orchestration + UI binding only)*
- `app/src/main/res/layout/activity_image_classification.xml` *(OCR TextViews)*
- `app/src/main/res/values/strings.xml` *(experimental OCR strings)*
- `gradle/libs.versions.toml`
- `app/build.gradle`

### Do not modify
- Module 5 inference/routing classes except for minimal activity orchestration hooks
- Python export pipeline / ONNX assets
- Allowed-set assets or profile config assets
- `MainActivity` navigation structure

---

## Risks / likely mistakes

| Risk | Mitigation |
|------|------------|
| OCR blocks classification | Separate executor; never chain OCR before classification UI update |
| Using 224×224 bitmap for OCR | Document and test against full display bitmap only |
| Hardcoded OCR strings in Java | All user-visible text via `strings.xml` |
| ML Kit listener on wrong thread | Use Tasks API / executor callbacks; marshal UI on main thread only |
| Forgetting to close `TextRecognizer` | Close in `onDestroy` alongside executors |
| OCR exception crashes activity | Catch inside OCR seam; return empty `OcrResult` |
| Feature creep (search, copy, routing) | Display-only in Module 6 |
| Dependency not in version catalog | Follow `android-dependency-closure` skill |

---

## Recommended implementation prompt split

### Prompt 1 — Phase 6A only
Add ML Kit dependency, `OcrResult`, `TextRecognitionReader`, and normalization helper. No Activity wiring yet.

### Prompt 2 — Phase 6B only
Wire parallel OCR invocation in `ImageClassificationActivity` with generation guards. No XML changes yet.

### Prompt 3 — Phase 6C only
Add OCR TextViews + strings; bind visibility rules. Do not alter classification layout semantics.

### Prompt 4 — Phase 6D only
Add unit + instrumentation tests described above. Keep Module 5 tests passing.

---

## End of plan
