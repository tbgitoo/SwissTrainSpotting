package com.tb.swisstrainspotting;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 5A Step 2: runtime integration tests for {@link OnnxClassifier}.
 *
 * <p>Requires {@code mobilenetv2.onnx} and {@code imagenet_classes.txt} in assets.
 */
@RunWith(AndroidJUnit4.class)
public class OnnxClassifierInstrumentedTest {

    private static final float CONFIDENCE_TOLERANCE = 1e-5f;

    private Context appContext;
    private OnnxClassifier classifier;

    @Before
    public void setUp() throws IOException {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        classifier = new OnnxClassifier(appContext);
    }

    @After
    public void tearDown() {
        if (classifier != null) {
            classifier.close();
        }
    }

    @Test
    public void classifier_initializesFromMobilenetOnnx() throws IOException {
        assertNotNull(classifier);
        List<String> labels = LabelLoader.loadDefaultLabels(appContext);
        assertEquals(1000, labels.size());
    }

    @Test
    public void labels_loadFromImagenetClassesAsset() throws IOException {
        List<String> labels = LabelLoader.loadDefaultLabels(appContext);
        assertEquals(1000, labels.size());
        assertFalse(labels.get(0).isEmpty());
        assertFalse(labels.get(999).isEmpty());
    }

    @Test
    public void classify_acceptsDeterministicZeroInput() {
        float[] input = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        ClassificationResult result = classifier.classify(input);

        assertNotNull(result);
        assertTrue(result.getClassIndex() >= 0);
        assertTrue(result.getClassIndex() < 1000);
        assertNotNull(result.getLabel());
        assertFalse(result.getLabel().isEmpty());
        assertTrue(result.getConfidence() > 0f);
        assertTrue(result.getConfidence() <= 1.0f);
        assertFalse(Float.isNaN(result.getConfidence()));
        assertFalse(Float.isInfinite(result.getConfidence()));
    }

    @Test
    public void classify_isRepeatableForSameInput() {
        float[] input = new float[ModelConfig.INPUT_ELEMENT_COUNT];
        Arrays.fill(input, 0.0f);

        ClassificationResult first = classifier.classify(input);
        ClassificationResult second = classifier.classify(input);

        assertEquals(first.getClassIndex(), second.getClassIndex());
        assertEquals(
                first.getConfidence(),
                second.getConfidence(),
                CONFIDENCE_TOLERANCE
        );
        assertEquals(first.getLabel(), second.getLabel());
    }

    @Test
    public void classify_rejectsNullInput() {
        try {
            classifier.classify(null);
            fail("Expected IllegalArgumentException for null input");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("150528") || e.getMessage().contains("null"));
        }
    }

    @Test
    public void classify_rejectsWrongInputLength() {
        try {
            classifier.classify(new float[100]);
            fail("Expected IllegalArgumentException for wrong length");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("150528"));
        }
    }

    @Test
    public void classify_rejectsUseAfterClose() {
        float[] input = new float[ModelConfig.INPUT_ELEMENT_COUNT];
        classifier.close();

        try {
            classifier.classify(input);
            fail("Expected IllegalStateException after close");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNullContext() throws IOException {
        new OnnxClassifier(null);
    }
}
