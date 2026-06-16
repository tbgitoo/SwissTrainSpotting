package com.tb.swisstrainspotting;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 5A Step 3: end-to-end integration from Bitmap through UI result display.
 */
@RunWith(AndroidJUnit4.class)
public class Phase5AStep3InstrumentedTest {

    private static final String METRO_FLON_ASSET = "metro_flon.png";
    private static final long UI_RESULT_TIMEOUT_MS = 60_000L;
    private static final long UI_POLL_INTERVAL_MS = 200L;

    private Context appContext;
    private Context testContext;
    private OnnxClassifier classifier;

    @Before
    public void setUp() throws IOException {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        classifier = new OnnxClassifier(appContext);
    }

    @After
    public void tearDown() {
        if (classifier != null) {
            classifier.close();
        }
    }

    @Test
    public void metroFlon_pipeline_bitmapPreprocessInferenceAndParse() throws IOException {
        Bitmap bitmap = decodeTestAsset(METRO_FLON_ASSET);
        assertNotNull("metro_flon.png must decode to a Bitmap", bitmap);

        float[] tensor = ImagePreprocessor.preprocess(bitmap);
        assertEquals(ModelConfig.INPUT_ELEMENT_COUNT, tensor.length);

        ClassificationResult result = classifier.classify(tensor);

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
    public void activity_displaysClassificationResultAfterImageLoad() throws Exception {
        Uri imageUri = copyTestAssetToAppCache(METRO_FLON_ASSET);
        Intent intent = new Intent(appContext, ImageClassificationActivity.class);
        intent.putExtra(ImageClassificationActivity.EXTRA_PICKER_RESULT_URI, imageUri.toString());

        String classifying = appContext.getString(R.string.classifying);
        String initFailed = appContext.getString(R.string.classifier_init_failed);
        String failed = appContext.getString(R.string.classification_failed);

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            String resultText = waitForClassificationResult(scenario, classifying, initFailed, failed);

            assertNotNull("Classification result text should appear", resultText);
            assertFalse("Result must not stay on loading state", classifying.contentEquals(resultText));
            assertFalse("Classifier must initialize", initFailed.contentEquals(resultText));
            assertFalse("Classification must succeed for metro_flon.png", failed.contentEquals(resultText));
            assertTrue("Result should include confidence percentage", resultText.contains("("));
            assertTrue("Result should include percent sign", resultText.contains("%"));
        }
    }

    private Bitmap decodeTestAsset(String assetName) throws IOException {
        try (InputStream inputStream = testContext.getAssets().open(assetName)) {
            return BitmapFactory.decodeStream(inputStream);
        }
    }

    private Uri copyTestAssetToAppCache(String assetName) throws IOException {
        File outFile = new File(appContext.getCacheDir(), assetName);
        try (InputStream in = testContext.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return FileProvider.getUriForFile(
                appContext,
                appContext.getPackageName() + ".fileprovider",
                outFile
        );
    }

    private static String waitForClassificationResult(
            ActivityScenario<ImageClassificationActivity> scenario,
            String classifying,
            String initFailed,
            String failed
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + UI_RESULT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<String> textRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                TextView resultView = activity.findViewById(R.id.tv_classification_result);
                if (resultView != null && resultView.getText() != null) {
                    textRef.set(resultView.getText().toString());
                }
            });

            String text = textRef.get();
            if (text != null
                    && !text.isEmpty()
                    && !classifying.contentEquals(text)
                    && !initFailed.contentEquals(text)) {
                return text;
            }

            if (failed.contentEquals(text)) {
                return text;
            }

            Thread.sleep(UI_POLL_INTERVAL_MS);
        }
        return null;
    }
}
