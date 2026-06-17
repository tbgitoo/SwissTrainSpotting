from __future__ import annotations

import argparse
import sys

from profiles import get_profile, list_class_dirs, list_images_for_class


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Discover dataset classes and counts for a profile.")
    parser.add_argument("--profile", required=True, choices=["hymenoptera", "swiss_trains"])
    return parser.parse_args()


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
            f"but found {len(class_dirs)} under {profile.raw_root}",
            file=sys.stderr,
        )
        return 1

    class_counts: dict[str, int] = {}
    for class_dir in class_dirs:
        count = len(list_images_for_class(class_dir))
        class_counts[class_dir.name] = count
        if count < profile.min_images_per_class:
            print(
                f"Class '{class_dir.name}' has {count} images but profile '{profile.name}' "
                f"requires at least {profile.min_images_per_class}",
                file=sys.stderr,
            )
            return 1

    print(f"Profile: {profile.name}")
    print(f"Raw root: {profile.raw_root}")
    print("Class IDs and indices (lexicographic):")
    for index, class_dir in enumerate(class_dirs):
        class_id = class_dir.name
        print(f"  {index}: {class_id} ({class_counts[class_id]} images)")

    total_images = sum(class_counts.values())
    estimated_val = sum(max(1, int(class_counts[class_id] * 0.2)) for class_id in class_counts)
    estimated_train = total_images - estimated_val
    print(f"Total images: {total_images}")
    print(f"Estimated split: train={estimated_train} val={estimated_val} (ratio=0.2, min 1 per class)")
    print("Discovery checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
