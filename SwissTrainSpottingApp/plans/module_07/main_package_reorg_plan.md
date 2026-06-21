# Main package reorganization plan

## 1. Proposed target package structure

- `com.tb.swisstrainspotting.ui` — Activities, navigation enums, and user-facing result formatting tied to Android resources and lifecycle.
- `com.tb.swisstrainspotting.ocr` — On-device OCR abstraction, ML Kit implementation, result types, and text normalization.
- `com.tb.swisstrainspotting.onnx` — ONNX inference, model/profile asset loading, label loading, logits parsing, routing between generic and specialized classifiers, and related result types.
- `com.tb.swisstrainspotting.imageprocess` — Bitmap-to-tensor preprocessing for model input (resize, normalize, NCHW layout).

No additional packages are recommended at this time; the four families above cover all 20 main-source classes without micro-package proliferation.

---

## 2. Class-by-class placement

- `AcquisitionMode`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ui`
  - rationale: Enum encoding gallery vs camera navigation choice passed from `MainActivity` to `ImageClassificationActivity`.
  - ambiguity: none

- `AllowedSetLoader`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Loads profile-scoped allowed-set assets that gate generic→specialized routing; tightly coupled to `ClassificationRouter.AllowedSet`.
  - ambiguity: Could sit beside routing-only types, but it is asset/config loading for the classification pipeline rather than UI.

- `ClassificationResult`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Immutable top-1 output from a single ONNX inference pass (index, label, confidence).
  - ambiguity: none

- `ClassificationRouter`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Runs generic + specialized inference and decides presentation routing mode from allowed-set membership; pure Java, no Android UI.
  - ambiguity: Presentation-aware logic could be argued for `ui`; kept in `onnx` because it orchestrates multi-model inference outputs.

- `ImageClassificationActivity`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting`
  - rationale: Primary app screen in Adnroid. Linked to xml, not a priori reusable. Keep in base package.
  - ambiguity: none

- `ImagePreprocessor`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.imageprocess`
  - rationale: Standalone Bitmap→planar NCHW float tensor conversion with ImageNet normalization.
  - ambiguity: none

- `LabelLoader`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Loads index-ordered label lists from assets for ONNX classifiers (plain text and exported JSON).
  - ambiguity: none

- `LogitsParser`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Stateless argmax + stable softmax over raw ONNX logits; produces `ClassificationResult`.
  - ambiguity: none

- `MainActivity`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting`
  - rationale: Android base classs linked to xml, difficult to re-use, keep in base
  - ambiguity: none

- `MlKitOcrAnalyzer`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ocr`
  - rationale: ML Kit `TextRecognizer` implementation of `OcrAnalyzer` with bitmap scaling and lifecycle management.
  - ambiguity: none

- `ModelConfig`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Hardcoded constants for the Phase 5A generic MobileNetV2 reference model (paths, node names, input shape).
  - ambiguity: References `ImagePreprocessor` dimensions; cross-package import is acceptable and reflects a shared input contract.

- `ModelProfile`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Immutable description of an ONNX model family loaded from exported `_model_metadata.json` assets.
  - ambiguity: none

- `OcrAnalyzer`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ocr`
  - rationale: Interface seam for swappable on-device OCR engines used by the classification screen.
  - ambiguity: none

- `OcrResult`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ocr`
  - rationale: Immutable holder distinguishing empty vs recognized OCR text.
  - ambiguity: none

- `OcrTextNormalizer`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ocr`
  - rationale: Pure helper collapsing ML Kit `Text` blocks into a display-ready string for `MlKitOcrAnalyzer`.
  - ambiguity: none

- `ProfileConfig`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ui`
  - rationale: App-side presentation config (domain display name, out-of-scope prefix) consumed by `RoutedResultFormatter` for conditional UI strings.
  - ambiguity: Loaded from assets like `ModelProfile`, but fields exist solely for user-facing message composition.

- `RoutedClassificationResult`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Data carrier for generic + specialized `ClassificationResult` pairs and the routing mode decided by `ClassificationRouter`.
  - ambiguity: Could follow `ClassificationRouter` into a routing sub-area; grouped under `onnx` to avoid an extra package.

- `RoutedResultFormatter`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.ui`
  - rationale: Formats routed inference output into Android string resources for `TextView` display; requires `Context`.
  - ambiguity: none

- `RoutingMode`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Enum (`DIRECT` / `CONDITIONAL`) describing how routed inference results should be presented; produced by `ClassificationRouter`.
  - ambiguity: Semantically presentation-oriented; kept with router/result types in `onnx` for cohesion.

