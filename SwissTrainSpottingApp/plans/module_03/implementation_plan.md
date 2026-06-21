# Module 3 — Image Preprocessing Implementation Plan

**Status:** Working implementation plan  
**Depends on:** Module 2 (orientation-corrected `Bitmap` available on classification screen)  
**Reference:** `../plans/01_architecture.md`, `AGENTS.md`  
**Target:** Java + XML, `minSdk 24`, no backend, no persistence

---

## 1. Module goal

**Module 3 does:**
- Accept an in-memory, orientation-corrected `Bitmap` from Module 2.
- Resize it to **224×224** (stretch, not center-crop).
- Convert pixels to **RGB float32** values in **NCHW** layout.
- Apply **ImageNet mean/std normalization** exactly matching the Python training contract.
- Return a flat `float[]` of length **150528** (`1 × 3 × 224 × 224`) ready for Module 5 ONNX input.

**Module 3 does not:**
- Acquire images, decode `Uri`, or apply EXIF correction (Module 2).
- Run ONNX inference, load models, or parse logits (Module 5).
- Train or export models (Module 4).
- Run OCR (Module 6).
- Persist tensors or Bitmaps to disk.
- Add UI beyond what is strictly needed for a later smoke hook (no classification UI in this module).

**Deliverable:** a deterministic, testable `Bitmap` → `float[]` conversion that matches `model_metadata.json` and the architecture contract.

---

## 2. Scope boundaries

### Upstream (Module 2 — not Module 3)
- Photo picker / camera acquisition
- `Uri` decode, stream closing, error toasts
- EXIF orientation correction
- Preview `ImageView` display
- Activity recreation via saved image `Uri`

**Assumption for Module 3:** every `Bitmap` passed to preprocessing is already visually upright. Module 3 must not re-read EXIF or re-rotate.

### Downstream (Module 5 — not Module 3)
- ONNX Runtime session creation
- Feeding `OnnxTensor` / running inference
- Label lookup and confidence display
- Background thread orchestration for inference (Module 5 wires this; Module 3 stays pure conversion)

### Module 3 boundary
Stops at: **“model-input float tensor produced.”**  
Starts at: **“valid upright `Bitmap` in memory.”**

---

## 3. Preprocessing contract

Authoritative source: `../plans/01_architecture.md` §D and `app/src/main/assets/model_metadata.json` (once Module 4 artifacts are copied).

### Input
- `Bitmap` in **ARGB_8888** (or converted internally to ARGB_8888 before sampling).
- Any source width/height; Module 3 performs the resize.

### Steps (in order)

1. **Resize** to **224×224** using stretch resize (`Bitmap.createScaledBitmap(..., filter=true)` or equivalent). No letterboxing, no center-crop.
2. **Sample RGB** from each pixel `(x, y)` where `x ∈ [0, 223]`, `y ∈ [0, 223]`.

   - Fetch pixel data in bulk using:
     `bitmap.getPixels(int[] pixelBuffer, int offset, int stride, int x, int y, int width, int height)`

   - Avoid calling `bitmap.getPixel(x, y)` inside the loop due to performance overhead.

   - The `pixelBuffer` will contain packed ARGB values (`int` per pixel).

   - Extract channels using Android `Color` helpers:
     - `Color.red(argb)`
     - `Color.green(argb)`
     - `Color.blue(argb)`

   - Ignore alpha for model input.
3. **Scale** each channel: `v = channel / 255.0f`
4. **ImageNet normalize** per channel:
   - `mean = [0.485, 0.456, 0.406]`
   - `std  = [0.229, 0.224, 0.225]`
   - `normalized = (v - mean[c]) / std[c]`
5. **Store in NCHW** layout for batch `N = 1`:
   - Channel order: **c = 0 → R**, **c = 1 → G**, **c = 2 → B**
   - Row-major within each channel: `y` outer, `x` inner:
   ```java
   for (int y = 0; y < INPUT_HEIGHT; y++) {
    for (int x = 0; x < INPUT_WIDTH; x++) {
        // read pixel at (x, y)
        // compute normalized R, G, B
        // store at index:
        // c * (INPUT_WIDTH * INPUT_HEIGHT) + y * INPUT_WIDTH + x
    }
   }
   ```
   -The pixel buffer is row-major (`y * INPUT_WIDTH + x`), matching the indexing used for tensor storage.
     


### Output
- Type: `float[]`
- Length: **`3 × 224 × 224 = 150528`**
- Semantics: logical shape **`[1, 3, 224, 224]`** flattened in NCHW order.

### Index formula

For pixel at `(x, y)` and channel `c ∈ {0,1,2}`:

```
index(c, y, x) = c × (224 × 224) + y × 224 + x
```

Equivalent flat layout:

```
[R plane 50176 floats][G plane 50176 floats][B plane 50176 floats]
```

### Uniform-pixel worked example

