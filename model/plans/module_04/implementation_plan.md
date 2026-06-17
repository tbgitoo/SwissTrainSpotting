# Module 4 — Model Training & ONNX Export Implementation Plan

**Status:** Final for implementation  
**Depends on:** `../plans/01_architecture.md`; Android preprocessing contract from Module 3 (`ImagePreprocessor`); Android inference contract from Module 5 Phase 5A (`ModelConfig`, `OnnxClassifier`, `LogitsParser`)  
**Reference:** `../SwissTrainSpottingApp/AGENTS.md` (ML artifact contract only — Python work stays in `model/`)  
**Target:** Python 3.10+, PyTorch + torchvision, ONNX opset 17, local-only training

---

## 1. Module goal

### Module 4 does
- Provide **one shared** training/export/verification pipeline with **two named dataset profiles**.
- Train a small **MobileNetV2** image classifier via transfer learning on folder-per-class data.
- Export profile-specific ONNX models and aligned metadata artifacts.
- Verify each exported model in Python using the **same preprocessing contract** as Android Module 3.
- Run all pipeline commands from a **project-local virtual environment** at `model/.venv` (local to `model/`, not committed, expected environment for all Module 4 commands).

### Two-phase staged strategy

| Phase | Profile | Purpose |
|-------|---------|---------|
| **4A** | `hymenoptera` (reference) | Validate discovery, split, preprocessing, training, ONNX export, and Python verification **before** project-specific images exist |
| **4B** | `swiss_trains` (project) | Produce final deployment artifacts for Android Phase 5B using the **same pipeline code**, different profile only |

Engineering rationale:
- Decouples image acquisition/labeling from pipeline correctness.
- Isolates training/export failures from dataset-quality or data-availability issues.
- Preserves artifact provenance: reference artifacts remain in repo; they are not overwritten by project runs.

### Module 4 does not
- Change Android preprocessing (Module 3) or inference wiring (Module 5).
- Add backend, persistence, cloud training, experiment tracking, or quantization (optional later work only).
- Introduce spatial preprocessing that diverges from architecture v2 (`CenterCrop`, `RandomResizedCrop`, letterboxing).
- Duplicate pipeline scripts per profile.

### Milestones
- **Phase 4A:** `verify_onnx.py --profile hymenoptera` exits 0; reference artifact family complete under `model/export/`.
- **Phase 4B:** `verify_onnx.py --profile swiss_trains` exits 0; project artifact family complete; manual copy to Android assets permitted.

---

## 2. Dataset profiles, layout, and label source

### Profile registry (single source of truth)
Implement one small module (e.g. `scripts/profiles.py`) mapping profile name → paths and thresholds. **All scripts resolve paths through this registry**; no hardcoded dataset roots in individual scripts.

| Profile | Role | Raw root | Val manifest | Artifact prefix |
|---------|------|----------|--------------|-----------------|
| `hymenoptera` | Reference pipeline validation | `model/data/raw_hymenoptera/` | `model/data/splits/hymenoptera_val_manifest.json` | `hymenoptera` |
| `swiss_trains` | Final project model | `model/data/raw_swiss_trains/` | `model/data/splits/swiss_trains_val_manifest.json` | `swiss_trains` |

CLI interface (all pipeline scripts):

```text
--profile {hymenoptera|swiss_trains}
```

Equivalent explicit flag names are acceptable; every script in §5–§6 must accept the same profile argument.

### Profile-specific dataset roots (no shared raw tree)
Reference and project data **must not** share one mixed raw directory.

```
model/data/
├── raw_hymenoptera/          # reference profile only
│   ├── ants/
│   │   └── *.jpg
│   └── bees/
│       └── *.jpg
├── raw_swiss_trains/         # project profile only
│   ├── re_460/
│   │   └── *.jpg
│   └── re_620/
│       └── *.jpg
└── splits/
    ├── hymenoptera_val_manifest.json
    └── swiss_trains_val_manifest.json
```

Rules (both profiles):
- **One class = one immediate subfolder** of that profile's raw root.
- Class folder names must use lowercase ASCII identifiers (e.g. `re_460`, `ants`) to avoid ordering ambiguity.
- Accepted extensions: `.jpg`, `.jpeg` (case-insensitive). Ignore other files.
- Images may be any aspect ratio; preprocessing stretch-resizes at train/val/inference time.
- Class identity derives from **folder name** only (unchanged from architecture folder-per-class assumption).

