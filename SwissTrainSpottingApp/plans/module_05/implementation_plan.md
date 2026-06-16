# Module 5 — ONNX Inference Integration Implementation Plan

**Status:** Working implementation plan  
**Depends on:** Module 2 (upright `Bitmap`), Module 3 (`ImagePreprocessor` → planar NCHW `float[]`)  
**Reference:** `../plans/01_architecture.md`, `AGENTS.md`, `plans/module_03/implementation_plan.md`  
**Skills:** `packed-vs-planar`, `android-dependency-closure`  
**Target:** Java + XML, `minSdk 24`, no backend, no persistence

---

## 1. Module goal

### Module 5 does
- Consume the Module 3 preprocessing output: `float[]` length **150528**, logical shape **`[1, 3, 224, 224]`**, **NCHW**, float32.
- Load an ONNX model from a **static on-device app resource or asset**.
- Create and manage an ONNX Runtime session.
- Build ONNX input tensors from the Module 3 float buffer **without repacking or layout drift**.
- Run inference **off the main thread**.
- Parse logits into a minimal result object suitable for UI display.
- Add a minimal classification result display to the existing classification screen.

### Module 5 does not
- Train or export ONNX models (Module 4).
- Change image acquisition, EXIF handling, or decode flow (Module 2).
- Reimplement preprocessing or alter Module 3’s contract.
- Run OCR (Module 6).
- Add backend/cloud inference or persistent history.
- Introduce large architectural layers, fragments, navigation stacks, DI frameworks, or major UI redesign.

### Deliverable
A working on-device inference path:

**upright `Bitmap` → Module 3 tensor → ONNX Runtime inference → label + confidence shown in UI**

---

## 2. Scope boundaries

### Upstream (not Module 5)
- **Module 2** provides an already-upright in-memory `Bitmap`.
- **Module 3** provides a tested `Bitmap -> float[]` conversion with:
  - RGB
  - float32
  - NCHW
  - logical shape `[1, 3, 224, 224]`
  - length `150528`

### Downstream (not Module 5)
- OCR remains Module 6.
- Broader application-level validation remains later work.
- Model training/export remains Module 4.

### Module 5 boundary
Starts at:
- a valid upright `Bitmap`, or
- a valid preprocessed tensor from Module 3

Stops at:
- a classification result available to the UI:
  - label
  - class index
  - confidence

---

## 3. Phase split

## Phase 5A — Reference-model Android inference bring-up

### Purpose
Validate the Android-side ONNX inference path independently of project-model uncertainty.

### Reference artifacts already in repo
- `app/src/main/res/raw/mobilenetv2.onnx`
- `app/src/main/res/raw/imagenet_classes.txt`
- `app/src/androidTest/assets/metro_flon.png`

### Why 5A exists
Phase 5A isolates the following risks before Module 4 artifacts are involved:
- ONNX Runtime dependency wiring
- model loading from Android resources
- session creation
- input tensor creation
- input/output name mismatches
- output parsing
- background-thread integration
- minimal UI result display

### 5A is done when
- the reference model loads on device/emulator
- a session is created successfully
- Module 3 output can be fed directly into ONNX Runtime
- logits are parsed into a usable result
- labels load correctly
- instrumented tests pass
- at least one integration test using `metro_flon.png` passes as **pipeline validation**
- minimal UI result display works on the classification screen

### 5A does not require
- correct Swiss train/domain predictions
- final production assets layout
- Module 4 artifacts

---

## Phase 5B — Project-model swap

### Purpose
Replace the reference model with the real project ONNX model when Module 4 artifacts exist.

### Expected project artifacts
- `app/src/main/assets/swiss_trains.onnx`
- `app/src/main/assets/labels.json`
- `app/src/main/assets/model_metadata.json`

### What changes in 5B
- model source location
- label format and loader
- input/output names if needed
- class count
- result interpretation based on project metadata

### What must stay stable in 5B
- the basic inference seam
- the wrapper API
- the result object
- the Module 3 handoff
- the background-thread pattern
- the overall test structure

### 5B principle
Phase 5B is a **configuration / artifact swap**, not a reason to redesign Module 5.

---

## 4. Inference contract

## Input to Module 5
Module 5 consumes Module 3 output:

- Java type: `float[]`
- length: **150528**
- logical shape: **`[1, 3, 224, 224]`**
- layout: **NCHW planar**
- dtype: **float32**
- normalization: **already applied by Module 3**

### Planar index reminder
For channel `c`, row `y`, column `x`:

```text
index(c, y, x) = c * (224 * 224) + y * 224 + x
```

Module 5 must treat this buffer as already valid model input data.

