# SwissTrainSpotting — Architecture & Implementation Plan

**Status:** Implemented 
**Target stack:** Java + XML Android app, Python transfer learning, ONNX Runtime on-device, ML Kit OCR (optional)  
**Scope:** Local-only, no backend, no persistence, minimal permissions

---

## Assumptions (explicit)

| Assumption | Rationale |
|------------|-----------|
| **3–10 train/loco classes** | Very small dataset; fewer classes keeps training tractable. |
| **Folder-per-class dataset** | `model/data/raw/<class_name>/*.jpg` |
| **MobileNetV2 (ImageNet pretrained)** | Stable, widely supported, easy ONNX export. |
| **Input size 224×224 RGB** | Standard MobileNetV2 input. |
| **Simplified preprocessing (resize only)** | Easier to reproduce exactly on Android. Spatial preprocessing is simplified; normalization is still applied. |
| **ImageNet normalization** | Same in Python and Android. |
| **Single activity (`MainActivity`)** | Simple one-screen app. |
| **System photo picker** | No storage permissions required. |
| **Camera via intent** | Minimal permission (`CAMERA`) only when needed. |
| **OCR optional** | Best-effort only; not required for core functionality. |
| **No model hot-swap** | One model in assets. |
| **minSdk 24** | Compatible with chosen libraries. |

---

## A. High-level data flow

USER → Capture / Select image  
→ Bitmap  
→ Preprocessing  
→ ONNX inference (generic + optional specialized classifiers)
→ routing / result selection 
→ Optional OCR  
→ UI display  

Training (in `model/`):  
Images → training → ONNX export → copy to Android assets  

---

## B. Modules (numbered)

### Module 1 — Android App UI
- Purpose: UI layout and interaction
- Output: Buttons, image preview, result display
- Milestone: App launches and displays UI

---

### Module 2 — Image Acquisition & Permissions
- Purpose: Capture or select image
- Output: In-memory Bitmap (orientation corrected)
- Milestone: Camera and gallery both work

---

### Module 3 — Image Preprocessing
- Purpose: Convert Bitmap → tensor
- Output: `float[1×3×224×224]`
- Milestone: Correct shape and reasonably consistent values

---

### Module 4 — Model Training & ONNX Export
- Purpose: Train model and export ONNX
- Output: `.onnx`, `labels.json`, `model_metadata.json`
- Milestone: ONNX model runs in Python

---

### Module 5 — ONNX Inference Integration
- Purpose: Run model in Android
- Output: Combined classification result (generic + optional specialized) with confidence(s)
- Milestone: App displays interpreted classification result (generic and/or specialized)

⚠️ Implementation constraints:
- Inference must **not run on the UI thread**
- ONNX Runtime objects must be **properly closed** to avoid memory leaks

---

### Module 6 — OCR (optional)
- Purpose: Extract visible text (best-effort only)
- Output: Extracted text or empty
- Milestone: No crash, text optionally displayed

Note: OCR is not part of the critical path and may be skipped.

---

### Module 7 — Finalization (Testing & Packaging)
- Purpose: Ensure system correctness and prepare the application for reuse and distribution
- Output:
  - validated end-to-end behavior
  - documented code (JavaDoc / Python docstrings)
  - reviewed project structure (proposal level, no formal sign-off)
  - installable APK
- Milestone: Application runs end-to-end on device and can be packaged and reused

---

## C. Repository structure


```
SwissTrainSpotting/
├── plans/
│   └── 01_architecture.md
│
├── model/
│   ├── data/raw/<class>/
│   ├── scripts/
│   ├── plans/
│   └── export/
│       ├── hymenoptera.onnx
│       ├── hymenoptera_labels.json
│       ├── hymenoptera_model_metadata.json
│       ├── swiss_trains.onnx
│       ├── swiss_trains_labels.json
│       └── swiss_trains_model_metadata.json
│
└── SwissTrainSpottingApp/
    ├── plans/
    └── app/src/main/
        ├── assets/
        │   ├── mobilenetv2.onnx
        │   ├── imagenet_classes.txt
        │   ├── hymenoptera.onnx
        │   ├── hymenoptera_labels.json
        │   ├── hymenoptera_model_metadata.json
        │   ├── swiss_trains.onnx
        │   ├── swiss_trains_labels.json
        │   └── swiss_trains_model_metadata.json
```

**Note:** `mobilenetv2.onnx` and `imagenet_classes.txt` are downloaded reference assets used for baseline (generic) inference, whereas `hymenoptera_*` and `swiss_trains_*` are profile-specific artifacts produced by the Python export workflow.

---

## D. Interface contract (Python ↔ Android)

- Input: 224×224 RGB image  
- Normalize: ImageNet mean/std  
- Layout: NCHW  
- Type: float32  
- Output: logits `[1, num_classes]`  

---

### Python preprocessing

```python
transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])
```

---

### Android equivalent

```java
Bitmap.createScaledBitmap(...);
pixel / 255.0f;
normalize per channel;
store in NCHW order;
```

Critical requirements:
- Ensure RGB channel order
- Apply EXIF rotation correction before preprocessing

---

## E. Main risks

- Preprocessing mismatch (RGB/BGR, layout)
- ONNX integration issues
- OCR unreliability
- Android image handling (rotation, memory)

---

## F. Development order

1. UI (Module 1)
2. Image acquisition (Module 2)
3. Preprocessing (Module 3)
4. Train model (Module 4)
5. Inference (Module 5)
6. OCR (Module 6, optional)
7. Finalize Documentation, Packaging, Usage Testing (Module 7)

---

## G. Minimal testing

- Image loads correctly
- Tensor conversion works
- ONNX returns label
- App runs without crash

---

## Definition of done

User can take or select a photo and receive a predicted class with confidence, entirely on-device.

OCR is optional and may be empty.

