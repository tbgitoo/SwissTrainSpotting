package com.tb.swisstrainspotting;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 5B: profile-based artifact family loading and minimal hymenoptera smoke inference.
 *
 * <p>Requires {@code hymenoptera.onnx}, {@code hymenoptera_labels.json}, and
 * {@code hymenoptera_model_metadata.json} in main assets.
 */
@RunWith(AndroidJUnit4.class)
public class Phase5BInstrumentedTest {

    private static final float CONFIDENCE_TOLERANCE = 1e-5f;

    private Context appContext;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void modelProfile_fromMetadata_parsesHymenopteraFields() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("dataset_profile", "hymenoptera");
        json.put("model_file", "hymenoptera.onnx");
        json.put("labels_file", "hymenoptera_labels.json");
        json.put("input_name", "input");
        json.put("output_name", "output");
        json.put("num_classes", 2);

        ModelProfile profile = ModelProfile.fromMetadata(json);

        assertEquals("hymenoptera", profile.getId());
        assertEquals("hymenoptera.onnx", profile.getModelFile());
        assertEquals("hymenoptera_labels.json", profile.getLabelsFile());
        assertEquals(ModelProfile.LABEL_FORMAT_JSON, profile.getLabelsFormat());
        assertEquals(2, profile.getNumClasses());
    }

    @Test(expected = JSONException.class)
    public void modelProfile_fromMetadata_rejectsMissingDatasetProfile() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model_file", "hymenoptera.onnx");
        json.put("labels_file", "hymenoptera_labels.json");

        ModelProfile.fromMetadata(json);
    }

    @Test
    public void modelProfile_load_hymenoptera_readsMetadataFromAssets() throws Exception {
        ModelProfile profile = ModelProfile.load(appContext, "hymenoptera");

        assertEquals("hymenoptera", profile.getId());
        assertEquals("hymenoptera.onnx", profile.getModelFile());
        assertEquals("hymenoptera_labels.json", profile.getLabelsFile());
        assertEquals(ModelProfile.LABEL_FORMAT_JSON, profile.getLabelsFormat());
        assertEquals("input", profile.getInputNodeName());
        assertEquals("output", profile.getOutputNodeName());
        assertEquals(2, profile.getNumClasses());
    }

    @Test
    public void modelProfile_fromMetadataAsset_hymenoptera_matchesLoadHelper() throws Exception {
        ModelProfile viaAsset = ModelProfile.fromMetadataAsset(
                appContext, "hymenoptera_model_metadata.json");
        ModelProfile viaPrefix = ModelProfile.load(appContext, "hymenoptera");

        assertEquals(viaPrefix.getId(), viaAsset.getId());
        assertEquals(viaPrefix.getModelFile(), viaAsset.getModelFile());
        assertEquals(viaPrefix.getLabelsFile(), viaAsset.getLabelsFile());
        assertEquals(viaPrefix.getNumClasses(), viaAsset.getNumClasses());
    }

    @Test
    public void labelLoader_hymenopteraJson_loadsTwoLabelsFromAssets() throws IOException {
        List<String> labels = LabelLoader.loadLabels(appContext, "hymenoptera_labels.json");

        assertEquals(2, labels.size());
        assertEquals("ants", labels.get(0));
        assertEquals("bees", labels.get(1));
    }

    @Test
    public void onnxClassifier_hymenopteraProfile_initializesWithTwoLabels() throws Exception {
        ModelProfile profile = ModelProfile.load(appContext, "hymenoptera");
        OnnxClassifier classifier = new OnnxClassifier(appContext, profile);

        try {
            List<String> labels = LabelLoader.loadLabels(appContext, profile.getLabelsFile());
            assertEquals(2, labels.size());

            float[] input = new float[ModelConfig.INPUT_ELEMENT_COUNT];
            ClassificationResult result = classifier.classify(input);

            assertNotNull(result);
            assertTrue(result.getClassIndex() >= 0);
            assertTrue(result.getClassIndex() < 2);
            assertNotNull(result.getLabel());
            assertTrue("ants".equals(result.getLabel()) || "bees".equals(result.getLabel()));
            assertTrue(result.getConfidence() > 0f);
            assertTrue(result.getConfidence() <= 1.0f);
            assertFalse(Float.isNaN(result.getConfidence()));
            assertFalse(Float.isInfinite(result.getConfidence()));
        } finally {
            classifier.close();
        }
    }

    @Test
    public void onnxClassifier_hymenopteraProfile_classifyIsRepeatable() throws Exception {
        ModelProfile profile = ModelProfile.load(appContext, "hymenoptera");
        OnnxClassifier classifier = new OnnxClassifier(appContext, profile);

        try {
            float[] input = new float[ModelConfig.INPUT_ELEMENT_COUNT];
            Arrays.fill(input, 0.0f);

            ClassificationResult first = classifier.classify(input);
            ClassificationResult second = classifier.classify(input);

            assertEquals(first.getClassIndex(), second.getClassIndex());
            assertEquals(first.getConfidence(), second.getConfidence(), CONFIDENCE_TOLERANCE);
            assertEquals(first.getLabel(), second.getLabel());
        } finally {
            classifier.close();
        }
    }

    @Test
    public void switchingProfile_isConfigurationOnly_notCodePath() throws Exception {
        ModelProfile imagenet = ModelProfile.mobileNetV2("input", "output");
        ModelProfile hymenoptera = ModelProfile.load(appContext, "hymenoptera");

        assertNotEquals(imagenet.getModelFile(), hymenoptera.getModelFile());
        assertNotEquals(imagenet.getLabelsFile(), hymenoptera.getLabelsFile());
        assertEquals(0, imagenet.getNumClasses());
        assertEquals(2, hymenoptera.getNumClasses());
    }
}
