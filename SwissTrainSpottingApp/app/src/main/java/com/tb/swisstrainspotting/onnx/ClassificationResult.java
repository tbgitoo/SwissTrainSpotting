package com.tb.swisstrainspotting.onnx;

/**
 * Immutable, single-label classification output from one ONNX inference pass.
 *
 * <p>This is the minimal runtime data type — produced after logits parsing (argmax over
 * exported class count) and label lookup via a class index. It carries exactly what callers
 * need to display: which class was predicted, its string name, and confidence. It does not
 * carry any routing or profile metadata; that lives in {@link RoutedClassificationResult} and
 * related config classes.
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
