package com.tb.swisstrainspotting;

/**
 * Pure Java helper for parsing ONNX logits into a ClassificationResult.
 */
public final class LogitsParser {

    private LogitsParser() {
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
     * </ul>
     *
     * @param logits flat logits array from ONNX output tensor
     * @return ClassificationResult with classIndex and confidence; label remains null here
     * @throws IllegalArgumentException if logits is null, empty, or contains non-finite values
     */
    public static ClassificationResult parse(float[] logits) {
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

        return new ClassificationResult(maxIndex, null, confidence);
    }
}