# Module 5 — ONNX Inference Integration Implementation Plan

**Status:** Final for implementation  
**Depends on:** Module 2 (upright `Bitmap`), Module 3 (`ImagePreprocessor` → planar NCHW `float[]`)  
**Reference:** `plans/01_architecture.md`, `AGENTS.md`, `plans/module_03/implementation_plan.md`  
**Skills:** packed-vs-planar, android-dependency-closure, android-testing 
**Target:** Java + XML, minSdk 24

---

## 1. Module goal

### Module 5 does
- Consume Module 3 output (`float[]`, length **150528**, shape `[1,3,224,224]`, NCHW)
- Load ONNX model from assets
- Create and reuse ONNX Runtime session
- Run inference off main thread
- Parse logits → result (label + confidence)
- Display minimal result in classification screen

### Module 5 does not
- training/export (Module 4)
- image acquisition / EXIF (Module 2)
- preprocessing changes (Module 3)
- OCR (Module 6)
- backend / persistence / architecture changes

---

## 2. Assets

Use `assets/` for all models and labels.

### Phase 5A
```
app/src/main/assets/mobilenetv2.onnx
app/src/main/assets/imagenet_classes.txt
```

### Phase 5B
Profile-scoped artifact family (example from `model/export/` — reference profile):
```
hymenoptera.onnx
hymenoptera_labels.json
hymenoptera_model_metadata.json
```

Project profile follows the same naming pattern (later):
```
swiss_trains.onnx
swiss_trains_labels.json
swiss_trains_model_metadata.json
```

Metadata binds the family (`model_file`, `labels_file`, `dataset_profile`). Phase 5A assets (plain-text labels, hard-coded constants) remain valid for the MobileNetV2 reference path.

### Phase 5C
Both families may reside in assets at once; runtime holds multiple ONNX sessions (generic + specialized).

### Phase 5D
No additional assets. Routing rules are derived from loaded metadata and a configurable allowed-set for specialized applicability.

---

## 3. Phase split

Phased delivery: **5A** (single reference model) → **5B** (profile-based loading) → **5C** (multi-model coexistence) → **5D** (routing).

### Phase 5A — reference model
Validate Android inference path using MobileNetV2.

### Phase 5B — Multi-configuration model loading
Load different model families through explicit configuration / profile handling (not a single fixed asset layout).

- Treat **model + labels + metadata** as one artifact family; metadata points to sibling files (`model_file`, `labels_file`, `dataset_profile` — as in `hymenoptera_model_metadata.json` from Phase 4A export).
- Support different label formats across families: Phase 5A plain-text `imagenet_classes.txt` (line index → label) vs exported `{prefix}_labels.json` (`classes[].index`, `id`, `display_name`).
- Read input/output node names, `num_classes`, and tensor contract from metadata where present; retain Phase 5A constants for the MobileNetV2 reference family.
- Switch between the generic reference model and a specialized exported profile (e.g. `hymenoptera` for pipeline validation, `swiss_trains` for deployment) via configuration only — no layout-specific code paths.

#### Validation beyond the baseline ImageNet MobileNetV2 model

The Phase 5A baseline (`mobilenetv2.onnx` / `imagenet_classes.txt`) verifies that the generic inference pipeline (asset loading, session creation, preprocessing → ONNX tensor, logits → argmax) works end-to-end. Phase 5B must also demonstrate that the profile-driven path is not specific to ImageNet:

