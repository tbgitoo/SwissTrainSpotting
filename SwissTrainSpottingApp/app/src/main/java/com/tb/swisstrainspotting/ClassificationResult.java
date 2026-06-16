package com.tb.swisstrainspotting;

/**
 * Immutable result of a single classification inference.
 */
public final class ClassificationResult {

    private final int classIndex;
    private final String label;
    private final float confidence;

    public ClassificationResult(int classIndex, String label, float confidence) {
        this.classIndex = classIndex;
        this.label = label;
        this.confidence = confidence;
    }

    public int getClassIndex() {
        return classIndex;
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }
}