- `OnnxClassifier`
  - current: `com.tb.swisstrainspotting`
  - proposed: `com.tb.swisstrainspotting.onnx`
  - rationale: Long-lived ONNX Runtime session wrapper: tensor creation, inference, logits extraction, and label lookup.
  - ambiguity: none

---

## 3. Classes that should remain central / application-facing

- `MainActivity` — Application entry point; only responsibility is mode selection and launching the classification flow.
- `ImageClassificationActivity` — Central orchestrator wiring acquisition, preprocessing, dual-model inference, OCR, routing, and result display. 
- `AcquisitionMode` — Small navigation enum owned by the launcher flow; belongs in `ui`.
- `RoutedResultFormatter` — Final presentation seam between inference outputs and visible text; belongs in `ui` with `ProfileConfig`.
- `ProfileConfig` — UI-facing copy configuration for conditional result strings; belongs in `ui` even though it is loaded from assets.

Reusable domain logic (`OnnxClassifier`, `ClassificationRouter`, `ImagePreprocessor`, OCR types) moves out of the flat root so Activities import from semantic packages rather than accumulating more responsibilities.

---

## 4. Ambiguous cases

- **`ClassificationRouter`, `RoutedClassificationResult`, `RoutingMode`**
  - Option A (`onnx`, recommended): Treat multi-model routing as part of the classification pipeline that sits immediately after inference.
  - Option B (`ui`): Emphasize that routing only affects presentation semantics, not which models run.
  - Recommendation: `onnx` — no Android dependencies, invoked synchronously inside the inference executor before UI formatting.

- **`ProfileConfig`**
  - Option A (`ui`, recommended): Fields are exclusively for user-visible conditional messaging via `RoutedResultFormatter`.
  - Option B (`onnx`): Loaded alongside `ModelProfile` by profile ID and conceptually paired with model metadata.
  - Recommendation: `ui` — separation keeps model artifact description (`ModelProfile`) distinct from display copy.

- **`AllowedSetLoader`**
  - Option A (`onnx`, recommended): Asset loader feeding `ClassificationRouter.AllowedSet` for the classification domain.
  - Option B (hypothetical `routing`): Isolate routing-specific asset loading from core inference types.
  - Recommendation: `onnx` — no separate routing package warranted for a single loader class.

- **`ModelConfig` ↔ `ImagePreprocessor` cross-reference**
  - `ModelConfig.INPUT_SHAPE` references `ImagePreprocessor` dimensions and `ImagePreprocessor` has no reverse dependency.
  - No package change needed; just be aware that `onnx` will import from `imageprocess` after the move.

---

## 5. Suggested manual execution order

Move classes in Android Studio (Refactor → Move) in roughly this order so each step leaves the project compilable with minimal broken-import churn. After each batch, run `./gradlew :app:compileDebugJavaWithJavac` (or Build → Make Project).

1. **`imageprocess` — leaf utility**
   - `ImagePreprocessor`

2. **`onnx` — value types and parsers (no router yet)**
   - `ClassificationResult`
   - `RoutingMode`
   - `LogitsParser`

3. **`ocr` — self-contained OCR stack**
   - `OcrResult`
   - `OcrTextNormalizer`
   - `OcrAnalyzer`
   - `MlKitOcrAnalyzer`

4. **`onnx` — asset loaders and config**
   - `ModelConfig` (after `ImagePreprocessor` is in `imageprocess`)
   - `LabelLoader`
   - `ModelProfile`
   - `AllowedSetLoader` (after `ClassificationRouter` inner `AllowedSet` exists, or move router first — see step 5)

5. **`onnx` — routing types (move together)**
   - `RoutedClassificationResult`
   - `ClassificationRouter` (includes nested `AllowedSet`; move before or with `AllowedSetLoader`)

6. **`onnx` — inference engine**
   - `OnnxClassifier`

7. **`ui` — presentation helpers (no Activities yet)**
   - `ProfileConfig`
   - `RoutedResultFormatter`
   - `AcquisitionMode`

8. **Manifest and references outside main source**
   - Update `AndroidManifest.xml` activity `android:name` entries if fully qualified class names change.
   - Update any `androidTest` / unit test imports that reference moved classes (out of scope for this plan, but required for a green build).
   - Search the repo for hardcoded FQCN strings (e.g. instrumentation stubs, `FileProvider` authority is unchanged).

Prefer moving one package family at a time and compiling between families. Leave both Activities in the package root (Android orchestrators)