### Guardrail
Apply `packed-vs-planar` here very explicitly:

- Module 5 receives **planar float data**
- Module 5 does **not** receive packed ARGB pixels
- Module 5 must **not** re-pack, interleave, or transpose unless a model contract explicitly requires it

---

## ONNX Runtime input
Phase 5A reference-model input should use shape:

```java
new long[] {1, 3, 224, 224}
```

The Module 3 float buffer should be wrapped directly as ONNX input.

### Rule
No HWC/NHWC assumptions.  
No `[R,G,B,R,G,B,...]` interleaving.  
No manual reshaping “just to be safe.”

---

## Output
### Phase 5A
The reference model is expected to return logits for **1000 ImageNet classes**.

Module 5 should:
- extract the logits buffer
- compute top class
- compute confidence (softmax or clearly documented equivalent)
- map index → label with `imagenet_classes.txt`

### Phase 5B
The project model output should be interpreted using:
- `labels.json`
- `model_metadata.json`

No shared Module 5 logic should hardcode the 1000-class assumption beyond the 5A reference configuration.

---

## Result object
Use a simple immutable result type, e.g.:

- `int classIndex`
- `String label`
- `float confidence`

That is enough for 5A and 5B.

---

## 5. Proposed implementation seam

Keep Module 5 simple, but do not collapse everything into one class.  
Small focused classes are preferred here because they are easier to test and debug.

### Likely classes
- `ClassificationResult`
- `OnnxClassifier`
- `LogitsParser`
- `LabelLoader`
- `ModelConfig`

### Minimal responsibilities

#### `ClassificationResult`
Small immutable POJO holding:
- class index
- label
- confidence

#### `ModelConfig`
Small configuration holder for:
- model location
- labels location
- input/output names
- class count

Phase 5A can use constants for the reference model.  
Phase 5B updates this cleanly for the project model.

#### `LabelLoader`
Small helper for loading labels:
- Phase 5A: `imagenet_classes.txt`
- Phase 5B: `labels.json`

#### `LogitsParser`
Pure Java helper for:
- argmax
- stable softmax
- top-class extraction

This should be JVM-testable.

#### `OnnxClassifier`
Single-purpose ONNX Runtime wrapper:
- load model
- create session
- build input tensor
- run inference
- return `ClassificationResult`

It may expose both:

```java
classify(float[] inputTensor)
classify(Bitmap uprightBitmap)
```

with the `Bitmap` overload delegating to Module 3 preprocessing first.

---

## 6. Model loading strategy

## Phase 5A
Load from:

- `R.raw.mobilenetv2`
- `R.raw.imagenet_classes`

This matches current repo state and keeps 5A simple.

## Phase 5B
Switch to:
- `app/src/main/assets/swiss_trains.onnx`
- `labels.json`
- `model_metadata.json`

Do not over-engineer this migration in 5A.  
Keep the swap small and localized.

---

## 7. Threading and UI split

### Background execution
Inference must not run on the main thread.

### Responsibility split
- `OnnxClassifier`: synchronous inference wrapper, no thread management inside
- `ImageClassificationActivity`: submits background inference work
- UI thread: updates result views after inference completes

### Expected flow
1. Image is loaded and displayed.
2. The activity launches background classification.
3. Background task calls:
   - `ImagePreprocessor.preprocess(bitmap)`
   - `OnnxClassifier.classify(...)`
4. UI thread updates:
   - result label
   - confidence
   - error message if needed

### UI scope
Keep UI changes minimal:
- add result `TextView`s
- add required strings
- no new screens
- no new navigation flows

---

## 8. Testing strategy

Module 5 needs stronger testing than average because failures can be:
- obvious compile errors
- runtime failures
- silently wrong results

### Strategy layers

#### A. Narrow JVM tests
Use pure Java tests where possible for:
- argmax correctness
- softmax sanity
- parsing guards for null/empty logits

#### B. Android instrumented inference tests
Use instrumented tests for:
- loading the reference model from real app resources
- creating an ONNX session
- running inference on controlled tensors
- validating label loading

#### C. Integration tests
Use instrumented end-to-end Android-side tests for:
- `metro_flon.png` decode
- Module 3 preprocessing
- Module 5 inference
- result parsing
- repeatability of output on the same input

### Testing principle
The `metro_flon.png` integration test validates the **inference path**, not final semantic correctness for Swiss rolling stock.

---

## 9. Initial tests for Phase 5A

These should be added before or alongside initial implementation.

### JVM tests
- `logitsParser_topIndex_returnsArgmax`
- `logitsParser_softmax_sumsToOne`
- `logitsParser_emptyOrNull_throwsOrGuards`