### Reference profile: `hymenoptera`
- **Purpose:** Known public dataset to exercise the full pipeline independently of Swiss train photo collection.
- **Expected classes:** `ants`, `bees` (PyTorch transfer-learning tutorial hymenoptera layout, reorganized into folder-per-class under `raw_hymenoptera/`).
- **Minimum counts:** ≥ **2** classes, ≥ **5** images per class.
- **Acquisition (out of band):** Download/prepare hymenoptera images locally; place into `raw_hymenoptera/<class>/`. Document dataset setup in `model/README.md` only — no automated registry or download framework in pipeline code.

### Project profile: `swiss_trains`
- **Purpose:** Final on-device Swiss train/loco classifier.
- **Expected classes:** 3–10 loco/train classes (architecture assumption), e.g. `re_460`, `re_620`.
- **Minimum counts:** ≥ **3** classes, ≥ **5** images per class.
- **Acquisition:** Images collected via the Android app (Module 2) into `raw_swiss_trains/<class>/` as they become available. Pipeline code must not assume this data exists for Phase 4A.

### Validation split (per profile, explicit)
Use a **scripted deterministic split derived from each profile's raw root**, not separate `train/` / `val/` folder trees.

Split policy (identical logic, independent manifests):
- Per class, sort image filenames **lexicographically** (stable, case-sensitive `sorted()`).
- Assign **20%** of each class to validation, **minimum 1 image** (dataset discovery already enforces ≥ 5 images per class).
- Fixed global seed **`42`** for tie-breaking only; primary ordering is filename sort.
- Persist to profile-specific manifest, e.g. `model/data/splits/hymenoptera_val_manifest.json`:

```json
{
  "version": 1,
  "profile": "hymenoptera",
  "seed": 42,
  "val_ratio": 0.2,
  "entries": [
    { "class_id": "ants", "path": "model/data/raw_hymenoptera/ants/img007.jpg" }
  ]
}
```

Training code loads train = all raw images in that profile's root **not** listed in its manifest; val = manifest entries only.

Do **not** mutate or move raw files during split generation. Re-running split for the same profile with unchanged raw tree must produce an identical manifest.

### Label source of truth (both profiles)
- **Class identity** = raw subfolder name (e.g. `ants`, `re_460`).
- **Class ordering** = lexicographic sort of folder names → indices `0..N-1`.
- Ordering is serialized into profile-specific artifacts:
  - `{prefix}_labels.json` (`classes[].index`)
  - checkpoint metadata (`class_to_idx`, `profile`)
  - `{prefix}_model_metadata.json` (`num_classes`, `class_ids`, `dataset_profile`)
- **ONNX logit index `i` maps to `{prefix}_labels.json` entry with `"index": i`**. Android `LogitsParser` argmax index must resolve to the same label string.

Display names: **`display_name = folder name`** in v1 for both profiles.

### Class discovery
`scripts/discover_dataset.py --profile <name>`:
- Resolve raw root and manifest path from `profiles.py`.
- Print class list in index order, per-class counts, train/val counts.
- Exit non-zero if profile unknown, folders missing, or profile-specific minimum counts unmet.

---

## 3. Preprocessing contract

Authoritative cross-platform contract (must match Module 3 `ImagePreprocessor` and architecture §D). **Unchanged across both profiles.**

| Item | Value |
|------|-------|
| Spatial size | 224 × 224 |
| Crop | **none** |
| Resize | stretch to 224×224 (bilinear) |
| Color | RGB |
| Scale | ÷ 255 → [0, 1] |
| Normalize | ImageNet mean `[0.485, 0.456, 0.406]`, std `[0.229, 0.224, 0.225]` |
| Layout | NCHW |
| Dtype | float32 |
| Batch at export | 1 |

### Rejected tutorial defaults (explicit)
Do **not** use `RandomResizedCrop`, `CenterCrop`, letterbox/pad-to-square, OpenCV BGR pipelines, or separate verify/train resize implementations. These would break alignment with Android `Bitmap.createScaledBitmap(..., 224, 224, true)`.

Note: using hymenoptera as reference data does **not** permit tutorial spatial transforms; only the **folder layout** is borrowed, not PyTorch tutorial `RandomResizedCrop` training defaults.

### Shared Python transform module
One canonical module (`scripts/preprocess.py`) used by train, val, export verification, and fixture generation for **both profiles**:

