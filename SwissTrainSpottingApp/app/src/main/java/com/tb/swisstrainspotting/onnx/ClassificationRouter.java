package com.tb.swisstrainspotting.onnx;

import com.tb.swisstrainspotting.ImageClassificationActivity;

import java.util.HashSet;
import java.util.Set;

/**
 * Presentation-aware routing between Module 5C (multi-model inference) and Module 5D (result presentation).
 *
 * <p>Never gates or skips inference: both generic and specialized {@link InferenceRunner} implementations
 * execute unconditionally with the same preprocessed tensor. If no allowed set is present, all results
 * route as {@link RoutingMode#DIRECT}. Otherwise, the generic classifier's top prediction label determines
 * whether the specialized result is presented directly (in-scope) or conditionally/hypothetically
 * (out-of-scope).
 *
 * <p><b>Thread model:</b> fully synchronous with no internal threading — designed to be called from
 * within {@link ImageClassificationActivity}'s background executor and marshaled to the UI thread by
 * the caller.
 */
public final class ClassificationRouter {

    /**
     * Minimal inference seam for Phase 5D.
     *
     * <p>Each runner consumes the same already-preprocessed planar NCHW tensor and returns a
     * fully computed classification result.
     */
    public interface InferenceRunner {
        ClassificationResult classify(float[] inputTensor);
    }

    /**
     * Represents which ImageNet top-prediction labels are considered "compatible" with a
     * specialized classifier's domain.
     *
     * <p>This is intentionally narrow for Phase 5D validation. The hymenoptera model (trained
     * on ants/bees images that were originally tagged as "train" in ImageNet) uses the generic
     * label "train" as its single allowed entry so that routing can be tested with the existing
     * MobileNetV2 + hymenoptera setup.
     *
     * <p>For production Swiss Trains profiles this would list ImageNet class labels corresponding
     * to different rail vehicles (locomotive, passenger carriage, freight car, tram, etc.).
     */
    public static class AllowedSet {
        private final Set<String> labels;

        public AllowedSet(String... labels) {
            this.labels = new HashSet<>();
            for (String label : labels) {
                if (label != null && !label.isEmpty()) {
                    this.labels.add(label);
                }
            }
        }

        public Set<String> getLabels() {
            return labels;
        }

        public boolean contains(String label) {
            return label != null && labels.contains(label);
        }

        public boolean isEmpty() {
            return labels.isEmpty();
        }
    }

    private ClassificationRouter() {}

    /**
     * Route a pair of classification results based on whether the generic top class is in-scope.
     *
     * <p>This method does NOT gate or skip inference. It only determines how to present an already-
     * computed specialized result relative to its generic counterpart.
     *
     * @param genericResult  result from the generic MobileNetV2 classifier (required, non-null)
     * @param specializedResult  result from the specialized classifier (required, non-null — must be computed
     *                           unconditionally before calling this method)
     * @param allowedSet  set of generic labels that are in-scope for the specialized model;
     *                    empty means all results route as DIRECT
     * @return a {@link RoutedClassificationResult} with the appropriate routing mode
     */
    public static RoutedClassificationResult route(
            ClassificationResult genericResult,
            ClassificationResult specializedResult,
            AllowedSet allowedSet) {

        if (genericResult == null) {
            throw new IllegalArgumentException("Generic result must not be null");
        }
        if (specializedResult == null) {
            throw new IllegalArgumentException("Specialized result must not be null — the specialized classifier " +
                    "must run unconditionally (no execution gating)");
        }

        RoutingMode mode;
        String genericLabel = genericResult.getLabel();

        AllowedSet effectiveAllowedSet = allowedSet != null ? allowedSet : new AllowedSet();
        if (effectiveAllowedSet.isEmpty() || effectiveAllowedSet.contains(genericLabel)) {
            mode = RoutingMode.DIRECT;
        } else {
            mode = RoutingMode.CONDITIONAL;
        }

        return new RoutedClassificationResult(genericResult, specializedResult, mode);
    }

    /**
     * Run the generic and specialized classifiers on the same preprocessed tensor, then route the
     * already-computed specialized result for presentation.
     *
     * <p>This method intentionally does not gate specialized execution based on the generic
     * prediction. The specialized runner always executes after the generic runner.
     */
    public static RoutedClassificationResult runAndRoute(
            float[] inputTensor,
            InferenceRunner genericRunner,
            InferenceRunner specializedRunner,
            AllowedSet allowedSet) {

        if (inputTensor == null) {
            throw new IllegalArgumentException("Input tensor must not be null");
        }
        if (genericRunner == null) {
            throw new IllegalArgumentException("Generic runner must not be null");
        }
        if (specializedRunner == null) {
            throw new IllegalArgumentException("Specialized runner must not be null");
        }

        ClassificationResult genericResult = genericRunner.classify(inputTensor);
        ClassificationResult specializedResult = specializedRunner.classify(inputTensor);
        return route(genericResult, specializedResult, allowedSet);
    }

    /**
     * Build an allowed set from the specialized profile's metadata.
     *
     * <p>Phase 5D: reads the metadata/config-defined compatible generic labels that represent the
     * ImageNet classes considered in-scope for this specialized classifier.
     */
    public static AllowedSet fromModelProfile(ModelProfile profile) {
        if (profile == null || profile.getAllowedSet().isEmpty()) {
            return new AllowedSet();
        }

        Set<String> allowedSet = new HashSet<>(profile.getAllowedSet());
        return new AllowedSet(allowedSet.toArray(new String[0]));
    }

    /**
     * For testing: build an allowed set from known ImageNet top-labels that map to this specialized model's domain.
     */
    public static AllowedSet forTesting(String... labels) {
        return new AllowedSet(labels);
    }
}
