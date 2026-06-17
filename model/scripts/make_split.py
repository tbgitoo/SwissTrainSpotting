from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

from profiles import REPO_ROOT, get_profile, list_class_dirs, list_images_for_class

SPLIT_VERSION = 1
SEED = 42
VAL_RATIO = 0.2


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create deterministic validation split manifest.")
    parser.add_argument("--profile", required=True, choices=["hymenoptera", "swiss_trains"])
    return parser.parse_args()


def to_repo_relative(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def main() -> int:
    args = parse_args()
    try:
        profile = get_profile(args.profile)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    if not profile.raw_root.exists():
        print(f"Raw root is missing: {profile.raw_root}", file=sys.stderr)
        return 1

    class_dirs = list_class_dirs(profile.raw_root)
    if len(class_dirs) < profile.min_classes:
        print(
            f"Profile '{profile.name}' requires at least {profile.min_classes} classes "
            f"but found {len(class_dirs)}",
            file=sys.stderr,
        )
        return 1

    rng = random.Random(SEED)
    entries: list[dict[str, str]] = []
    for class_dir in class_dirs:
        class_id = class_dir.name
        images = list_images_for_class(class_dir)
        if len(images) < profile.min_images_per_class:
            print(
                f"Class '{class_id}' has {len(images)} images but needs at least "
                f"{profile.min_images_per_class}",
                file=sys.stderr,
            )
            return 1

        val_count = max(1, int(len(images) * VAL_RATIO))
        image_indices = list(range(len(images)))
        rng.shuffle(image_indices)
        selected_indices = set(sorted(image_indices[:val_count]))
        for idx in selected_indices:
            entries.append({"class_id": class_id, "path": to_repo_relative(images[idx])})

    entries.sort(key=lambda item: (item["class_id"], item["path"]))
    manifest = {
        "version": SPLIT_VERSION,
        "profile": profile.name,
        "seed": SEED,
        "val_ratio": VAL_RATIO,
        "entries": entries,
    }

    profile.split_manifest.parent.mkdir(parents=True, exist_ok=True)
    with profile.split_manifest.open("w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
        handle.write("\n")

    print(f"Wrote split manifest: {profile.split_manifest}")
    print(f"Validation samples: {len(entries)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
