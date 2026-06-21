package com.tb.swisstrainspotting.module05.phaseB;

import static org.junit.Assert.*;

import com.tb.swisstrainspotting.onnx.ClassificationResult;
import com.tb.swisstrainspotting.onnx.LogitsParser;

import org.junit.Test;

public class LogitsParserProfileTest {

    @Test
    public void parse_withTwoClasses_findsArgmax() {
        float[] logits = {0.5f, 2.3f};
        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals("argmax of [0.5, 2.3] should be index 1", 1, result.getClassIndex());
        assertTrue("confidence must be positive", result.getConfidence() > 0f);
        assertTrue("confidence must be <= 1", result.getConfidence() <= 1.0f);
    }

    @Test
    public void parse_withTwoClasses_softmaxProbabilitiesValid() {
        float[] logits = {-1.0f, 1.0f};
        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals(1, result.getClassIndex());
        assertTrue("confidence should be the softmax probability of class 1",
                result.getConfidence() > 0.7f && result.getConfidence() <= 1.0f);
    }

    @Test
    public void parse_withLabels_twoClasses_mapsCorrectly() {
        float[] logits = {-0.3f, 3.7f};
        java.util.List<String> labels = java.util.Arrays.asList("ants", "bees");

        ClassificationResult result = LogitsParser.parse(logits, labels);

        assertEquals(1, result.getClassIndex());
        assertEquals("bees", result.getLabel());
    }

    @Test
    public void parse_withSmallClassCount_notEqualImagenet() {
        float[] numClasses2Logits = new float[2];
        float[] imagenetLogits = new float[1000];
        numClasses2Logits[1] = 5.0f;
        imagenetLogits[499] = 5.0f;

        ClassificationResult smallResult = LogitsParser.parse(numClasses2Logits);
        ClassificationResult largeResult = LogitsParser.parse(imagenetLogits);

        // Both should succeed independently — no class count coupling
        assertTrue(smallResult.getClassIndex() >= 0);
        assertTrue(largeResult.getClassIndex() >= 0);
        assertEquals(1, (int) smallResult.getClassIndex());
        assertEquals(499, (int) largeResult.getClassIndex());
    }

    @Test
    public void parse_withSingleClass_returnsZero() {
        float[] logits = new float[1];
        logits[0] = 2.5f;

        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals("single-class argmax is always index 0", 0, result.getClassIndex());
        assertEquals(1.0f, result.getConfidence(), 1e-6);
    }

    @Test
    public void parse_withThreeClasses_differentFromTwoAndThousand() {
        float[] logits = new float[3];
        logits[2] = 7.0f;

        ClassificationResult result = LogitsParser.parse(logits);

        assertEquals(2, result.getClassIndex());
        assertTrue(result.getConfidence() > 0.95f);
    }
}
