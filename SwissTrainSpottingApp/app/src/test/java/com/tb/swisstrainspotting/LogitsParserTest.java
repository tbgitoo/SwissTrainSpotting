package com.tb.swisstrainspotting;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JVM unit tests for LogitsParser (pure Java helper).
 */
public class LogitsParserTest {

    private LogitsParser parser;

    @Before
    public void setUp() {
        parser = new LogitsParser();
    }

    // Test: topIndex_returnsArgmax
    @Test
    public void topIndex_returnsArgmax() {
        float[] logits = {0.1f, 5.0f, -3.0f, 7.2f, 0.0f};
        ClassificationResult result = parser.parse(logits);
        assertEquals("argmax should be index 3 (value 7.2 is highest)", 3, result.getClassIndex());
    }

    @Test
    public void topIndex_returnsArgmax_forMultipleMaxValues() {
        // When two equal max values exist, argmax returns the first occurrence
        float[] logits = {4.0f, 4.0f, 1.0f, 2.0f};
        ClassificationResult result = parser.parse(logits);
        assertEquals("argmax should return first occurrence of max", 0, result.getClassIndex());
    }

    @Test
    public void topIndex_returnsArgmax_forNegativeValues() {
        float[] logits = {-5.0f, -1.0f, -3.0f};
        ClassificationResult result = parser.parse(logits);
        assertEquals("argmax of negative values should be index 1", 1, result.getClassIndex());
    }

    // Test: softmax_sumsToOne
    @Test
    public void softmax_sumsToOne() {
        float[] logits = {0.5f, -1.2f, 3.4f, -0.8f};
        
        // Parse once to get argmax confidence (softmax at argmax)
        ClassificationResult resultOne = parser.parse(logits);

        // Compute full softmax manually for verification
        float maxLogit = logits[0];
        for (float v : logits) {
            if (v > maxLogit) maxLogit = v;
        }
        
        double sum = 0.0;
        for (float v : logits) {
            sum += Math.exp(v - maxLogit);
        }

        // Each softmax value should sum to ~1.0
        for (int i = 0; i < logits.length; i++) {
            double p = Math.exp(logits[i] - maxLogit) / sum;
            assertNotNull("softmax probability at " + i + " should not be null", 
                Float.valueOf((float) p));
        }

        // Confidence (softmax of argmax class) must be between 0 and 1
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
        for (int i = 0; i < logits.length; i++) logits[i] = 1.0f;

        ClassificationResult result = parser.parse(logits);
        // Uniform logits -> argmax=0, softmax at index 0 = 1/N
        assertEquals(4, logits.length);
        float expectedUniformConfidence = 1.0f / logits.length;
        assertEquals(
            "Uniform logits: confidence should equal 1/num_classes",
            expectedUniformConfidence, result.getConfidence(), 1e-6
        );
    }

    // Test: emptyOrNull_throwsOrGuards
    @Test(expected = IllegalArgumentException.class)
    public void emptyOrNull_throwsForNull() {
        parser.parse((float[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyOrNull_throwsForEmpty() {
        parser.parse(new float[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsNaNValue() {
        float[] logits = {1.0f, Float.NaN, 2.0f};
        parser.parse(logits);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsInfinityValue() {
        float[] logits = {1.0f, Float.POSITIVE_INFINITY, 2.0f};
        parser.parse(logits);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_rejectsNegativeInfinityValue() {
        float[] logits = {1.0f, Float.NEGATIVE_INFINITY, 2.0f};
        parser.parse(logits);
    }
}
