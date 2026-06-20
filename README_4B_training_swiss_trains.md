# Module 4B — SwissTrain Safari Guide

Operational guide for a developer returning from a train safari with new Swiss train photos: prepare data, train the `swiss_trains` profile, export/verify ONNX, copy artifacts to Android, and switch the app’s active specialized model.

---

## Purpose

Module 4B turns your safari photos into a deployable on-device classifier for Swiss train/loco classes. It uses the **same Python pipeline** already validated in Phase 4A (`hymenoptera`), with only `--profile swiss_trains` and a different raw-data folder.

The generic MobileNetV2 baseline (`mobilenetv2.onnx` / `imagenet_classes.txt`) stays in place. Hymenoptera artifacts remain in the repo/assets as a **reference profile**; you switch the app to use `swiss_trains` as the active specialized model.

---

## Short answer: how model switching works

**Python side:** use `--profile swiss_trains` on every pipeline script (not `hymenoptera`).

**Android side:** the active specialized profile is **not** selected by an asset config file. It is a **single hardcoded string** in `ImageClassificationActivity.java`:

```java
ModelProfile specialtyProfile = ModelProfile.load(getApplicationContext(), "hymenoptera");
```

Change `"hymenoptera"` → `"swiss_trains"`.

Once that string is changed, the app automatically loads the matching asset family by naming convention:

| Purpose | Asset path (profile = `swiss_trains`) |
|---------|---------------------------------------|
| Model + labels metadata | `swiss_trains_model_metadata.json` |
| ONNX model | `swiss_trains.onnx` (filename from metadata `model_file`) |
| Labels | `swiss_trains_labels.json` (from metadata `labels_file`) |
| Routing allowed-set | `swiss_trains_allowed_mobilenetv2_labels.txt` |
| UI domain messaging | `swiss_trains_profile_config.json` |

**Copying `swiss_trains.*` ONNX artifacts alone is not sufficient.** You must also change the Java profile ID above. The routing/UI assets for `swiss_trains` are already committed; the ONNX trio must be copied after training.

Hymenoptera files can stay in `app/src/main/assets/` — they are not removed. They remain loadable (tests and manual `ModelProfile.load(context, "hymenoptera")` still work), but the classification screen uses whichever profile ID is hardcoded in `ImageClassificationActivity`.

---

## Where to place Swiss train images

From the repository root:

```
model/data/raw_swiss_trains/
├── re_460/
│   └── *.jpg
├── re_620/
│   └── *.jpg
└── …
```

**Do not** put safari images in:

- `model/data/raw_hymenoptera/` (reference profile only)
- `model/data/raw/` (not used — profiles have separate roots)
- Android assets (training reads from `model/data/` only)

After copying photos off your phone/camera, create one subfolder per class under `model/data/raw_swiss_trains/`.

---

## Class folder naming rules

- **One class = one immediate subfolder** of `raw_swiss_trains/`.
- Names must be **lowercase ASCII identifiers** (letters, digits, underscores): e.g. `re_460`, `re_620`, `re_420`.
- These folder names become class IDs in labels, metadata, and the UI.
- **Class index order** is **lexicographic sort of folder names** (not creation order). Example: `re_420` → index 0, `re_460` → index 1, `re_620` → index 2.
- Avoid spaces, uppercase, or special characters — they are not validated by the pipeline and will complicate Android display.

Pick names you want to see in classification results (v1 uses folder name as `display_name`).

---

## Image filename guidance

- Accepted extensions: **`.jpg`**, **`.jpeg`** (case-insensitive). Other files are ignored.
- Filenames can be anything (e.g. `IMG_20260620_143052.jpg`, `re460_platform_01.jpg`).
- **Split assignment** uses **lexicographic sort of filenames** within each class (20% to validation, minimum 1 per class).
- Renaming files after training changes the split manifest — re-run `make_split.py` (and ideally re-train) if you rename in bulk.
- Aspect ratio does not matter; images are stretch-resized to 224×224 at train/val/inference time.
- EXIF orientation is **not** corrected in the Python pipeline — rotate exports before placing if needed. (The Android app applies EXIF when classifying.)

---

## Minimum dataset expectations

