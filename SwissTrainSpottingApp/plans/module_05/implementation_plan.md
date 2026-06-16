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
```
swiss_trains.onnx
labels.json
model_metadata.json
```

---

## 3. Phase split

### Phase 5A — reference model
Validate Android inference path using MobileNetV2.

### Phase 5B — project model
Replace artifacts only (no redesign):
- model
- labels
- metadata

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

Responsibilities
- `OnnxClassifier`: session + inference
- `LogitsParser`: argmax + softmax
- `LabelLoader`: labels
- `ModelConfig`: names + constants

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

---

## 13. Implementation steps

1. tests + scaffolding
2. runtime
3. integration + UI
4. model swap

---

## End of plan
