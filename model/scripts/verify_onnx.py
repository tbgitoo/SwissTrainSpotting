"""Validate exported ONNX artifacts against the architecture contract and metadata.

Checks:
  - onnx.checker passes, opset == 17
  - exactly one input "input" (shape [1,3,224,224]) and one output "output"
  - labels.json class count matches metadata num_classes
  - preprocessing contract fields in metadata are correct
  - ONNX Runtime produces valid logits from a real image via shared preprocessing

Optionally writes a tensor-head fixture JSON for Android-side verification.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch

from preprocess import preprocess_image_path_batched
from profiles import EXPORT_ROOT, REPO_ROOT, get_profile, list_class_dirs, list_images_for_class


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify exported ONNX model for a profile.")
    parser.add_argument("--profile", required=True, choices=["hymenoptera", "swiss_trains"])
    parser.add_argument("--write-fixture", action="store_true", help="Write tensor head fixture JSON.")
    return parser.parse_args()


def choose_sample_image(profile_name: str) -> Path:
    profile = get_profile(profile_name)
    if profile.split_manifest.exists():
        with profile.split_manifest.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        entries = payload.get("entries", [])
        if entries:
            path = Path(entries[0]["path"])
            return path if path.is_absolute() else REPO_ROOT / path

    class_dirs = list_class_dirs(profile.raw_root)
    for class_dir in class_dirs:
        images = list_images_for_class(class_dir)
        if images:
            return images[0]
    raise RuntimeError(f"No images found under {profile.raw_root}")


def softmax(logits: np.ndarray) -> np.ndarray:
    shifted = logits - np.max(logits, axis=1, keepdims=True)
    exp_values = np.exp(shifted)
    return exp_values / np.sum(exp_values, axis=1, keepdims=True)


def main() -> int:
    args = parse_args()
    profile = get_profile(args.profile)
    prefix = profile.artifact_prefix
    onnx_path = EXPORT_ROOT / f"{prefix}.onnx"
    labels_path = EXPORT_ROOT / f"{prefix}_labels.json"
    metadata_path = EXPORT_ROOT / f"{prefix}_model_metadata.json"
    fixture_path = EXPORT_ROOT / f"{prefix}_test_fixture_tensor_head.json"

    for path in [onnx_path, labels_path, metadata_path]:
        if not path.exists():
            raise FileNotFoundError(f"Required artifact missing: {path}")

    onnx_model = onnx.load(str(onnx_path))
    onnx.checker.check_model(onnx_model)
    opsets = {opset.domain: opset.version for opset in onnx_model.opset_import}
    if opsets.get("", 0) != 17:
        raise RuntimeError(f"ONNX opset is {opsets.get('', 0)}; expected 17.")

    with labels_path.open("r", encoding="utf-8") as handle:
        labels_payload = json.load(handle)
    with metadata_path.open("r", encoding="utf-8") as handle:
        metadata_payload = json.load(handle)

    labels = labels_payload.get("classes", [])
    if metadata_payload.get("dataset_profile") != profile.name:
        raise RuntimeError("Metadata dataset_profile does not match requested profile.")
    if metadata_payload.get("model_file") != onnx_path.name:
        raise RuntimeError("Metadata model_file does not match ONNX filename.")
    if metadata_payload.get("labels_file") != labels_path.name:
        raise RuntimeError("Metadata labels_file does not match labels filename.")
    if metadata_payload.get("num_classes") != len(labels):
        raise RuntimeError("Metadata num_classes does not match labels length.")
    if metadata_payload.get("input_name") != "input" or metadata_payload.get("output_name") != "output":
        raise RuntimeError("Metadata input/output names do not match contract.")
    if metadata_payload.get("input_shape") != [1, 3, 224, 224]:
        raise RuntimeError("Metadata input_shape does not match contract.")

    providers = ["CPUExecutionProvider"]
    session = ort.InferenceSession(str(onnx_path), providers=providers)
    inputs = session.get_inputs()
    outputs = session.get_outputs()
    if len(inputs) != 1 or len(outputs) != 1:
        raise RuntimeError("Expected one ONNX input and one output.")
    if inputs[0].name != "input" or outputs[0].name != "output":
        raise RuntimeError("ONNX node names do not match required input/output names.")

    sample_image = choose_sample_image(profile.name)
    batch_tensor = preprocess_image_path_batched(sample_image)
    if batch_tensor.shape != (1, 3, 224, 224):
        raise RuntimeError(f"Unexpected input tensor shape: {tuple(batch_tensor.shape)}")
    if not torch.isfinite(batch_tensor).all():
        raise RuntimeError("Input tensor contains non-finite values.")

    logits = session.run(["output"], {"input": batch_tensor.numpy().astype(np.float32)})[0]
    if logits.shape[0] != 1 or logits.shape[1] != len(labels):
        raise RuntimeError(f"Unexpected output shape: {logits.shape}, labels={len(labels)}")
    if not np.isfinite(logits).all():
        raise RuntimeError("Output logits contain non-finite values.")

    probs = softmax(logits)
    top_index = int(np.argmax(probs[0]))
    confidence = float(probs[0][top_index])
    if not math.isfinite(confidence):
        raise RuntimeError("Top-1 confidence is not finite.")

    top_class_id = labels[top_index]["id"]
    print(f"Verified ONNX model: {onnx_path}")
    print(f"Sample image: {sample_image}")
    print(f"Top-1: {top_class_id} ({confidence:.4f})")

    if args.write_fixture:
        flat = batch_tensor.flatten().tolist()
        fixture = {
            "version": 1,
            "dataset_profile": profile.name,
            "input_shape": [1, 3, 224, 224],
            "tensor_head": flat[:20],
        }
        with fixture_path.open("w", encoding="utf-8") as handle:
            json.dump(fixture, handle, indent=2)
            handle.write("\n")
        print(f"Wrote fixture tensor head: {fixture_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