Profile `swiss_trains` (from `model/scripts/profiles.py`):

| Requirement | Value |
|-------------|-------|
| Minimum classes | **3** |
| Minimum images per class | **5** |
| Architecture target | 3–10 train/loco classes |

`discover_dataset.py` exits non-zero if these are not met. Add more classes or photos before training.

---

## Exact training/export command sequence

All commands run from `model/` with the project-local virtual environment.

### One-time / refresh environment

```bash
cd model
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export TORCH_HOME="$HOME/.cache/torch"
```

### After placing images under `data/raw_swiss_trains/`

```bash
cd model
source .venv/bin/activate
export TORCH_HOME="$HOME/.cache/torch"

python scripts/discover_dataset.py --profile swiss_trains
python scripts/make_split.py --profile swiss_trains
python scripts/train.py --profile swiss_trains
python scripts/export_onnx.py --profile swiss_trains
python scripts/verify_onnx.py --profile swiss_trains --write-fixture
```

**Pass criteria:** every command exits **0**. Do not copy to Android if `verify_onnx.py` fails.

**What each step does:**

1. **discover** — validates folder layout, counts, and prints class index map.
2. **make_split** — writes `model/data/splits/swiss_trains_val_manifest.json` (does not move raw files).
3. **train** — head-only MobileNetV2 transfer learning → `model/export/checkpoints/swiss_trains_best_model.pt`.
4. **export** — ONNX + labels + metadata under `model/export/`.
5. **verify** — ONNX Runtime inference on a real image; optional tensor fixture for cross-check with Android preprocessing.

---

## Produced artifacts

Under `model/export/` (prefix `swiss_trains`):

| File | Purpose |
|------|---------|
| `swiss_trains.onnx` | On-device model (logits, input `input`, output `output`) |
| `swiss_trains_labels.json` | Index → class id / display_name |
| `swiss_trains_model_metadata.json` | Contract for Android `ModelProfile` loading |
| `swiss_trains_test_fixture_tensor_head.json` | Optional; first 20 input floats for preprocessing alignment |
| `checkpoints/swiss_trains_best_model.pt` | PyTorch checkpoint (keep for re-export; not copied to Android) |

Also created:

- `model/data/splits/swiss_trains_val_manifest.json`

Hymenoptera artifacts in `model/export/hymenoptera*` are **not** overwritten by a `swiss_trains` run (separate prefixes).

---

## How to copy artifacts into Android assets

From the **repository root** (after `verify_onnx.py --profile swiss_trains` succeeds):

```bash
cp model/export/swiss_trains.onnx \
   model/export/swiss_trains_labels.json \
   model/export/swiss_trains_model_metadata.json \
   SwissTrainSpottingApp/app/src/main/assets/
```

**Already in assets (no copy needed for Module 4B routing/UI):**

- `SwissTrainSpottingApp/app/src/main/assets/swiss_trains_allowed_mobilenetv2_labels.txt`
- `SwissTrainSpottingApp/app/src/main/assets/swiss_trains_profile_config.json`

**Keep unchanged (generic baseline):**

- `mobilenetv2.onnx`
- `imagenet_classes.txt`

**Keep in place (reference profile — do not delete):**

- `hymenoptera.onnx`, `hymenoptera_labels.json`, `hymenoptera_model_metadata.json`
- `hymenoptera_allowed_mobilenetv2_labels.txt`, `hymenoptera_profile_config.json`

Note: `SwissTrainSpottingApp/.gitignore` ignores `app/src/main/assets/hymenoptera*`, so hymenoptera ONNX files may exist locally but are not committed. That does not affect the `swiss_trains` deployment path.

If export produces a sibling `swiss_trains.onnx.data` (external weights), copy it alongside the `.onnx` file. Current export uses embedded weights; check `model/export/` after export.

---

## How to switch the Android specialized model from hymenoptera to swiss_trains

### Step 1 — Copy ONNX artifact family (see above)

Ensure these exist in assets:

- `swiss_trains.onnx`
- `swiss_trains_labels.json`
- `swiss_trains_model_metadata.json`

### Step 2 — Change the active profile ID in Java

