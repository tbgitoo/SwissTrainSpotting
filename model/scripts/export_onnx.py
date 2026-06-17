from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import onnx
import torch
import torchvision
from torch import nn
from torchvision.models import mobilenet_v2

from preprocess import INPUT_HEIGHT, INPUT_WIDTH
from profiles import CHECKPOINTS_ROOT, EXPORT_ROOT, get_profile


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export profile checkpoint to ONNX + metadata.")
    parser.add_argument("--profile", required=True, choices=["hymenoptera", "swiss_trains"])
    return parser.parse_args()


def build_model(num_classes: int) -> nn.Module:
    model = mobilenet_v2(weights=None)
    in_features = model.classifier[1].in_features
    model.classifier[1] = nn.Linear(in_features, num_classes)
    return model


def main() -> int:
    args = parse_args()
    profile = get_profile(args.profile)

    checkpoint_path = CHECKPOINTS_ROOT / f"{profile.artifact_prefix}_best_model.pt"
    if not checkpoint_path.exists():
        raise FileNotFoundError(f"Checkpoint does not exist: {checkpoint_path}")

    checkpoint = torch.load(checkpoint_path, map_location="cpu")
    class_to_idx = checkpoint["class_to_idx"]
    if checkpoint.get("profile") != profile.name:
        raise RuntimeError(
            f"Checkpoint profile '{checkpoint.get('profile')}' does not match requested profile '{profile.name}'"
        )

    num_classes = len(class_to_idx)
    model = build_model(num_classes)
    model.load_state_dict(checkpoint["state_dict"])
    model.eval()

    EXPORT_ROOT.mkdir(parents=True, exist_ok=True)
    onnx_path = EXPORT_ROOT / f"{profile.artifact_prefix}.onnx"
    labels_path = EXPORT_ROOT / f"{profile.artifact_prefix}_labels.json"
    metadata_path = EXPORT_ROOT / f"{profile.artifact_prefix}_model_metadata.json"

    dummy = torch.randn(1, 3, INPUT_HEIGHT, INPUT_WIDTH, dtype=torch.float32)
    torch.onnx.export(
        model,
        dummy,
        str(onnx_path),
        input_names=["input"],
        output_names=["output"],
        opset_version=17,
        dynamic_axes=None,
        dynamo=False,
    )

    onnx_model = onnx.load(str(onnx_path))
    onnx.checker.check_model(onnx_model)
    opsets = {opset.domain: opset.version for opset in onnx_model.opset_import}
    if opsets.get("", 0) != 17:
        raise RuntimeError(f"Exported ONNX opset is {opsets.get('', 0)}; expected 17.")

    labels = sorted(class_to_idx.items(), key=lambda item: item[1])
    labels_payload = {
        "version": 1,
        "dataset_profile": profile.name,
        "classes": [
            {"index": index, "id": class_id, "display_name": class_id}
            for class_id, index in labels
        ],
    }
    with labels_path.open("w", encoding="utf-8") as handle:
        json.dump(labels_payload, handle, indent=2)
        handle.write("\n")

    metadata_payload = {
        "version": 2,
        "dataset_profile": profile.name,
        "model_file": onnx_path.name,
        "labels_file": labels_path.name,
        "backbone": "mobilenet_v2",
        "num_classes": num_classes,
        "class_ids": [class_id for class_id, _ in labels],
        "input_name": "input",
        "output_name": "output",
        "input_shape": [1, 3, INPUT_HEIGHT, INPUT_WIDTH],
        "input_dtype": "float32",
        "layout": "NCHW",
        "color_order": "RGB",
        "crop": "none",
        "resize": {"width": INPUT_WIDTH, "height": INPUT_HEIGHT, "method": "bilinear_stretch"},
        "normalize": {
            "scale_to_0_1": True,
            "mean": [0.485, 0.456, 0.406],
            "std": [0.229, 0.224, 0.225],
        },
        "output": {"type": "logits", "shape": [1, "num_classes"], "softmax": "applied_in_app"},
        "opset_version": 17,
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "pytorch_version": torch.__version__,
        "torchvision_version": torchvision.__version__,
        "onnx_version": onnx.__version__,
    }
    with metadata_path.open("w", encoding="utf-8") as handle:
        json.dump(metadata_payload, handle, indent=2)
        handle.write("\n")

    model_size_mb = onnx_path.stat().st_size / (1024 * 1024)
    print(f"Exported ONNX: {onnx_path} ({model_size_mb:.2f} MB)")
    if model_size_mb > 20:
        print("Warning: ONNX model is larger than 20 MB.")
    print(f"Wrote labels: {labels_path}")
    print(f"Wrote metadata: {metadata_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