```python
transforms.Compose([
    transforms.Resize((224, 224)),  # stretch; NOT RandomResizedCrop / CenterCrop
    transforms.ToTensor(),
    transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225],
    ),
])
```

PIL loading: `Image.open(...).convert("RGB")`.

### Phase-specific usage

| Phase | Transform | Notes |
|-------|-----------|-------|
| **Training** | Canonical compose | Optional **horizontal flip** (disabled for initial baseline runs; enable only if empirical results justify it), applied before resize only. No other spatial aug in v1. |
| **Validation** | Canonical compose only | Must match Android inference exactly. |
| **Export verification** | Canonical compose only | Same code path as validation. |
| **ONNX export dummy input** | `torch.randn(1, 3, 224, 224)` for tracing only. |

### Android alignment checkpoints
Verification (per profile) must assert:
- tensor shape `(1, 3, 224, 224)`; finite values
- NCHW plane indices `[0]`, `[224*224]`, `[2*224*224]`
- optional fixture: write first 20 floats to `model/export/{prefix}_test_fixture_tensor_head.json` (tolerance `1e-4` vs Android)

---

## 4. Transfer-learning strategy

**Same strategy for both profiles** — only checkpoint and artifact paths differ by profile.

### Chosen approach: head-only feature extraction (required)
- Load `torchvision.models.mobilenet_v2(weights=MobileNet_V2_Weights.IMAGENET1K_V1)`.
- Replace `model.classifier[1]` with `nn.Linear(in_features=last_channel, out_features=num_classes)`.
- **Freeze all parameters** except the new classifier head.
- Train with Adam: lr `1e-3`, batch size `16` (fallback `8`), epochs `15`.
- Loss: `CrossEntropyLoss`; metrics: train/val loss + val accuracy per epoch.

### Optional later fine-tune (not required for 4A or 4B)
- Unfreeze last MobileNetV2 block only; lr `1e-4`, ≤ 5 epochs.
- Document decision in training log if used.

### Checkpoint output (profile-specific)
Save best val-accuracy weights to:

```text
model/export/checkpoints/{prefix}_best_model.pt
```

Checkpoint must include:
- `profile`
- `state_dict`
- `class_to_idx` (ordered dict matching `{prefix}_labels.json`)
- hyperparameters snapshot

Examples:
- `model/export/checkpoints/hymenoptera_best_model.pt`
- `model/export/checkpoints/swiss_trains_best_model.pt`

---

## 5. Training workflow

### Repository layout

```
model/
├── .venv/                                # local Python env (not committed)
├── data/
│   ├── raw_hymenoptera/<class>/*.jpg
│   ├── raw_swiss_trains/<class>/*.jpg
│   └── splits/
│       ├── hymenoptera_val_manifest.json
│       └── swiss_trains_val_manifest.json
├── scripts/
│   ├── profiles.py                       # profile registry
│   ├── discover_dataset.py               # --profile
│   ├── make_split.py                     # --profile (or embedded in discover)
│   ├── preprocess.py
│   ├── train.py                          # --profile
│   ├── export_onnx.py                    # --profile
│   └── verify_onnx.py                    # --profile
├── export/
│   ├── checkpoints/
│   │   ├── hymenoptera_best_model.pt
│   │   └── swiss_trains_best_model.pt
│   ├── hymenoptera.onnx
│   ├── hymenoptera_labels.json
│   ├── hymenoptera_model_metadata.json
│   ├── hymenoptera_test_fixture_tensor_head.json
│   ├── swiss_trains.onnx
│   ├── swiss_trains_labels.json
│   ├── swiss_trains_model_metadata.json
│   └── swiss_trains_test_fixture_tensor_head.json
├── requirements.txt
└── README.md
```

### Execution order (per profile)
All commands assume `model/.venv` is activated. Replace `<profile>` with `hymenoptera` or `swiss_trains`:

1. `python scripts/discover_dataset.py --profile <profile>`
2. `python scripts/make_split.py --profile <profile>` (if not combined with discover)
3. `python scripts/train.py --profile <profile>`
4. `python scripts/export_onnx.py --profile <profile>`
5. `python scripts/verify_onnx.py --profile <profile>`

**Phase 4A:** run full sequence with `--profile hymenoptera`.  
**Phase 4B:** rerun the **same commands** with `--profile swiss_trains` after project raw data exists. No script changes between phases.