Edit `SwissTrainSpottingApp/app/src/main/java/com/tb/swisstrainspotting/ImageClassificationActivity.java` in `onCreate()`:

```java
// Before (Phase 4A / current default):
ModelProfile specialtyProfile = ModelProfile.load(getApplicationContext(), "hymenoptera");

// After (Module 4B deployment):
ModelProfile specialtyProfile = ModelProfile.load(getApplicationContext(), "swiss_trains");
```

No other Java changes are required for loading: the same block derives `profileId` from `specialtyProfile.getId()` and loads:

- `AllowedSetLoader.load(..., profileId)` → `swiss_trains_allowed_mobilenetv2_labels.txt`
- `ProfileConfig.load(..., profileId)` → `swiss_trains_profile_config.json`

### Step 3 — Rebuild and run

```bash
cd SwissTrainSpottingApp
./gradlew assembleDebug
```

Install and classify a train photo. Generic MobileNetV2 still runs first; specialized `swiss_trains` runs unconditionally; routing uses the Swiss train allowed-set.

### What stays reference-only vs active

| Component | Role after switch |
|-----------|-------------------|
| `mobilenetv2.onnx` + `imagenet_classes.txt` | **Active** generic classifier (always) |
| `swiss_trains.*` ONNX + metadata + labels | **Active** specialized classifier |
| `swiss_trains_*` allowed-set + profile config | **Active** routing and UI messaging |
| `hymenoptera.*` artifacts | **Present, not active** on classification screen; still used by Phase 5B/5C instrumented tests if kept in assets |

There is **no** runtime settings screen or asset flag to toggle profiles — only the Java string (or a future refactor to externalize it).

---

## Final acceptance checklist

### Python / Module 4B

- [ ] Safari images in `model/data/raw_swiss_trains/<class>/` (≥ 3 classes, ≥ 5 images each)
- [ ] `python scripts/discover_dataset.py --profile swiss_trains` → exit 0; class indices printed
- [ ] `python scripts/make_split.py --profile swiss_trains` → `model/data/splits/swiss_trains_val_manifest.json` created
- [ ] `python scripts/train.py --profile swiss_trains` → `model/export/checkpoints/swiss_trains_best_model.pt`
- [ ] `python scripts/export_onnx.py --profile swiss_trains` → three `swiss_trains*` files in `model/export/`
- [ ] `python scripts/verify_onnx.py --profile swiss_trains --write-fixture` → exit 0; sensible top-1 on a val image
- [ ] Hymenoptera artifacts in `model/export/hymenoptera*` still present and unchanged

### Android integration

- [ ] Copied `swiss_trains.onnx`, `swiss_trains_labels.json`, `swiss_trains_model_metadata.json` to `SwissTrainSpottingApp/app/src/main/assets/`
- [ ] Confirmed `swiss_trains_allowed_mobilenetv2_labels.txt` and `swiss_trains_profile_config.json` already in assets
- [ ] Changed `ImageClassificationActivity` profile ID from `"hymenoptera"` to `"swiss_trains"`
- [ ] App builds without classifier init errors
- [ ] Train photo: generic result shown; specialized result uses **your class folder names** (e.g. `re_460`) with plausible confidence
- [ ] Train-related generic label (e.g. `electric locomotive`): specialized result shown **directly** (not conditional)
- [ ] Non-train photo: specialized result still computed; UI shows conditional framing (`Not a train; if classified within SwissTrains: …`)
- [ ] Hymenoptera assets still on disk (reference / tests); generic MobileNetV2 unchanged

**Module 4B done** when Python verification passes and the app uses `swiss_trains` as the active specialized profile end-to-end.

---

## Quick reference — key paths

```
model/data/raw_swiss_trains/          ← put safari photos here
model/data/splits/swiss_trains_val_manifest.json
model/export/swiss_trains.onnx          ← copy to Android
model/export/swiss_trains_labels.json
model/export/swiss_trains_model_metadata.json
SwissTrainSpottingApp/app/src/main/assets/
SwissTrainSpottingApp/app/src/main/java/com/tb/swisstrainspotting/ImageClassificationActivity.java  ← profile switch
```

For hymenoptera setup (Phase 4A), see `model/README.md` and `model/data/README.md`.
