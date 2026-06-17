from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

import torch
from torch import nn, optim
from torch.utils.data import DataLoader, Dataset
from torchvision.models import MobileNet_V2_Weights, mobilenet_v2

from preprocess import build_preprocess_transform, load_rgb_image
from profiles import CHECKPOINTS_ROOT, REPO_ROOT, get_profile, list_class_dirs, list_images_for_class

SEED = 42
EPOCHS = 15
LEARNING_RATE = 1e-3
BATCH_SIZE = 16
NUM_WORKERS = 0


class ImagePathDataset(Dataset):
    def __init__(self, items: list[tuple[Path, int]], transform) -> None:
        self.items = items
        self.transform = transform

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, int]:
        path, class_index = self.items[index]
        image = load_rgb_image(path)
        tensor = self.transform(image)
        return tensor, class_index


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train MobileNetV2 classifier head for a profile.")
    parser.add_argument("--profile", required=True, choices=["hymenoptera", "swiss_trains"])
    return parser.parse_args()


def repo_path(path_str: str) -> Path:
    candidate = Path(path_str)
    if candidate.is_absolute():
        return candidate
    return REPO_ROOT / candidate


def load_manifest_entries(manifest_path: Path) -> list[tuple[str, Path]]:
    if not manifest_path.exists():
        raise FileNotFoundError(f"Split manifest does not exist: {manifest_path}")
    with manifest_path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    entries: list[tuple[str, Path]] = []
    for item in payload.get("entries", []):
        entries.append((item["class_id"], repo_path(item["path"])))
    return entries


def build_splits(profile_name: str) -> tuple[list[tuple[Path, int]], list[tuple[Path, int]], dict[str, int]]:
    profile = get_profile(profile_name)
    class_dirs = list_class_dirs(profile.raw_root)
    class_ids = [d.name for d in class_dirs]
    class_to_idx = {class_id: index for index, class_id in enumerate(class_ids)}

    val_entries = load_manifest_entries(profile.split_manifest)
    val_set = {(class_id, path.resolve()) for class_id, path in val_entries}

    train_items: list[tuple[Path, int]] = []
    val_items: list[tuple[Path, int]] = []
    for class_dir in class_dirs:
        class_id = class_dir.name
        class_index = class_to_idx[class_id]
        for image_path in list_images_for_class(class_dir):
            key = (class_id, image_path.resolve())
            if key in val_set:
                val_items.append((image_path, class_index))
            else:
                train_items.append((image_path, class_index))

    if not train_items:
        raise RuntimeError("Training split is empty.")
    if not val_items:
        raise RuntimeError("Validation split is empty.")
    return train_items, val_items, class_to_idx


def build_model(num_classes: int) -> nn.Module:
    model = mobilenet_v2(weights=MobileNet_V2_Weights.IMAGENET1K_V1)
    for parameter in model.parameters():
        parameter.requires_grad = False
    in_features = model.classifier[1].in_features
    model.classifier[1] = nn.Linear(in_features, num_classes)
    return model


def evaluate(model: nn.Module, loader: DataLoader, criterion: nn.Module, device: torch.device) -> tuple[float, float]:
    model.eval()
    total_loss = 0.0
    total = 0
    correct = 0
    with torch.no_grad():
        for inputs, targets in loader:
            inputs = inputs.to(device)
            targets = targets.to(device)
            logits = model(inputs)
            loss = criterion(logits, targets)
            total_loss += loss.item() * targets.size(0)
            predictions = torch.argmax(logits, dim=1)
            correct += (predictions == targets).sum().item()
            total += targets.size(0)
    return total_loss / total, correct / total


def main() -> int:
    args = parse_args()
    random.seed(SEED)
    torch.manual_seed(SEED)

    profile = get_profile(args.profile)
    train_items, val_items, class_to_idx = build_splits(profile.name)

    transform = build_preprocess_transform()
    train_dataset = ImagePathDataset(train_items, transform)
    val_dataset = ImagePathDataset(val_items, transform)
    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True, num_workers=NUM_WORKERS)
    val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, num_workers=NUM_WORKERS)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = build_model(num_classes=len(class_to_idx)).to(device)

    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.classifier[1].parameters(), lr=LEARNING_RATE)

    best_acc = -1.0
    best_state = None
    for epoch in range(1, EPOCHS + 1):
        model.train()
        running_loss = 0.0
        seen = 0
        for inputs, targets in train_loader:
            inputs = inputs.to(device)
            targets = targets.to(device)
            optimizer.zero_grad()
            logits = model(inputs)
            loss = criterion(logits, targets)
            loss.backward()
            optimizer.step()
            running_loss += loss.item() * targets.size(0)
            seen += targets.size(0)

        train_loss = running_loss / seen
        val_loss, val_acc = evaluate(model, val_loader, criterion, device)
        print(
            f"epoch={epoch:02d} train_loss={train_loss:.4f} "
            f"val_loss={val_loss:.4f} val_acc={val_acc:.4f}"
        )

        if val_acc > best_acc:
            best_acc = val_acc
            best_state = {k: v.detach().cpu() for k, v in model.state_dict().items()}

    if best_state is None:
        print("Training did not produce a checkpoint state.", file=sys.stderr)
        return 1

    CHECKPOINTS_ROOT.mkdir(parents=True, exist_ok=True)
    checkpoint_path = CHECKPOINTS_ROOT / f"{profile.artifact_prefix}_best_model.pt"
    checkpoint = {
        "profile": profile.name,
        "state_dict": best_state,
        "class_to_idx": class_to_idx,
        "hyperparameters": {
            "seed": SEED,
            "epochs": EPOCHS,
            "learning_rate": LEARNING_RATE,
            "batch_size": BATCH_SIZE,
            "optimizer": "Adam",
            "loss": "CrossEntropyLoss",
            "backbone": "mobilenet_v2",
            "weights": "MobileNet_V2_Weights.IMAGENET1K_V1",
        },
    }
    torch.save(checkpoint, checkpoint_path)
    print(f"Saved checkpoint: {checkpoint_path}")
    print(f"Best validation accuracy: {best_acc:.4f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
