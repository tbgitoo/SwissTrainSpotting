from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ProfileConfig:
    name: str
    raw_root: Path
    split_manifest: Path
    artifact_prefix: str
    min_classes: int
    min_images_per_class: int


REPO_ROOT = Path(__file__).resolve().parent.parent
DATA_ROOT = REPO_ROOT / "data"
SPLITS_ROOT = DATA_ROOT / "splits"
EXPORT_ROOT = REPO_ROOT / "export"
CHECKPOINTS_ROOT = EXPORT_ROOT / "checkpoints"

SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg"}


PROFILE_REGISTRY: dict[str, ProfileConfig] = {
    "hymenoptera": ProfileConfig(
        name="hymenoptera",
        raw_root=DATA_ROOT / "raw_hymenoptera",
        split_manifest=SPLITS_ROOT / "hymenoptera_val_manifest.json",
        artifact_prefix="hymenoptera",
        min_classes=2,
        min_images_per_class=5,
    ),
    "swiss_trains": ProfileConfig(
        name="swiss_trains",
        raw_root=DATA_ROOT / "raw_swiss_trains",
        split_manifest=SPLITS_ROOT / "swiss_trains_val_manifest.json",
        artifact_prefix="swiss_trains",
        min_classes=3,
        min_images_per_class=5,
    ),
}


def get_profile(profile_name: str) -> ProfileConfig:
    if profile_name not in PROFILE_REGISTRY:
        supported = ", ".join(sorted(PROFILE_REGISTRY))
        raise ValueError(f"Unknown profile '{profile_name}'. Supported profiles: {supported}")
    return PROFILE_REGISTRY[profile_name]


def list_class_dirs(raw_root: Path) -> list[Path]:
    if not raw_root.exists():
        return []
    class_dirs = [path for path in raw_root.iterdir() if path.is_dir()]
    return sorted(class_dirs, key=lambda p: p.name)


def list_images_for_class(class_dir: Path) -> list[Path]:
    images = [
        path
        for path in class_dir.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_IMAGE_EXTENSIONS
    ]
    return sorted(images, key=lambda p: p.name)