Pixel RGB `(1, 2, 3)` on 0–255 scale at any `(x, y)` after resize:

```
R_out = (1/255.0f - 0.485f) / 0.229f
G_out = (2/255.0f - 0.456f) / 0.224f
B_out = (3/255.0f - 0.406f) / 0.225f
```

All 50176 positions in each channel plane share the same value when the input bitmap is uniform.

All intermediate computations must be performed in float precision (float32), not double and not integer math.

---

## 4. Proposed implementation seam

### Class
`com.tb.swisstrainspottin.ImagePreprocessor`

### Public API (minimal)
```java
public final class ImagePreprocessor {
    public static final int INPUT_WIDTH = 224;
    public static final int INPUT_HEIGHT = 224;
    public static final int CHANNELS = 3;
    public static final int TENSOR_LENGTH = CHANNELS * INPUT_WIDTH * INPUT_HEIGHT;

    public static float[] preprocess(Bitmap bitmap);
}
```

### Design rules
- **Single responsibility:** `Bitmap` → `float[]` only.
- **No Android UI dependencies** inside the class.
- **No ONNX imports** in Module 3.
- **Null/invalid guard:** reject `null`, recycled, or zero-size `Bitmap` with a clear exception (type TBD: `IllegalArgumentException` is sufficient).
- **No static mutable state.**
- **Constants** for mean, std, width, height, and tensor length live in `ImagePreprocessor` (or a package-private `PreprocessConstants` if needed); values must match `model_metadata.json`.

### Integration hook (later, not Module 3 core)
Module 5 (or a thin caller in `ImageClassificationActivity`) will call `ImagePreprocessor.preprocess(displayBitmap)` off the main thread. Module 3 implementation may add the class only; wiring into the activity is optional smoke-test scope (see §7).

### Why this seam
- Pure Java, one call site, no Activity/Fragment coupling.
- Directly callable from instrumentation tests with synthetic `Bitmap`s.
- Matches AGENTS.md preference for simple structure without new architecture layers.

---

## 5. Testing strategy

### Order of work (mandatory for Module 3)

1. **Define contract** — constants, index formula, normalization (§3).
2. **Write a small set of failing specification tests** — core numerical invariants only (§6).
3. **Implement `ImagePreprocessor`** until those tests pass.
4. **Add secondary tests** — resize/spatial checks (§7).
5. **Add one light smoke/integration check** if useful (§7).

### Why test-first here
- Preprocessing bugs are **silent** (wrong logits, plausible-looking UI).
- The contract is **fully numeric** and knowable before ONNX exists.
- Failures localize to RGB order, layout, normalization, or indexing — not to model quality.
- A handful of deterministic tests gives high confidence without a test framework investment.

### Test placement
- **Primary:** `app/src/test/java/.../ImagePreprocessorTest.java` for pure math/index assertions on hand-built `float[]` expectations and, where needed, small helper methods.
- **Bitmap-dependent checks:** `app/src/androidTest/java/.../ImagePreprocessorInstrumentedTest.java` using synthetic `Bitmap.createBitmap(...)` (Android `Bitmap` is not available on the JVM test classpath).

Keep both files small. No custom test harness, no golden-file pipeline, no snapshot framework.

### Float comparison
- Use **`assertEquals(expected, actual, epsilon)`** with **`epsilon = 1e-4f`** unless a specific test documents a looser tolerance for resize interpolation.


### Note
Bitmap-based tests (most of §6) are expected to run as instrumented tests under androidTest, because Android Bitmap is not available in pure JVM tests.


---

## 6. Initial tests to write before implementation

Write these first; they **must fail** until `ImagePreprocessor` exists.

| # | Test | Purpose |
|---|------|---------|
| 1 | **`tensorLength_is150528`** | Returned array length equals `3 × 224 × 224`. |
| 2 | **`uniformRgb_1_2_3_producesExpectedChannels`** | Build a **224×224** ARGB bitmap with every pixel `RGB(1,2,3)`. After preprocess, every index in the R plane equals `(1/255f - 0.485f) / 0.229f`; G and B planes likewise with their means/stds. Sample at least indices `0`, `50175`, `50176`, `100351`, `100352`, `150527`. |
| 3 | **`singlePixel_mapsToCorrectNchwIndex`** | Build a **224×224** bitmap: all pixels black except `(x=20, y=10)` set to `RGB(255, 0, 0)`. Assert tensor at `index(0,10,20)` equals `(1.0f - 0.485f) / 0.229f` and that G/B values at that spatial location are `(0/255f - mean)/std` within epsilon. Assert a far pixel (e.g. `(0,0)`) differs unless also red. |
| 4 | **`nullBitmap_throws`** | `preprocess(null)` throws (document expected exception type). |

**Implementation gate:** do not consider preprocessing “working” until tests 1–3 pass on device/emulator (instrumented) or via the chosen test split.

---

## 7. Additional tests after the first implementation passes