### Training sanity signals (minimum)
- Loss decreases over first few epochs (not NaN).
- Val accuracy > random guess (`1/num_classes`) by epoch 5.
- No class with zero train samples after split.
- Checkpoint reload reproduces same `class_to_idx` as discovery output for that profile.

### Reproducibility
- Use project-local virtual environment at `model/.venv` for all Module 4 commands.
- Install pinned dependencies from `requirements.txt` into `.venv`.
- Seeds: `torch.manual_seed(42)`, `random.seed(42)`.
- Log Python, torch, torchvision, onnx, onnxruntime versions in export metadata.
- Pin dependencies in `requirements.txt`.

---

## 6. Export workflow

### ONNX export contract (explicit, both profiles)

| Field | Value |
|-------|-------|
| Input name | `"input"` |
| Output name | `"output"` |
| Input shape | `[1, 3, 224, 224]` |
| Output shape | `[1, N]` where `N = num_classes` for that profile |
| Input dtype | float32 |
| Output dtype | float32 (logits, **no softmax** in graph) |
| Opset | `17` |
| Dynamic axes | **none** (fixed batch 1) |

Profile-specific output files:

| Profile | ONNX | Labels | Metadata |
|---------|------|--------|----------|
| `hymenoptera` | `model/export/hymenoptera.onnx` | `model/export/hymenoptera_labels.json` | `model/export/hymenoptera_model_metadata.json` |
| `swiss_trains` | `model/export/swiss_trains.onnx` | `model/export/swiss_trains_labels.json` | `model/export/swiss_trains_model_metadata.json` |

Node names match Android Phase 5A `ModelConfig.INPUT_NODE_NAME` / `OUTPUT_NODE_NAME` so Phase 5B remains an asset swap.

### Export steps (`scripts/export_onnx.py --profile <name>`)
1. Resolve paths from `profiles.py`.
2. Load `checkpoints/{prefix}_best_model.pt`; rebuild MobileNetV2 + head with saved `num_classes`.
3. `model.eval()`; trace with `torch.randn(1, 3, 224, 224)`.
4. `torch.onnx.export(..., input_names=["input"], output_names=["output"], opset_version=17, dynamic_axes=None)` → `{prefix}.onnx`.
5. Emit `{prefix}_labels.json` and `{prefix}_model_metadata.json` from checkpoint `class_to_idx` (cross-check against sorted folder list).
6. Assert file exists. Warn if size exceeds **20 MB**; investigate unexpected growth before Android asset copy.

### Post-export verification (`scripts/verify_onnx.py --profile <name>`)
Required checks (exit 0 only if all pass):
1. Load ONNX with `onnxruntime.InferenceSession`.
2. Assert input/output names and shapes match `{prefix}_model_metadata.json`.
3. Preprocess one image from **that profile's** raw root (CLI arg or default first val image).
4. Run inference; output `[1, N]`, all finite.
5. Assert `N == len({prefix}_labels.json classes)`.
6. Print top-1 class id + confidence (softmax in script only — mirrors Android `LogitsParser`).
7. Write `{prefix}_test_fixture_tensor_head.json`.

### Manual copy to Android (Phase 4B only)
After `verify_onnx.py --profile swiss_trains` passes:

```bash
cp model/export/swiss_trains.onnx \
   model/export/swiss_trains_labels.json \
   model/export/swiss_trains_model_metadata.json \
   ../SwissTrainSpottingApp/app/src/main/assets/
```

Rename at copy time **only if** Module 5B expects unprefixed asset names (`labels.json`, `model_metadata.json`). Document the exact target filenames in `model/README.md` to match Phase 5B without changing this plan's export names.

`model/README.md` must document: `.venv` creation and activation; `pip install -r requirements.txt`; `TORCH_HOME=$HOME/.cache/torch`; exact per-profile command sequence (discover → split → train → export → verify); dataset preparation steps; Android asset copy filenames.


Reference profile artifacts are **not** copied to Android assets.

---

## 7. Artifact definitions

Each profile produces a **distinct artifact family**. Schemas are identical; filenames and embedded profile fields differ.

### `{prefix}_labels.json`
Purpose: class index → label mapping for that profile (Android Phase 5B uses `swiss_trains` family only).

