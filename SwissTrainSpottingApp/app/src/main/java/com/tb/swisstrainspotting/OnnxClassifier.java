package com.tb.swisstrainspotting;

/**
 * Placeholder for ONNX Runtime inference session management.
 *
 * Phase 5A: constructor + method signatures only — does not yet complete full runtime implementation.
 */
public class OnnxClassifier implements AutoCloseable {

    private boolean closed = false;

    /**
     * Create a classifier that will load the Phase 5A model from assets.
     *
     * Expected input:
     * - float[] tensor of length {@code ModelConfig.INPUT_ELEMENT_COUNT} (150528)
     * - shape {@code [1, 3, 224, 224]} (NCHW)
     *
     * Will use:
     * - {@code OrtEnvironment.getEnvironment()} for singleton environment
     * - One long-lived {@code OrtSession} created once in this constructor or a later init step
     */
    public OnnxClassifier(android.content.Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        // Phase 5A scaffold: session NOT yet created. Phase 5B will gate this with AssetHelper wiring.
    }

    /**
     * Classify an input tensor. Does not run on main thread — caller is responsible for threading.
     * The result must be returned on the calling thread; UI updates are the caller's responsibility.
     *
     * @param inputTensor planar NCHW float[] of length {@code ModelConfig.INPUT_ELEMENT_COUNT}
     * @return ClassificationResult with classIndex and confidence
     * @throws IllegalStateException if this instance has been closed or input tensor length is wrong
     */
    public ClassificationResult classify(float[] inputTensor) {
        // Phase 5A scaffold: return placeholder. Full implementation deferred to Phase 5A step 2.
        if (closed) {
            throw new IllegalStateException("Classifier is closed");
        }
        int expectedLen = ModelConfig.INPUT_ELEMENT_COUNT;
        if (inputTensor == null || inputTensor.length != expectedLen) {
            throw new IllegalArgumentException(
                "Input tensor must have length " + expectedLen + ", got " + 
                (inputTensor == null ? "null" : Integer.toString(inputTensor.length))
            );
        }
        // TODO: Phase 5A step 2 — create ONNX Runtime OnnxTensor, get Session.run(), extract output logits
        throw new UnsupportedOperationException("Inference not yet implemented (Phase 5A step 2)");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // TODO: Phase 5A step 2 — release OrtSession and OnnxTensor instances held here
    }
}
