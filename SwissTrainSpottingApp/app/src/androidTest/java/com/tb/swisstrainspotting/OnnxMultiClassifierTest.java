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

import org.json.JSONException;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 5C — Multi-model coexistence tests.
 *
 * <p>Validates that two classifier instances (MobileNetV2 + Hymenoptera) can:
 * <ul>
 *   <li>coexist with separate sessions on a shared {@code OrtEnvironment}</li>
 *   <li>run inference on the same input tensor without cross-contamination</li>
 *   <li>maintain independent lifecycle (closing one does not affect the other)</li>
 * </ul>
 *
 * Assumes assets from Phase 5A and 5B are present:
 * {@code mobilenetv2.onnx}, {@code imagenet_classes.txt},
 * {@code hymenoptera.onnx}, {@code hymenoptera_labels.json}.
 */
@RunWith(AndroidJUnit4.class)
public class OnnxMultiClassifierTest {

    private static final float CONFIDENCE_TOLERANCE = 1e-5f;

    private Context appContext;
    private OnnxClassifier mobileNetClassifer;
    private OnnxClassifier hymenopteraClassifier;

    @Before
    public void setUp() throws IOException, JSONException {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Phase 5A reference model (plain-text labels, MobileNetV2)
        mobileNetClassifer = new OnnxClassifier(
                appContext,
                ModelProfile.mobileNetV2("input", "output")
        );

        // Phase 5B profile-driven model (JSON labels, Hymenoptera)
        ModelProfile hymenopteraProfile = ModelProfile.fromMetadataAsset(
                appContext, "hymenoptera_model_metadata.json"
        );
        assertNotNull("Hymenoptera profile must not be null", hymenopteraProfile);
        assertEquals("hymenoptera", hymenopteraProfile.getId());

        hymenopteraClassifier = new OnnxClassifier(appContext, hymenopteraProfile);
    }

    @After
    public void tearDown() {
        if (mobileNetClassifer != null) {
            mobileNetClassifer.close();
            mobileNetClassifer = null;
        }
        if (hymenopteraClassifier != null) {
            hymenopteraClassifier.close();
            hymenopteraClassifier = null;
        }
    }

    // -----------------------------------------------------------------------
    // Phase 5C Test 1 — Coexistence
    // -----------------------------------------------------------------------

    /**
     * Load two model profiles, initialize two classifiers with separate sessions,
     * run inference on both using the same input tensor.
     * Assert both produce valid results with independent class indices / labels.
     */
    @Test
    public void coexistence_twoClassifiers_runInferenceOnSameInput() throws IOException {
        float[] sharedInput = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        ClassificationResult motionetResult = mobileNetClassifer.classify(sharedInput);
        ClassificationResult hymenopteraResult = hymenopteraClassifier.classify(sharedInput);

        // Both produce valid results independently
        assertNotNull("MobileNetV2 result must not be null", motionetResult);
        assertNotNull("Hymenoptera result must not be null", hymenopteraResult);

        // MobileNetV2: 1000 classes, label from plain-text
        List<String> imagenetLabels = LabelLoader.loadDefaultLabels(appContext);
        assertTrue("MobileNetV2 should have labels", imagenetLabels != null && !imagenetLabels.isEmpty());
        assertNotNull(motionetResult.getLabel());

        // Hymenoptera: 2 classes, label from JSON
        List<String> hymenopteraLabels = LabelLoader.loadLabels(appContext, "hymenoptera_labels.json");
        assertEquals(2, hymenopteraLabels.size());
        assertNotNull(hymenopteraResult.getLabel());
        assertTrue("Hymenoptera label should be 'ants' or 'bees'",
                "ants".equals(hymenopteraResult.getLabel()) || "bees".equals(hymenopteraResult.getLabel()));

        // Confidence values are independent and valid
        assertTrue("MobileNetV2 confidence > 0", motionetResult.getConfidence() > 0f);
        assertTrue("MobileNetV2 confidence <= 1", motionetResult.getConfidence() <= 1.0f);
        assertTrue("Hymenoptera confidence > 0", hymenopteraResult.getConfidence() > 0f);
        assertTrue("Hymenoptera confidence <= 1", hymenopteraResult.getConfidence() <= 1.0f);
    }

    // -----------------------------------------------------------------------
    // Phase 5C Test 2 — Isolation (repeatability)
    // -----------------------------------------------------------------------

