# AGENTS.md — model/ (Module 4 Python pipeline)

## 1) Scope
- Applies when the current working directory is `<repo>/model`.
- Governs work in the current `model/` subtree rooted here.
- Applies only to the Python-side Module 4 training/export/verification pipeline.
- Out of scope: Android Java/XML/Kotlin/Groovy/Gradle/UI/runtime redesign.

## 2) Relationship to parent rules
- `../AGENTS.md` remains authoritative for global project constraints.
- This file adds stricter local rules for Module 4 Python work in `model/`.
- Do not treat this file as a replacement for parent/global rules.

## 3) Python execution environment
- Run all Module 4 commands from `model/.venv`.
- Install dependencies from `model/requirements.txt` only.
- Use `TORCH_HOME=$HOME/.cache/torch`.
- Do not assume global Python packages or user-system Python state.

## 4) Dataset profile rules
- Supported profiles are exactly:
  - `hymenoptera`
  - `swiss_trains`
- All script paths must resolve through `scripts/profiles.py`.
- Do not hardcode dataset roots in individual scripts.
- Keep profile raw trees separate:
  - `data/raw_hymenoptera/`
  - `data/raw_swiss_trains/`
- Class identity comes only from immediate class folder names.
- Class folder names must be lowercase ASCII identifiers.

## 5) Split and label-order rules
- Splits are manifest-based, deterministic, and derived from each profile raw tree.
- Splitting must never move, rename, or mutate raw files.
- Class index order is lexicographic by class folder name.
- Exported labels and metadata must preserve that exact order.
- Do not re-derive or reorder labels in any "helpful" alternate way.

## 6) Preprocessing contract (must match Android)
- Spatial: resize-only to `224x224` (stretch).
- No crop.
- Color: RGB.
- Normalize: ImageNet mean/std.
- Layout: NCHW.
- Dtype: float32.
- Use one shared preprocessing implementation for train/val/export-verify.
- Do not introduce:
  - `RandomResizedCrop`
  - `CenterCrop`
  - letterboxing/pad-to-square paths
  - OpenCV/BGR preprocessing paths
  - separate train and verify preprocessing implementations

## 7) Transfer-learning policy
- Baseline is MobileNetV2 pretrained on ImageNet.
- Train classifier head only.
- Keep backbone frozen.
- No broad model redesign or aggressive optimization by default.
- Optional fine-tuning is out of scope unless explicitly requested.

## 8) ONNX export contract
- Preserve:
  - input name: `input`
  - output name: `output`
  - input shape: `[1,3,224,224]`
  - output semantics: logits only
  - opset: `17`
  - dynamic axes: none
- Do not rename ONNX I/O nodes.
- Do not add softmax into the exported ONNX graph.

## 9) Artifact rules
- Expected artifact families per profile prefix:
  - `{prefix}.onnx`
  - `{prefix}_labels.json`
  - `{prefix}_model_metadata.json`
  - `export/checkpoints/{prefix}_best_model.pt`
- Keep reference artifacts isolated from project artifacts; do not overwrite by cross-profile runs.
- Only `swiss_trains` artifacts are copied to Android assets later.

## 10) Simplicity and anti-overengineering
- Keep Module 4 local, script-based, minimal, and reproducible.
- Do not introduce unless explicitly requested:
  - notebooks
  - cloud training
  - experiment tracking platforms
  - CI/CD pipelines
  - MLOps frameworks
  - package-management/tooling overkill (Poetry, conda, Docker, etc.)

## 11) README expectations
- `model/README.md` must document:
  - `.venv` setup and activation
  - dependency installation from `requirements.txt`
  - `TORCH_HOME` setting
  - dataset preparation per profile
  - exact per-profile command sequence (discover/split/train/export/verify)
  - Android asset copy filenames for `swiss_trains`

