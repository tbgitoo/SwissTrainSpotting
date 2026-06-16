package com.tb.swisstrainspotting;

import java.util.List;

/**
 * Pure Java helper for parsing ONNX logits into a ClassificationResult.
 */
public final class LogitsParser {

    private LogitsParser() {
    }

    /**
     * Parse a flat logits array into a ClassificationResult without label lookup.
     *
     * @param logits flat logits array from ONNX output tensor
     * @return ClassificationResult with classIndex and confidence; label is null
     */
    public static ClassificationResult parse(float[] logits) {
        return parse(logits, null);
    }

    /**
     * Parse a flat logits array into a ClassificationResult.
     *
     * <p>Contract:
     * <ul>
     *   <li>logits must be non-null and non-empty</li>
     *   <li>all values must be finite (not NaN, not Infinity)</li>
     *   <li>classIndex = argmax(logits)</li>
     *   <li>confidence = stable softmax probability of the argmax class</li>
     *   <li>when labels is non-null, map index to label or fail if out of range</li>
     * </ul>
     *
     * @param logits flat logits array from ONNX output tensor
     * @param labels optional label list for index lookup; may be null
     * @return ClassificationResult with classIndex, optional label, and confidence
     * @throws IllegalArgumentException if logits is null, empty, non-finite, or index out of range
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
