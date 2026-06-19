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
| Module 2 | Provides upright in-memory `Bitmap` and image-load lifecycle |
| Module 3 | Unchanged; OCR does **not** consume tensor |
| Module 5 | Classification display remains authoritative |
| ML Kit Text Recognition | On-device Latin OCR |
| Executors | Background execution pattern |

---

## Architectural placement

OCR runs parallel to the inference path:

```
Inference path: Bitmap → preprocess → ONNX → classification UI
Aux path: Bitmap → OCR → optional UI display
```

OCR must remain:
- asynchronous
- independent
- non-blocking
- display-only

---

## Step-by-step workflow

### Phase 6A — Setup
1. Add ML Kit dependency
2. Create `OcrResult`
3. Define `OcrAnalyzer` interface
4. Implement `MlKitOcrAnalyzer`

### Phase 6B — Execution
5. Trigger OCR after bitmap ready
6. Run on `ocrExecutor`
7. Wait for result in controlled background flow
8. Apply UI update via `runOnUiThread`

### Phase 6C — UI
9. Add OCR container `ll_ocr_section`
10. Add label + result TextViews
11. Show only if non-empty

### Phase 6D — Validation
12. Unit tests
13. Instrumentation tests using stub analyzer

---

## Classes

- OcrResult
- OcrAnalyzer
- MlKitOcrAnalyzer
- OcrTextNormalizer
- ImageClassificationActivity (integration)

---

## Threading and safety

- OCR executor separate
- control ML Kit task execution
- UI update only on main thread

### Bitmap handling
- Use upright bitmap
- If oversized → scale copy for OCR only

---

## Failure handling

- Any OCR failure → empty result
- Never crash
- Never block inference

---

## Testing

- unit: normalization
- instrumentation: UI + concurrency via stub

---

## Done when

- OCR runs without blocking
- UI shows optional text
- no crashes
- tests pass


