package com.tb.swisstrainspotting.module05.phaseA;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tb.swisstrainspotting.onnx.ClassificationResult;
import com.tb.swisstrainspotting.onnx.LabelLoader;
import com.tb.swisstrainspotting.onnx.LogitsParser;
import com.tb.swisstrainspotting.onnx.ModelConfig;
import com.tb.swisstrainspotting.onnx.OnnxClassifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

/**
 * Phase 5A: tests for the ONNX inference seam.
 */
@RunWith(AndroidJUnit4.class)
public class OnnxInferenceSeamInstrumentedTest {

    private Context appContext;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void labelLoader_imagenet_loads1000Labels() throws IOException {
        List<String> labels = LabelLoader.loadDefaultLabels(appContext);

        assertEquals("Should load 1000 ImageNet labels", 1000, labels.size());
        assertFalse("First label must not be empty", labels.get(0).isEmpty());
        assertFalse("Last label must not be empty", labels.get(labels.size() - 1).isEmpty());
    }

    @Test(expected = IOException.class)
    public void labelLoader_throwsOnMissingFile() throws IOException {
        LabelLoader.loadLabels(appContext, "nonexistent_file.txt");
    }

    @Test
    public void onnxClassifier_rejectsWrongInputLength() throws IOException {
        OnnxClassifier classifier = new OnnxClassifier(appContext);

        try {
            classifier.classify(new float[100]);
            fail("Should have thrown IllegalArgumentException for too-short tensor");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("150528"));
        }

        try {
            classifier.classify(null);
            fail("Should have thrown IllegalArgumentException for null tensor");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        classifier.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void onnxClassifier_rejectsNullContext() throws IOException {
        new OnnxClassifier(null);
    }

    @Test
    public void onnxClassifier_rejectsCallsAfterClose() throws IOException {
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

    @Test
    public void modelAndLabelsAssetsExist() throws IOException {
        try (var is = appContext.getAssets().open(ModelConfig.MODEL_FILE)) {
            assertNotNull("mobilenetv2.onnx must exist in assets", is);
        }

        List<String> labels = LabelLoader.loadDefaultLabels(appContext);
        assertEquals(1000, labels.size());
    }

    @Test
    public void logitsParser_argmax_withLargeClassCount() {
        int numClasses = 1000;
        float[] logits = new float[numClasses];
        logits[42] = 9.9f;

        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals("argmax should be index 42", 42, result.getClassIndex());
        assertTrue("Confidence should be near 1.0 for large margin", result.getConfidence() > 0.95f);
    }

    @Test
    public void logitsParser_singleElement_returnsClassZeroWithConfidenceOne() {
        float[] logits = new float[]{42.0f};

        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals("Only class available is 0", 0, result.getClassIndex());
        assertEquals("Single-element softmax confidence should be 1.0", 1.0f, result.getConfidence(), 1e-6);
    }
}