Secondary priority — add only after §6 is green.

| # | Test | Purpose |
|---|------|---------|
| 5 | **`resize_stretchesNon224UniformImage`** | Build **100×50** uniform `RGB(128,128,128)` bitmap. After preprocess, all 150528 outputs equal the normalized value for `128/255f`. Proves resize ran and sampling covers full 224×224 output. |
| 6 | **`quadrantSpatialSanity`** | Build **224×224** bitmap: top-left quadrant red `(255,0,0)`, other quadrants black. After preprocess, R-plane samples from `(x=50,y=50)` vs `(x=200,y=200)` differ beyond epsilon; confirms spatial structure survives resize/sampling. |
| 7 | **Light smoke (optional)** | From `ImageClassificationActivity` test seam or a package-visible call path, obtain the displayed upright `Bitmap` and run `ImagePreprocessor.preprocess(...)`; assert length 150528 and no exception. Does **not** assert ONNX output. |

Do not add ONNX, OCR, or classification UI tests in Module 3.

---

## 8. Test fixtures and storage

### Preferred: synthetic Bitmaps in test code
- Use `Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)`
- For performance in test setup, prefer `setPixels(...)` with a precomputed `int[]` buffer rather than repeated `setPixel(...)` calls.
- Best for exact numeric tests (§6): no file I/O, no codec variance, no EXIF.

### PNG assets (optional, secondary)
- Location: `app/src/androidTest/assets/` (e.g. `preprocess_quadrant_224.png`).
- Use only for §7 smoke or file-decode integration checks.
- **Do not use JPEG** for exact numeric preprocessing tests — lossy compression and chroma subsampling break byte-exact expectations.

### Do not use
- Module 2 gallery fixtures (`Landscape_*.jpg`) as numeric oracle — they conflate decode/EXIF with preprocessing.
- Emulator gallery / MediaStore images.
- Generated tensors checked into the repo.

---

## 9. Notices / pitfalls

| Pitfall | Guard |
|---------|--------|
| **RGB vs BGR** | Channel 0 must be R from `Color.red()`, not B. |
| **HWC vs NCHW** | Do not interleave `[R,G,B,R,G,B,...]` per pixel. Planes are contiguous: all R, then all G, then all B. |
| **Wrong flat index** | Use `c*50176 + y*224 + x`, not `y*224*3 + x*3 + c`. |
| **Wrong normalization** | Apply `(v/255 - mean) / std`, not `v/255` alone, not `/255` after normalize, not `[0,1]` mean from wrong dataset. |
| **Mean/std channel swap** | R/G/B means and stds are not interchangeable. |
| **Alpha treated as RGB** | Strip alpha; do not normalize alpha into a channel. |
| **Resize side effects** | Stretch to 224×224 even when aspect ratio differs; do not center-crop unless contract changes. |
| **Filtering differences** | Use one consistent resize API; document if bilinear vs nearest — default scaled bitmap filter is acceptable if tests use uniform or quadrant patterns tolerant of interpolation. |
| **Recycled/null Bitmap** | Fail fast before native crash. |
| **Module 2 leakage in tests** | Module 3 tests must not assert EXIF, `Uri`, or picker behavior. Pass already-upright synthetic Bitmaps. |
| **UI thread work** | Preprocessing may be heavy; Module 5 runs off main thread. Module 3 class itself stays thread-agnostic but callers must not block UI once wired. |
| **Metadata drift** | When `model_metadata.json` arrives from Module 4, verify constants match; do not hardcode alternate values. |

---

## 10. Acceptance criteria

Module 3 is done when:

- [ ] `ImagePreprocessor` exists with a single public `preprocess(Bitmap)` entry point.
- [ ] Output `float[]` length is **150528** for every valid input.
- [ ] Resize to **224×224 stretch** is applied before sampling.
- [ ] **RGB** channel order and **NCHW** layout match §3 index formula.
- [ ] **ImageNet** mean/std normalization matches architecture and future `model_metadata.json`.
- [ ] §6 specification tests pass (tensor length, uniform RGB 1/2/3, single-pixel index mapping, null guard).
- [ ] At least one §7 secondary test passes (resize and/or quadrant spatial sanity).
- [ ] No EXIF, ONNX, OCR, persistence, or new Activity/Fragment introduced.
- [ ] No dependency on Module 5 inference to validate preprocessing correctness.
- [ ] Code compiles; relevant unit and/or androidTest targets compile and run green for preprocessing tests.

---

## Recommended prompt split (for later implementation)

**Prompt 1 — tests first:** Implement Module 3 §6 failing tests only (`ImagePreprocessorTest` / instrumented counterpart). No production implementation yet.

**Prompt 2 — implementation:** Implement `ImagePreprocessor` until §6 tests pass. No ONNX wiring.

**Prompt 3 — secondary validation:** Add §7 tests and any optional smoke hook. Still no Module 5 inference.