### Instrumented tests
- `labelLoader_imagenet_loads1000Labels`
- `onnxClassifier_sessionCreates_fromRawResource`
- `onnxClassifier_runsWithZeroInput_tensorLength150528`
- `onnxClassifier_rejectsWrongInputLength`

### Required gate
Module 5 Phase 5A should not be considered working until:
- the session creates successfully
- inference runs successfully on a valid tensor

---

## 10. Additional tests and integration tests

After the initial 5A tests pass, add:

- `metroFlon_preprocessThenInfer_succeeds`
- `metroFlon_inference_isRepeatable`
- `classifyBitmap_helper_matchesTwoStepPath`

### Optional UI smoke
A UI/Espresso smoke test is optional and low priority.  
Only add it if the seam is already straightforward and synchronization is simple.

Do not expand Module 5 around Espresso complexity.

---

## 11. Assets and files

## Phase 5A
Existing reference artifacts:

- `app/src/main/res/raw/mobilenetv2.onnx`
- `app/src/main/res/raw/imagenet_classes.txt`
- `app/src/androidTest/assets/metro_flon.png`

These are fixed inputs to the module and should be treated as existing infrastructure.

## Phase 5B
Expected project artifacts:

- `app/src/main/assets/swiss_trains.onnx`
- `app/src/main/assets/labels.json`
- `app/src/main/assets/model_metadata.json`

---

## 12. Dependency planning

Module 5 requires ONNX Runtime Android.

### Planned dependency
Add:

- `com.microsoft.onnxruntime:onnxruntime-android`

through the version catalog.

Apply `android-dependency-closure`:
- add catalog entry
- add app dependency
- ensure main code compiles
- ensure relevant test targets compile

Do not silently guess versions during implementation; use the project’s chosen versioning style consistently.

---

## 13. Notices and pitfalls

### Packed-vs-planar mistakes
Avoid:
- treating packed pixels as model input
- interleaving RGB for ONNX input
- changing `[1,3,224,224]` into `[1,224,224,3]`
- reshaping based on intuition instead of contract

### ONNX Runtime integration mistakes
Avoid:
- wrong input/output node names
- wrong tensor shape
- main-thread inference
- leaking `OrtSession`, `OnnxTensor`, or result objects
- reloading the model on every classification

### Output parsing mistakes
Avoid:
- argmax over the wrong array
- label indexing drift
- unstable or naïve softmax
- treating reference ImageNet output as domain truth

### Scope drift
Avoid:
- pulling Model 4 concerns into 5A
- redesigning UI
- adding persistence
- re-testing EXIF/decode behavior under Module 5

---

## 14. Acceptance criteria

## Phase 5A is done when
- ONNX Runtime dependency is wired correctly
- the reference model loads from `res/raw`
- an ONNX session is created successfully
- Module 3 output is accepted directly as model input
- inference runs without exception
- labels load and map to output indices
- `ClassificationResult` is returned with valid content
- inference runs off the main thread
- minimal result UI updates work
- initial tests pass
- `metro_flon.png` integration test passes

## Phase 5B is done when
- the project model and metadata are available
- reference artifacts are replaced cleanly
- config and loaders are updated without architectural rewrite
- class count and labels align with metadata
- regression tests pass with the project model path

---

## 15. Recommended implementation prompt split

### Prompt 1 — tests and scaffolding
Add:
- ONNX Runtime dependency
- `ClassificationResult`
- `ModelConfig`
- `LogitsParser`
- `LabelLoader`
- `OnnxClassifier` skeleton
- initial failing tests

No UI yet.

### Prompt 2 — runtime integration
Implement:
- model loading
- session creation
- tensor creation
- inference
- label loading
- result parsing

Make initial tests pass.  
No UI yet.

### Prompt 3 — integration and minimal UI
Implement:
- `metro_flon.png` integration test
- background inference wiring in `ImageClassificationActivity`
- minimal result display on screen

Optional UI smoke only if trivial.

### Prompt 4 — Phase 5B project-model swap
Only after Module 4 artifacts exist:
- switch to project assets
- update config/labels/metadata handling
- re-run regression tests

No architecture rewrite.

---

## 16. Repo touchpoints

Relevant implementation areas:

- `ImagePreprocessor.java`
- `ImageClassificationActivity.java`
- `activity_image_classification.xml`
- `res/raw/mobilenetv2.onnx`
- `res/raw/imagenet_classes.txt`
- `androidTest/assets/metro_flon.png`
- `gradle/libs.versions.toml`
- `app/build.gradle`
- `res/values/strings.xml`