```json
{
  "version": 1,
  "dataset_profile": "swiss_trains",
  "classes": [
    {
      "index": 0,
      "id": "re_460",
      "display_name": "re_460"
    }
  ]
}
```

Rules:
- `classes` sorted by ascending `index`; contiguous `0..N-1`.
- `id` = raw folder name; unique within profile.
- `dataset_profile` must match CLI `--profile` value.
- Count = ONNX output dimension for that profile's model.

### `{prefix}_model_metadata.json`
Purpose: contract document for runtime loading and validation.

```json
{
  "version": 2,
  "dataset_profile": "swiss_trains",
  "model_file": "swiss_trains.onnx",
  "labels_file": "swiss_trains_labels.json",
  "backbone": "mobilenet_v2",
  "num_classes": 3,
  "class_ids": ["re_460", "re_620", "..."],
  "input_name": "input",
  "output_name": "output",
  "input_shape": [1, 3, 224, 224],
  "input_dtype": "float32",
  "layout": "NCHW",
  "color_order": "RGB",
  "crop": "none",
  "resize": { "width": 224, "height": 224, "method": "bilinear_stretch" },
  "normalize": {
    "scale_to_0_1": true,
    "mean": [0.485, 0.456, 0.406],
    "std": [0.229, 0.224, 0.225]
  },
  "output": {
    "type": "logits",
    "shape": [1, "num_classes"],
    "softmax": "applied_in_app"
  },
  "opset_version": 17,
  "exported_at": "2026-06-17T12:00:00Z",
  "pytorch_version": "2.x.x",
  "torchvision_version": "0.x.x"
}
```

Rules:
- `model_file` / `labels_file` reference the **prefixed export filenames** in `model/export/`.
- `normalize`, `resize`, `layout`, `input_shape` match Module 3 exactly (both profiles).
- `class_ids` order matches `{prefix}_labels.json` indices.
- `dataset_profile` records provenance for Phase 5B swap traceability.

### `{prefix}.onnx`
- MobileNetV2 + linear head; logits only; fixed I/O names `input` / `output`.

---

## 8. Validation and testing

Run the full table **per profile** during its phase. Commands use `--profile <name>` throughout.

| Step | Command | Pass criteria |
|------|---------|---------------|
| Profile registry | import `profiles.py` | Both profiles resolve raw root, manifest, prefix, thresholds |
| Dataset discovery | `discover_dataset.py --profile <p>` | Profile minimum class/image counts met; index map printed |
| Split manifest | `make_split.py --profile <p>` | Deterministic re-run → identical `{prefix}_val_manifest.json` |
| Class ordering | discovery vs export | `class_to_idx` matches `{prefix}_labels.json` |
| Training sanity | `train.py --profile <p>` | No NaN; val acc > random; `{prefix}_best_model.pt` saved |
| Checkpoint integrity | reload checkpoint | Same `profile`, `num_classes`, head shape |
| ONNX export | `export_onnx.py --profile <p>` | `{prefix}.onnx` exists; < 20 MB; I/O names verified |
| ONNX structural check | in `verify_onnx.py` | `onnx.checker.check_model` passes |
| ORT inference | `verify_onnx.py --profile <p>` | Exit 0; `[1,N]` finite logits; N matches labels |
| Metadata completeness | in `verify_onnx.py` | Required keys; `dataset_profile` consistent |
| Artifact bundle | list `model/export/` | All three `{prefix}*` required files present for that profile |
| Cross-profile isolation | after both runs | Hymenoptera artifacts unchanged when re-running `swiss_trains` |

Failure handling:
- Any mismatch → do not copy project artifacts to Android.
- Reference profile failure blocks Phase 4A; project profile failure blocks Phase 4B only.

---

## 9. Acceptance criteria

### Phase 4A complete (reference profile `hymenoptera`)
- [ ] `model/data/raw_hymenoptera/<class>/*.jpg` populated; separate from project raw tree.
- [ ] `hymenoptera_val_manifest.json` exists; train/val derived deterministically from `raw_hymenoptera/`.
- [ ] Class ordering lexicographic; serialized to checkpoint + `hymenoptera_labels.json`.
- [ ] Shared pipeline scripts accept `--profile hymenoptera`.
- [ ] Head-only training completes → `checkpoints/hymenoptera_best_model.pt`.
- [ ] `hymenoptera.onnx` exports with `input`/`output`, `[1,3,224,224]` → `[1,N]`, opset 17.
- [ ] `hymenoptera_labels.json` + `hymenoptera_model_metadata.json` internally consistent.
- [ ] `verify_onnx.py --profile hymenoptera` exits 0 on a real hymenoptera raw image.
- [ ] Resize-only preprocessing; no `CenterCrop` / `RandomResizedCrop`.
- [ ] Reference artifacts retained in `model/export/` (not overwritten by later project runs).

