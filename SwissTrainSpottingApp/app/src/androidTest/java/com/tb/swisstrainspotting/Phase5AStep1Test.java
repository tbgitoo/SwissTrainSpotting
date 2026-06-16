package com.tb.swisstrainspotting;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

/**
 * Phase 5A Step 1: tests + scaffolding for the ONNX inference seam.
 * <p>
 * Tests that can run now (parsing, label loading): pass.
 * Tests requiring runtime inference (session creation / classify): intentionally
 * fail or catch UnsupportedOperationException — full implementation is Phase 5A step 2.
 */
@RunWith(AndroidJUnit4.class)
public class Phase5AStep1Test {

    private Context appContext;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // =========================================================================
    // Label loading tests
    // =========================================================================

    /** labelLoader_imagenet_loads1000Labels */
    @Test
    public void labelLoader_imagenet_loads1000Labels() throws IOException {

        List<String> labels = LabelLoader.loadDefaultLabels(appContext);

        // ImageNet has 1000 classes — verify we loaded exactly that many.
        assertEquals("Should load 1000 ImageNet labels", 1000, labels.size());

        // Verify first and last labels are non-empty strings (not blank lines)
        assertFalse("First label must not be empty", labels.get(0).isEmpty());
        assertFalse("Last label must not be empty", labels.get(labels.size() - 1).isEmpty());
    }

    /** LabelLoader with null path throws IOException for non-existent file. */
    @Test(expected = IOException.class)
    public void labelLoader_throwsOnMissingFile() throws IOException {

        // "nonexistent_file.txt" does not exist in assets — should throw
        LabelLoader.loadLabels(appContext, "nonexistent_file.txt");
    }

    // =========================================================================
    // OnnxClassifier input validation tests (scaffold only — no full inference yet)
    // =========================================================================

    /** onnxClassifier_rejectsWrongInputLength */
    @Test
    public void onnxClassifier_rejectsWrongInputLength() {
        OnnxClassifier classifier = new OnnxClassifier(appContext);
        
        // Too short
        try {
            classifier.classify(new float[100]);
            fail("Should have thrown IllegalArgumentException for too-short tensor");
        } catch (IllegalArgumentException e) {
            // Expected — the scaffold validates input length even before runtime wiring
        }

        // Null is wrong
        try {
            classifier.classify(null);
            fail("Should have thrown IllegalArgumentException for null tensor");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Correct length but not yet implemented — should throw UnsupportedOperationException
        float[] correctLength = new float[ModelConfig.INPUT_ELEMENT_COUNT]; // 150528
        try {
            classifier.classify(correctLength);
            fail("Should have thrown UnsupportedOperationException until Phase 5A step 2");
        } catch (UnsupportedOperationException e) {
            // Expected — inference not yet implemented in this scaffold phase
        }

        classifier.close();
    }

    /** OnnxClassifier rejects null context. */
    @Test(expected = IllegalArgumentException.class)
    public void onnxClassifier_rejectsNullContext() {
        new OnnxClassifier(null);
    }

    /** OnnxClassifier rejects calls after close(). */
    @Test
    public void onnxClassifier_rejectsCallsAfterClose() {
        float[] tensor = new float[ModelConfig.INPUT_ELEMENT_COUNT];
        OnnxClassifier classifier = new OnnxClassifier(appContext);
        classifier.close();

        try {
            classifier.classify(tensor);
            fail("Should have thrown IllegalStateException after close");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    // =========================================================================
    // Session seam placeholder (Phase 5B — model/session creation from assets)
    // =========================================================================

    /** Session creation from assets: placeholder test for Phase 5A step 2. */
    @Test
    public void onnxClassifier_sessionFromAssets_placeholder() throws Exception {
        // This is a Phase 5A step 2 placeholder. The full implementation will:
        // 1. Load mobilenetv2.onnx from appContext.getAssets().open(...)
        // 2. Create OrtSession via OrtEnvironment + AssetDescriptor
        //    (or FileDescriptor if copied to cache first)
        // For now, verify the model file exists as a pre-condition check:
        try (var is = appContext.getAssets().open(ModelConfig.MODEL_FILE)) {
            assertNotNull("mobilenetv2.onnx must exist in assets", is);
        }

        // Verify labels file also exists

        List<String> labels = LabelLoader.loadDefaultLabels(appContext);
        assertEquals(1000, labels.size());

        // TODO (Phase 5A step 2): verify that OnnxClassifier constructor creates a session
        // and that classify() returns correct results for a known input.
    }

    // =========================================================================
    // LogitsParser instrumented tests (validates float[] handling on real device)
    // =========================================================================

    /** LogitsParser: argmax with large number of classes (edge case). */
    @Test
    public void logitsParser_argmax_withLargeClassCount() {
        int numClasses = 1000;
        float[] logits = new float[numClasses];
        // Set index 42 to the max value
        logits[42] = 9.9f;


        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals("argmax should be index 42", 42, result.getClassIndex());
        assertTrue("Confidence should be near 1.0 for large margin", result.getConfidence() > 0.99f);
    }

    /** LogitsParser: single element returns that class with confidence 1.0. */
    @Test
    public void logitsParser_singleElement_returnsClassZeroWithConfidenceOne() {
        float[] logits = new float[]{42.0f};

        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals("Only class available is 0", 0, result.getClassIndex());
        assertEquals("Single-element softmax confidence should be 1.0", 1.0f, result.getConfidence(), 1e-6);
    }
}