    /**
     * Run classifier A, then classifier B, then classifier A again.
     * Assert classifier A produces consistent results across runs with no cross-contamination.
     */
    @Test
    public void isolation_repeatabilityAcrossClassifiers() {
        float[] sharedInput = new float[ModelConfig.INPUT_ELEMENT_COUNT];
        Arrays.fill(sharedInput, 0.5f);

        // Classifier A first round
        ClassificationResult a1 = mobileNetClassifer.classify(sharedInput);

        // Classifier B
        ClassificationResult b = hymenopteraClassifier.classify(sharedInput);

        // Classifier A second round
        ClassificationResult a2 = mobileNetClassifer.classify(sharedInput);

        // Classifier A must be consistent across runs
        assertEquals("MobileNetV2 classIndex must be consistent",
                a1.getClassIndex(), a2.getClassIndex());
        assertEquals(
                "MobileNetV2 confidence must be consistent",
                a1.getConfidence(),
                a2.getConfidence(),
                CONFIDENCE_TOLERANCE
        );
        assertEquals("MobileNetV2 label must be consistent",
                a1.getLabel(), a2.getLabel());

        // Classifier B result is independent (not contaminated by A)
        assertNotNull(b.getLabel());
    }

    /**
     * Run classifier B, then classifier A, then classifier B again.
     * Assert classifier B produces consistent results across runs with no cross-contamination.
     */
    @Test
    public void isolation_repeatabyAcrossClassifiers_BFirst() {
        float[] sharedInput = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        // Classifier B first round
        ClassificationResult b1 = hymenopteraClassifier.classify(sharedInput);

        // Classifier A
        ClassificationResult a = mobileNetClassifer.classify(sharedInput);

        // Classifier B second round
        ClassificationResult b2 = hymenopteraClassifier.classify(sharedInput);

        // Classifier B must be consistent across runs
        assertEquals("Hymenoptera classIndex must be consistent",
                b1.getClassIndex(), b2.getClassIndex());
        assertEquals(
                "Hymenoptera confidence must be consistent",
                b1.getConfidence(),
                b2.getConfidence(),
                CONFIDENCE_TOLERANCE
        );
        assertEquals("Hymenoptera label must be consistent",
                b1.getLabel(), b2.getLabel());

        // Classifier A result is independent (not contaminated by B)
        assertNotNull(a.getLabel());
    }

    // -----------------------------------------------------------------------
    // Phase 5C Test 3 — Lifecycle Independence
    // -----------------------------------------------------------------------

    /**
     * Close one classifier and verify the other remains operational.
     */
    @Test
    public void lifecycle_closeOneClassifier_otherStillWorks() throws IOException, JSONException {
        float[] sharedInput = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        // Both should work initially
        ClassificationResult beforeCloseA = mobileNetClassifer.classify(sharedInput);
        ClassificationResult beforeCloseB = hymenopteraClassifier.classify(sharedInput);
        assertNotNull(beforeCloseA.getLabel());
        assertNotNull(beforeCloseB.getLabel());

        // Close a fresh Hymenoptera instance for proper verification
        OnnxClassifier closedInstance = new OnnxClassifier(appContext,
                ModelProfile.fromMetadataAsset(appContext, "hymenoptera_model_metadata.json"));
        closedInstance.close();

        try {
            closedInstance.classify(sharedInput);
            fail("Closed classifier should throw");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Classifier A must still work after closing classifier B's counterpart
        ClassificationResult afterBClose = mobileNetClassifer.classify(sharedInput);
        assertNotNull(afterBClose.getLabel());

        assertEquals("MobileNetV2 result unchanged after Hymenoptera close",
                beforeCloseA.getClassIndex(), afterBClose.getClassIndex());
    }

    /**
     * Close both classifiers and verify they are both invalid afterwards.
     */
    @Test
    public void lifecycle_closeBothClassifiers_bothInvalid() throws IOException {
        float[] sharedInput = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        mobileNetClassifer.close();
        hymenopteraClassifier.close();

        try {
            mobileNetClassifer.classify(sharedInput);
            fail("Closed MobileNetV2 classifier should throw");
        } catch (IllegalStateException e) {
            // Expected
        }

        try {
            hymenopteraClassifier.classify(sharedInput);
            fail("Closed Hymenoptera classifier should throw");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    /**
     * Re-create a classifier after the other is closed; verify it still works.
     */
    @Test
    public void lifecycle_recreateClassifier_afterClosingOther() throws IOException {
        float[] sharedInput = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        // Close MobileNetV2, create a brand-new one
        mobileNetClassifer.close();

        OnnxClassifier freshMobileNet = new OnnxClassifier(appContext);
        ClassificationResult result = freshMobileNet.classify(sharedInput);
        assertNotNull(result.getLabel());
        assertTrue(result.getClassIndex() >= 0);

        // Original Hymenoptera should still be alive
        ClassificationResult hymenopteraResult = hymenopteraClassifier.classify(sharedInput);
        assertNotNull(hymenopteraResult.getLabel());

        freshMobileNet.close();
    }
}