### Phase 4B complete (project profile `swiss_trains`)
- [ ] `model/data/raw_swiss_trains/<class>/*.jpg` populated (≥ 3 classes, ≥ 5 images/class).
- [ ] `swiss_trains_val_manifest.json` exists; independent of hymenoptera manifest.
- [ ] **Same script code** as 4A; only `--profile swiss_trains` (and data) differs.
- [ ] Head-only training completes → `checkpoints/swiss_trains_best_model.pt`.
- [ ] `swiss_trains.onnx` + `swiss_trains_labels.json` + `swiss_trains_model_metadata.json` emitted.
- [ ] `verify_onnx.py --profile swiss_trains` exits 0.
- [ ] Manual copy of **swiss_trains** artifact family to Android assets permitted.
- [ ] No Android code changes required for Module 4 completion.

**Module 4 done** = Phase 4A **and** Phase 4B acceptance criteria met.

---

## 10. Implementation steps

Execute in order. Each step should be a small, reviewable commit.

1. **Scaffold profile-aware tree and local `.venv`** — create `model/.venv`; install from `requirements.txt`; scaffold `raw_hymenoptera/`, `raw_swiss_trains/`, `splits/`, `export/checkpoints/`, `requirements.txt`, `README.md` with per-profile command examples.
2. **Implement `profiles.py`** — registry for both profiles (paths, prefixes, minimum counts).
3. **Implement `preprocess.py`** — canonical compose; profile-agnostic.
4. **Implement `discover_dataset.py --profile`** — scan profile raw root; enforce profile thresholds.
5. **Implement `make_split.py --profile`** — write profile-specific manifest deterministically.
6. **Implement `train.py --profile`** — manifest-aware loader; profile-specific checkpoint path.
7. **Phase 4A — reference validation** — populate `raw_hymenoptera/`; run discover → split → train → export → verify with `--profile hymenoptera`.
8. **Implement/export scripts if not done** — `export_onnx.py --profile`, `verify_onnx.py --profile` (may be steps 7–8 combined during first 4A run).
9. **Gate Phase 4A** — confirm §9 Phase 4A checklist before depending on project data.
10. **Phase 4B — project run** — populate `raw_swiss_trains/`; rerun **identical commands** with `--profile swiss_trains` only.
11. **Gate Phase 4B** — confirm §9 Phase 4B checklist; document Android asset copy filenames.

### Optional later work (not Module 4)
- Partial unfreeze fine-tune per profile.
- INT8 quantization.
- Separate `display_name` mapping file.
- Small `prepare_hymenoptera.sh` setup helper (not a registry).

---

## Pitfalls

| Pitfall | Guard |
|---------|-------|
| Single shared raw tree | Rejected; use `raw_hymenoptera/` and `raw_swiss_trains/`. |
| Hardcoded paths in scripts | All paths via `profiles.py` + `--profile`. |
| Overwriting reference artifacts | Distinct `{prefix}*` filenames; checkpoints per profile. |
| Tutorial hymenoptera transforms | Folder layout only; spatial preprocessing stays resize-only. |
| `ImageFolder` implicit ordering | Always sort folder names explicitly. |
| Re-derive labels at export | Read `class_to_idx` from checkpoint; cross-check folders. |
| Softmax in ONNX | Logits only; softmax in verify script + Android. |
| Wrong ONNX node names | Hardcode `input`/`output` to match `ModelConfig`. |
| Collapsing 4A into ad-hoc test | Reference profile produces full artifact family + acceptance gate. |
| Android copy of reference model | Copy **swiss_trains** family only in Phase 4B. |

---

## Implementation notes

### Python execution environment
All Module 4 commands are expected to run with `model/.venv` activated, not from an unspecified global Python environment.

### Pretrained backbone source
MobileNetV2 weights from TorchVision (`MobileNet_V2_Weights.IMAGENET1K_V1`); not stored in repo.

Explicit cache location:

- `TORCH_HOME=$HOME/.cache/torch`

---

## End of plan
