# Module 4 - Model Training and ONNX Export

Run all commands from this directory (`model/`).

## Local Python environment

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export TORCH_HOME="$HOME/.cache/torch"
```

## Dataset preparation

The pipeline does not download datasets automatically.

- Reference profile raw data: `data/raw_hymenoptera/`
- Project profile raw data: `data/raw_swiss_trains/`

Each profile uses folder-per-class data, where the immediate subfolder name is the class ID.
Class folder names must be lowercase ASCII identifiers.

`hymenoptera` is expected to contain `ants` and `bees` with at least 5 images each.

## Phase 4A command sequence (hymenoptera)

```bash
python scripts/discover_dataset.py --profile hymenoptera
python scripts/make_split.py --profile hymenoptera
python scripts/train.py --profile hymenoptera
python scripts/export_onnx.py --profile hymenoptera
python scripts/verify_onnx.py --profile hymenoptera --write-fixture
```

Produced artifacts:

- `export/checkpoints/hymenoptera_best_model.pt`
- `export/hymenoptera.onnx`
- `export/hymenoptera_labels.json`
- `export/hymenoptera_model_metadata.json`
- `export/hymenoptera_test_fixture_tensor_head.json` (optional)

## Phase 4B command sequence (swiss_trains, later)

Use the same scripts with only `--profile` changed:

```bash
python scripts/discover_dataset.py --profile swiss_trains
python scripts/make_split.py --profile swiss_trains
python scripts/train.py --profile swiss_trains
python scripts/export_onnx.py --profile swiss_trains
python scripts/verify_onnx.py --profile swiss_trains --write-fixture
```

Only the `swiss_trains` artifact family is copied to Android assets later:

- `swiss_trains.onnx`
- `swiss_trains_labels.json`
- `swiss_trains_model_metadata.json`
