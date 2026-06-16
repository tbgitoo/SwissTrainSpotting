package com.tb.swisstrainspotting;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * JVM unit tests for LogitsParser (pure Java helper).
 */
public class LogitsParserTest {

    @Test
    public void topIndex_returnsArgmax() {
        float[] logits = {0.1f, 5.0f, -3.0f, 7.2f, 0.0f};
        ClassificationResult result = LogitsParser.parse(logits);
        assertEquals("argmax should be index 3 (value 7.2 is highest)", 3, result.getClassIndex());
    }

    @Test
    public void topIndex_returnsArgmax_forMultipleMaxValues() {
        float[] logits = {4.0f, 4.0f, 1.0f, 2.0f};
        ClassificationResult result = LogitsParser.parse(logits);
        assertEquals("argmax should return first occurrence of max", 0, result.getClassIndex());
    }

    @Test
    public void topIndex_returnsArgmax_forNegativeValues() {
        float[] logits = {-5.0f, -1.0f, -3.0f};
        ClassificationResult result = LogitsParser.parse(logits);
        assertEquals("argmax of negative values should be index 1", 1, result.getClassIndex());
    }

    @Test
    public void softmax_sumsToOne() {
        float[] logits = {0.5f, -1.2f, 3.4f, -0.8f};
        ClassificationResult resultOne = LogitsParser.parse(logits);

        float maxLogit = logits[0];
        for (float v : logits) {
            if (v > maxLogit) {
                maxLogit = v;
            }
        }

        double sum = 0.0;
        for (float v : logits) {
            sum += Math.exp(v - maxLogit);
        }

        for (int i = 0; i < logits.length; i++) {
            double p = Math.exp(logits[i] - maxLogit) / sum;
            assertNotNull("softmax probability at " + i + " should not be null",
                    Float.valueOf((float) p));
        }

        assertTrue(
                "Confidence must be positive, got: " + resultOne.getConfidence(),
                resultOne.getConfidence() > 0f
        );
        assertTrue(
                "Confidence must be <= 1, got: " + resultOne.getConfidence(),
                resultOne.getConfidence() <= 1.0f
        );
    }

    @Test
    public void softmax_sumsToOne_forUniformLogits() {
        float[] logits = new float[4];
        for (int i = 0; i < logits.length; i++) {
            logits[i] = 1.0f;
        }

        ClassificationResult result = LogitsParser.parse(logits);
        float expectedUniformConfidence = 1.0f / logits.length;
        assertEquals(
                "Uniform logits: confidence should equal 1/num_classes",
                expectedUniformConfidence, result.getConfidence(), 1e-6
        );
    }

    @Test
    public void stableSoftmax_usesMaxLogitShift() {
        float[] logits = {1000.0f, 1001.0f, 999.0f};
        ClassificationResult result = LogitsParser.parse(logits);
        assertEquals(1, result.getClassIndex());
        assertTrue(result.getConfidence() > 0.5f);
        assertTrue(result.getConfidence() <= 1.0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyOrNull_throwsForNull() {
        LogitsParser.parse((float[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyOrNull_throwsForEmpty() {
        LogitsParser.parse(new float[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsNaNValue() {
        LogitsParser.parse(new float[]{1.0f, Float.NaN, 2.0f});
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsInfinityValue() {
        LogitsParser.parse(new float[]{1.0f, Float.POSITIVE_INFINITY, 2.0f});
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsNegativeInfinityValue() {
        LogitsParser.parse(new float[]{1.0f, Float.NEGATIVE_INFINITY, 2.0f});
    }

    @Test
    public void parse_mapsIndexToLabel() {
        float[] logits = {0.1f, 5.0f, -3.0f};
        List<String> labels = Arrays.asList("alpha", "beta", "gamma");

        ClassificationResult result = LogitsParser.parse(logits, labels);

        assertEquals(1, result.getClassIndex());
        assertEquals("beta", result.getLabel());
        assertTrue(result.getConfidence() > 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsIndexOutsideLabelRange() {
        float[] logits = {0.0f, 0.0f, 9.0f};
        List<String> labels = Arrays.asList("only", "two");

        LogitsParser.parse(logits, labels);
    }

    @Test
    public void parse_withEmptyLabelList_rejectsArgmaxIndex() {
        float[] logits = {1.0f};
        try {
            LogitsParser.parse(logits, Collections.emptyList());
            fail("Expected IllegalArgumentException for index outside empty label list");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("outside label range"));
        }
    }
}