- An alternative model such as `hymenoptera.onnx` with its matching label file (`hymenoptera_labels.json`) loads and runs through the same pipeline without introducing model-specific code paths.
- Output handling works with a different number of classes (e.g. `num_classes = 2` vs ImageNet's 1000) — shape validation, logits extraction, and argmax must not assert or hard-code a specific class count.
- At least one smoke test loads the alternative metadata profile construct from an assets JSON, creates the classifier via `ModelProfile`, runs a single deterministic inference pass with valid preprocessed input, and asserts that a result (label, confidence) is returned non-null.

This validation confirms model-agnostic loading and output handling. It establishes that Phase 5B has wired up the profile → ONNX session lifecycle correctly; it does **not** assert production-level classification accuracy for the transferred model.

### Phase 5C — Multi-model support
Runtime coexistence and infrastructure only; **no routing or combined prediction yet.**

- Keep more than one ONNX model available at runtime.
- Load and manage multiple `OrtSession` instances in parallel (one per loaded family), each obeying section 5 lifecycle rules on a shared `OrtEnvironment`.
- Coexistence of a generic classifier (e.g. MobileNetV2 / broad ImageNet) and a specialized classifier (e.g. `swiss_trains`, or `hymenoptera` as a development stand-in).
- Independent labels / metadata handling per loaded model.
- Expose per-model inference entry points; do **not** merge or choose between model outputs in this phase.

#### Testing — multi-model coexistence only

Phase 5B already validated single-model correctness (profile-based loading, JSON label parsing, alternative model initialization and inference, non-ImageNet class count support). Phase 5C testing must **not repeat** those validations.

**Required tests:**

1. **Coexistence test** — Load two model profiles (e.g. MobileNetV2 + Hymenoptera), initialize two classifiers with separate sessions, run inference on both using the same input tensor, assert both produce valid results with independent class indices / labels.

2. **Isolation test** — Run classifier A, then classifier B, then classifier A again; assert classifier A produces consistent results across runs with no cross-contamination from classifier B.

3. **Lifecycle independence (optional)** — Close one classifier and verify the other remains operational.

**Explicitly excluded (already covered by Phase 5B):** asset existence, metadata parsing, label loading, ONNX model loading correctness, parser correctness.

### Phase 5D — Presentation-aware routing logic
Routing / presentation only; **not model loading.**

- Run the generic MobileNetV2 classifier first.
- Run the specialized classifier (e.g. hymenoptera → later SwissTrains) regardless of the generic result. Do **not** skip or gate execution based on the generic output.
- The specialized classifier result is always computed and retained.
- Check whether the generic top prediction belongs to a predefined **allowed set** for which the specialized model is applicable (e.g. train-related ImageNet categories).
- If **yes** (in-scope): present the specialized classifier result as the direct classification (label + confidence).
- If **no** (out-of-scope): present the specialized result conditionally / hypothetically via UI text such as:
  - "Doesn't look like a train; if it were a train, the closest class would be: Re420."
- The allowed set is defined by the specialized profile's metadata.

#### Validation — Phase 5D routing scenarios

1. **Applicable case** — Generic top prediction falls within the specialized profile's allowed set. Assert that the UI presents the specialized result as a direct classification (label + confidence) with no conditional framing.

2. **Non-applicable case** — Generic top prediction does NOT fall within the allowed set. Assert that:
   - the specialized classifier was still computed (non-null result);
   - the UI presents the specialized result as conditional / hypothetical;
   - the generic result is also surfaced to the user.

3. **No execution gating** — Out-of-scope generic results must never prevent the specialized classifier from running. Verify that inference on both models executes regardless of the generic output.

---

## 4. Inference contract

### Input
- `float[]`
- length: **150528**
- shape: `[1,3,224,224]`
- layout: NCHW

### Rules
- do not transpose or repack
- do not treat as HWC
- use ONNX Runtime API only

Create tensors using:
- `OnnxTensor.createTensor(env, inputData, shape)`

---

## 5. Session lifecycle (STRICT)

`OnnxClassifier`:

- obtain `OrtEnvironment` via `OrtEnvironment.getEnvironment()` (singleton)
- create `OrtSession` once
- reuse session for all calls
- expose `close()`

Forbidden:
- creating session inside `classify()`
- creating multiple `OrtEnvironment` instances

Phase 5C: one `OrtSession` per loaded model family; still a single `OrtEnvironment`.

---

## 6. Output handling

Before parsing:
- ensure tensor exists
- validate rank (expected `[1, N]`)
- flatten to `float[] logits`

Parser contract:
```
parse(float[] logits)
```

After inference:
- assert output size is valid
- assert all values are finite (no NaN / Infinity)

---

## 7. Prediction rules

- class = argmax(logits)
- confidence optional

If used:
```
exp(logit - maxLogit)
```

---

## 8. Node validation

At initialization:
- read model input/output names
- verify expected names
- fail if mismatch

---

## 9. Threading and UI

- inference NOT on main thread
- Activity launches background task
- UI updated on main thread only

If Activity destroyed:
- discard result

---

## 10. Implementation seam

Classes:
- `ClassificationResult`
- `OnnxClassifier`
- `LogitsParser`
- `LabelLoader`
- `ModelConfig`

Phase 5D may add a small routing seam (e.g. `ClassificationRouter`) without changing 5A–5C classes.

Responsibilities
- `OnnxClassifier`: session + inference (one instance per loaded family from 5C onward)
- `LogitsParser`: argmax + softmax
- `LabelLoader`: labels — plain-text (5A) and JSON artifact families (5B+)
- `ModelConfig`: per-profile names + constants; metadata-driven from 5B onward

---

## 11. Testing

### Required
- tensor length = 150528
- output size validation
- output values are finite
- repeatability

### Integration
```
app/src/androidTest/assets/metro_flon.png
```

Pipeline:
Bitmap → preprocess → inference → parse

---

## 12. Acceptance criteria

Phase 5A done when:
- model loads from assets
- session created once
- inference runs
- correct result structure returned
- UI updates safely
- integration test passes

Phase 5B done when:
- an exported artifact family loads via profile / configuration (e.g. `hymenoptera` or `swiss_trains`)
- metadata drives model path, labels path, node names, and `num_classes`
- JSON labels resolve correctly by `classes[].index`
- switching between MobileNetV2 reference and an exported profile requires configuration change only

Phase 5C done when:
- generic and specialized models load and coexist in memory
- each family has its own session; lifecycle rules hold per session
- both can run inference on the same preprocessed tensor without cross-contamination

Phase 5D done when:
- both generic and specialized classifiers run unconditionally (no execution gating)
- in-scope generic results present the specialized label + confidence directly
- out-of-scope generic results present the specialized result as conditional / hypothetical
- specialized classifier output is always computed, retained, and displayed regardless of the generic prediction

---

## 13. Implementation steps

1. tests + scaffolding
2. runtime
3. integration + UI
4. profile-based model loading (5B)
5. multi-model coexistence (5C)
6. routing logic (5D)

---

## End of plan
