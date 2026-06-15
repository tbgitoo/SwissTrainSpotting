---
name: packed-vs-planar
description: Avoids packed-vs-planar mistakes in SwissTrainSpottingApp, especially Bitmap.getPixels buffers, NCHW float tensors, and Android-side ONNX input preparation.

---

# Packed vs Planar — SwissTrainSpottingApp

## Purpose

Stop a recurring bug class in this repo: treating **packed Android pixel buffers** and **planar model tensors** as if they share the same length and indexing rules.

A real failure already occurred here: code sized a `getPixels(...)` buffer as `3 * width * height` instead of `width * height`.

This skill is for **Java + Android app code only** (`com.tb.swisstrainspotting`). Contract source: `AGENTS.md`, `plans/module_03/implementation_plan.md`, `plans/01_architecture.md`.

## When to use

Apply before writing or reviewing code that:

- calls `Bitmap.getPixels(...)` or builds a temporary `int[]` from a `Bitmap`
- extracts `Color.red/green/blue(...)` from packed ARGB ints
- allocates `float[]` for ONNX / `ImagePreprocessor` output
- writes flat index math for RGB → NCHW conversion
- reuses constants like `TENSOR_LENGTH`, `INPUT_WIDTH`, `INPUT_HEIGHT`

Especially relevant to **Module 3** (`ImagePreprocessor`) and **Module 5** ONNX input wiring.

## Core rule

**One array element = one thing. Derive size and indexing from that thing — not from a nearby constant.**

Convenient existing constants are a common trap. Reusing a correct constant from the wrong representation is still wrong.

For every buffer, answer before allocating:

1. **What does one element represent?** (one packed pixel vs one channel value at one coordinate)
2. **How many elements are needed?** (write the formula)
3. **What is the flat index formula?** (write it; do not guess)

| Representation | One element is | Size for W×H image |
|----------------|----------------|--------------------|
| **Packed** (Android bitmap pixels) | one ARGB `int` per pixel | `W * H` |
| **Planar** (this repo's model input) | one `float` per channel per pixel | `3 * W * H` |

These sizes are **not interchangeable** even when both describe the same image.

## Repo-specific examples

### Packed pixel buffer (`Bitmap.getPixels`)

```java
int w = bitmap.getWidth();
int h = bitmap.getHeight();
int[] pixels = new int[w * h];   // NOT 3 * w * h
bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

int argb = pixels[y * w + x];
int r = Color.red(argb);
int g = Color.green(argb);
int b = Color.blue(argb);
```

- **Packed:** 4 components (A,R,G,B) live inside one `int`.
- **Buffer index:** `y * w + x` — no channel dimension in the packed buffer.

### Planar model tensor (`ImagePreprocessor` output)

Contract (224×224, NCHW, float32):

```java
public static final int INPUT_WIDTH = 224;
public static final int INPUT_HEIGHT = 224;
public static final int CHANNELS = 3;
public static final int TENSOR_LENGTH = CHANNELS * INPUT_WIDTH * INPUT_HEIGHT; // 150528

float[] tensor = new float[TENSOR_LENGTH];
```

- **Planar:** separate R, G, B planes — one float per channel per pixel.
- **Flat index** for channel `c`, pixel `(x, y)`:

```
index(c, y, x) = c * (INPUT_WIDTH * INPUT_HEIGHT) + y * INPUT_WIDTH + x
```

Layout in memory:

```
[R plane 50176][G plane 50176][B plane 50176]
```

### Correct conversion pattern (sketch)

```java
int[] pixels = new int[INPUT_WIDTH * INPUT_HEIGHT];
scaled.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT);

float[] out = new float[TENSOR_LENGTH];

for (int y = 0; y < INPUT_HEIGHT; y++) {
    for (int x = 0; x < INPUT_WIDTH; x++) {
        int argb = pixels[y * INPUT_WIDTH + x];
        float r = Color.red(argb) / 255.0f;
        float g = Color.green(argb) / 255.0f;
        float b = Color.blue(argb) / 255.0f;
        // normalize per channel, then write to planar index(c, y, x)
    }
}
```

**Do not** store R,G,B interleaved as `[R,G,B,R,G,B,...]` — that is HWC/interleaved, not this repo's NCHW contract.

## Typical failure modes

| Mistake | Why it fails |
|---------|----------------|
| `new int[3 * w * h]` for `getPixels` | Packed buffer needs one `int` per pixel, not per channel |
| `new float[w * h]` for model input | Planar tensor needs `3 * w * h` floats |
| Reusing `TENSOR_LENGTH` for pixel buffer size | Downstream constant applied to upstream packed buffer |
| Index `(y * w + x) * 3 + c` | HWC/interleaved layout — wrong for NCHW |
| Index `c * w + x` (missing `y * w`) | Broken planar indexing |
| Treating `Color.red(argb)` as a separate array slot | Channel lives inside packed `int`; extract first, then write to planar float |
| Using BGR order | Contract is RGB: `c=0` R, `c=1` G, `c=2` B |
| Confusing resize output size with tensor channel count | 224×224 is spatial size; channels are a separate planar dimension |

## Review checklist

Before finishing any Bitmap → tensor change:

- [ ] Named the representation: **packed** or **planar**
- [ ] Wrote buffer size as a formula (`w*h` vs `3*w*h`)
- [ ] Wrote flat index formula on paper or in a comment
- [ ] Pixel buffer uses `int[w*h]`; tensor uses `float[3*w*h]` (or named constants derived correctly)
- [ ] Packed read index: `y * w + x`
- [ ] Planar write index: `c * (w*h) + y * w + x`
- [ ] Channels extracted with `Color.red/green/blue`, not by treating the `int[]` as floats
- [ ] No HWC/interleaved output unless the contract explicitly changed
- [ ] Did not reuse `TENSOR_LENGTH` / `CHANNELS` for unrelated intermediate buffers
- [ ] Module 3 tests still target planar output length **150528** for 224×224

## Where this matters in this repo

| Area | Packed vs planar touchpoint |
|------|----------------------------|
| Module 3 — `ImagePreprocessor` | `getPixels` → normalize → planar NCHW `float[]` |
| Module 5 — ONNX input | Same `float[]` shape fed to runtime; do not re-pack incorrectly |
| androidTest preprocessing tests | Synthetic `Bitmap` + assert planar indices and length |
| OCR / future image seams | Any new Android-side array conversion — re-derive sizes |

When unsure: re-read `plans/module_03/implementation_plan.md` §3 (index formula) and apply this skill before allocating arrays.
