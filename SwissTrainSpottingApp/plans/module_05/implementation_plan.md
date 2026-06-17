# Module 5 — ONNX Inference Integration Implementation Plan

**Status:** Final for implementation  
**Depends on:** Module 2 (upright `Bitmap`), Module 3 (`ImagePreprocessor` → planar NCHW `float[]`)  
**Reference:** `plans/01_architecture.md`, `AGENTS.md`, `plans/module_03/implementation_plan.md`  
**Skills:** packed-vs-planar, android-dependency-closure  
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

### Phase 5C — Multi-model support
Runtime coexistence and infrastructure only; **no routing or combined prediction yet.**

- Keep more than one ONNX model available at runtime.
- Load and manage multiple `OrtSession` instances in parallel (one per loaded family), each obeying section 5 lifecycle rules on a shared `OrtEnvironment`.
- Coexistence of a generic classifier (e.g. MobileNetV2 / broad ImageNet) and a specialized classifier (e.g. `swiss_trains`, or `hymenoptera` as a development stand-in).
- Independent labels / metadata handling per loaded model.
- Expose per-model inference entry points; do **not** merge or choose between model outputs in this phase.

### Phase 5D — Decision / routing logic
Decision logic only; **not model loading.**

- Combine generic and specialized classifiers in one prediction flow on the shared preprocessed tensor.
- Run the generic classifier first.
- Check whether the generic top prediction belongs to a predefined **allowed set** for which the specialized model is applicable (e.g. train-related ImageNet categories).
- If **yes**: run the specialized classifier; return specialized label + confidence.
- If **no**: return the generic category together with an out-of-scope indication (e.g. generic result + “not compatible with specialized classifier”).
- UI may surface both stages when useful; keep display minimal per AGENTS.md.

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
- prediction flow runs generic classifier first, then conditionally specialized
- out-of-scope generic results return without specialized inference
- in-scope results return the specialized label + confidence

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
