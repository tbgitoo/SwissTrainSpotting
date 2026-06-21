"""Shared preprocessing contract: resize 224x224, RGB, ImageNet normalize, NCHW, float32.

This exact pipeline runs in both Python training/export and Android (Module 3).
No crops, no letterboxing, no BGR paths. One implementation serves all scripts.
"""

from __future__ import annotations

from pathlib import Path

import torch
from PIL import Image
from torchvision import transforms


IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]
INPUT_WIDTH = 224
INPUT_HEIGHT = 224


def build_preprocess_transform() -> transforms.Compose:
    """Return the preprocessing pipeline shared with Android (Module 3).

    Must match Android exactly: Resize(224,224) -> ToTensor() [scales to 0-1] -> Normalize(ImageNet mean/std).
    Output is torch.Tensor in NCHW layout with float32 dtype.
    """
    return transforms.Compose(
        [
            transforms.Resize((INPUT_HEIGHT, INPUT_WIDTH)),
            transforms.ToTensor(),
            transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ]
    )


def load_rgb_image(image_path: Path) -> Image.Image:
    with Image.open(image_path) as image:
        return image.convert("RGB")


def preprocess_pil_image(image: Image.Image) -> torch.Tensor:
    transform = build_preprocess_transform()
    return transform(image)


def preprocess_image_path(image_path: Path) -> torch.Tensor:
    image = load_rgb_image(image_path)
    return preprocess_pil_image(image)

"""Load an image and return a batched tensor of shape [1, 3, 224, 224].

Used by export/verification steps that require ONNX-ready batched input.
"""

def preprocess_image_path_batched(image_path: Path) -> torch.Tensor:
    tensor = preprocess_image_path(image_path)
    return tensor.unsqueeze(0).to(dtype=torch.float32)
