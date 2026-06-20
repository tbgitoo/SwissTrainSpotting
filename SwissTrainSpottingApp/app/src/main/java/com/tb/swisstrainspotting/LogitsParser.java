package com.tb.swisstrainspotting;

import java.util.List;

/**
 * Stateless logits → top-1 classifier parser operating on the exported ONNX contract.
 *
 * <p>Affiliation: Module 5 inference pipeline. This class is agnostic to profile, label format,
 * or routing — it produces {@link ClassificationResult} from raw tensor output. Label lookup
 * (including JSON index ordering vs plain-text) is handled by the caller via the optional
 * {@code labels} parameter; if provided, each index maps directly to its class name.
 *
 * <p>Stable softmax: subtracts the max logit from every element before exponentiating to avoid
 * overflow. Confidence is the argmax probability (not a cross-entropy score), meaning it represents
 * the model's output-space weight for the predicted class — not calibration quality.
 */
public final class LogitsParser {

    private LogitsParser() {
    }

    /**
     * Parse logits into the top-1 classification result without label lookup.
     *
     * <p>Sets classIndex to argmax(logits) and confidence to the stable softmax probability of that index.
     * The returned result's {@code label} field is null — callers should use the overloaded
     * variant with a labels list for human-readable output.
     */
    public static ClassificationResult parse(float[] logits) {
        return parse(logits, null);
    }

    /**
     * Parse logits into the top-1 classification result with possible label lookup.
     *
     * <p>Core contract: validates logits are non-null, non-empty, and finite; computes argmax for classIndex;
     * applies stable softmax (subtracts max before exp to avoid overflow) for confidence as the predicted-class
     * probability — this reflects output-space weight, not calibration quality. If {@code labels} is provided,
     * maps argmax index to label via flat list indexing; throws if the index falls outside [0, labels.size()).
     */
    public static ClassificationResult parse(float[] logits, List<String> labels) {
        if (logits == null || logits.length == 0) {
            throw new IllegalArgumentException("logits must be non-null and non-empty");
        }

        for (int i = 0; i < logits.length; i++) {
            float value = logits[i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException(
                        "logits contains non-finite value at index " + i
                );
            }
        }

        int maxIndex = 0;
        float maxValue = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > maxValue) {
                maxValue = logits[i];
                maxIndex = i;
            }
        }

        double denominator = 0.0;
        for (float logit : logits) {
            denominator += Math.exp(logit - maxValue);
        }

        float confidence = (float) (1.0 / denominator);

        String label = null;
        if (labels != null) {
            if (maxIndex < 0 || maxIndex >= labels.size()) {
                throw new IllegalArgumentException(
                        "Class index " + maxIndex + " is outside label range [0, "
                                + labels.size() + ")"
                );
            }
            label = labels.get(maxIndex);
        }

        return new ClassificationResult(maxIndex, label, confidence);
    }
}
