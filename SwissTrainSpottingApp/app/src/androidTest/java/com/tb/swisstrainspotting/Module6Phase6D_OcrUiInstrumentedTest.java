package com.tb.swisstrainspotting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 6D: instrumentation tests for OCR UI behavior in {@link ImageClassificationActivity}.
 *
 * <p>Uses a stub {@link OcrAnalyzer} via {@link ImageClassificationActivity#setOcrAnalyzerForTesting(OcrAnalyzer)}
 * so tests do not depend on ML Kit runtime output.
 */
@RunWith(AndroidJUnit4.class)
public class Module6Phase6D_OcrUiInstrumentedTest {

    private static final String FIXTURE_BASELINE = "Landscape_1.jpg";
    private static final String STUB_OCR_TEXT = "STUB-OCR-RE420";
    private static final long UI_WAIT_TIMEOUT_MS = 15_000L;

    @After
    public void tearDown() {
        ImageClassificationActivity.setOcrAnalyzerForTesting(null);
    }

    @Test
    public void applyOcrResult_empty_hidesOcrSection() throws IOException {
        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            scenario.onActivity(activity -> {
                activity.applyOcrResult(OcrResult.empty());

                View ocrSection = activity.findViewById(R.id.ll_ocr_section);
                assertEquals(View.GONE, ocrSection.getVisibility());
            });
        }
    }

    @Test
    public void applyOcrResult_nonEmpty_showsOcrSection() throws IOException {
        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            scenario.onActivity(activity -> {
                activity.applyOcrResult(new OcrResult(STUB_OCR_TEXT));

                View ocrSection = activity.findViewById(R.id.ll_ocr_section);
                TextView ocrResult = activity.findViewById(R.id.tv_ocr_result);
                assertEquals(View.VISIBLE, ocrSection.getVisibility());
                assertEquals(STUB_OCR_TEXT, ocrResult.getText().toString());
            });
        }
    }

    @Test
    public void ocrText_doesNotOverwriteClassificationResult() throws IOException {
        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            scenario.onActivity(activity -> {
                String classificationText = "Classification stays separate";
                TextView classificationView = activity.findViewById(R.id.tv_classification_result);
                classificationView.setText(classificationText);

                activity.applyOcrResult(new OcrResult(STUB_OCR_TEXT));

                assertEquals(classificationText, classificationView.getText().toString());
                assertFalse(classificationView.getText().toString().contains(STUB_OCR_TEXT));

                TextView ocrResult = activity.findViewById(R.id.tv_ocr_result);
                assertEquals(STUB_OCR_TEXT, ocrResult.getText().toString());
            });
        }
    }

    @Test
    public void ocrFailureEquivalentPath_doesNotCrashActivity() throws Exception {
        ImageClassificationActivity.setOcrAnalyzerForTesting(new OcrAnalyzer() {
            @Override
            public OcrResult recognize(Bitmap bitmap) {
                throw new RuntimeException("simulated OCR failure");
            }

            @Override
            public void close() {
            }
        });

        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            assertTrue(waitForClassificationResult(scenario));
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertFalse(activity.isDestroyed());
                View ocrSection = activity.findViewById(R.id.ll_ocr_section);
                assertEquals(View.GONE, ocrSection.getVisibility());
            });
        }
    }

    @Test
    public void stubReturningEmpty_keepsOcrHidden_afterImageLoad() throws Exception {
        ImageClassificationActivity.setOcrAnalyzerForTesting(new OcrAnalyzer() {
            @Override
            public OcrResult recognize(Bitmap bitmap) {
                return OcrResult.empty();
            }

            @Override
            public void close() {
            }
        });

        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            assertTrue(waitForClassificationResult(scenario));
            assertTrue(waitForOcrSectionHidden(scenario));

            scenario.onActivity(activity -> {
                View ocrSection = activity.findViewById(R.id.ll_ocr_section);
                assertEquals(View.GONE, ocrSection.getVisibility());
            });
        }
    }

    @Test
    public void stubReturningText_showsOcrAfterImageLoad() throws Exception {
        ImageClassificationActivity.setOcrAnalyzerForTesting(new OcrAnalyzer() {
            @Override
            public OcrResult recognize(Bitmap bitmap) {
                return new OcrResult(STUB_OCR_TEXT);
            }

            @Override
            public void close() {
            }
        });

        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            assertTrue(waitForClassificationResult(scenario));
            assertTrue(waitForOcrSectionVisible(scenario));

            scenario.onActivity(activity -> {
                TextView ocrResult = activity.findViewById(R.id.tv_ocr_result);
                assertEquals(STUB_OCR_TEXT, ocrResult.getText().toString());
            });
        }
    }

    @Test
    public void delayedOcrResult_doesNotBlockClassificationText() throws Exception {
        CountDownLatch ocrStarted = new CountDownLatch(1);
        ImageClassificationActivity.setOcrAnalyzerForTesting(new OcrAnalyzer() {
            @Override
            public OcrResult recognize(Bitmap bitmap) {
                ocrStarted.countDown();
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new OcrResult(STUB_OCR_TEXT);
            }

            @Override
            public void close() {
            }
        });

        try (ActivityScenario<ImageClassificationActivity> scenario = launchWithFixture()) {
            assertTrue(waitForClassificationResult(scenario));
            assertTrue(ocrStarted.await(5L, TimeUnit.SECONDS));

            scenario.onActivity(activity -> {
                TextView classificationView = activity.findViewById(R.id.tv_classification_result);
                String classifying = activity.getString(R.string.classifying);
                String classificationText = classificationView.getText().toString();

                assertNotEquals(classifying, classificationText);
                assertFalse(classificationText.isEmpty());
                assertFalse(classificationText.contains(STUB_OCR_TEXT));

                View ocrSection = activity.findViewById(R.id.ll_ocr_section);
                assertEquals(View.GONE, ocrSection.getVisibility());
            });

            assertTrue(waitForOcrSectionVisible(scenario));

            scenario.onActivity(activity -> {
                TextView classificationView = activity.findViewById(R.id.tv_classification_result);
                TextView ocrResult = activity.findViewById(R.id.tv_ocr_result);

                assertFalse(classificationView.getText().toString().contains(STUB_OCR_TEXT));
                assertEquals(STUB_OCR_TEXT, ocrResult.getText().toString());
            });
        }
    }

    private static ActivityScenario<ImageClassificationActivity> launchWithFixture() throws IOException {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri imageUri = copyFixtureToAppCache(testContext, appContext, FIXTURE_BASELINE);
        Intent intent = new Intent(appContext, ImageClassificationActivity.class);
        intent.putExtra(ImageClassificationActivity.EXTRA_PICKER_RESULT_URI, imageUri.toString());
        return ActivityScenario.launch(intent);
    }

    private static boolean waitForClassificationResult(
            ActivityScenario<ImageClassificationActivity> scenario) throws InterruptedException {
        long deadline = System.currentTimeMillis() + UI_WAIT_TIMEOUT_MS;
        AtomicReference<String> classifyingLabel = new AtomicReference<>();
        scenario.onActivity(activity -> classifyingLabel.set(activity.getString(R.string.classifying)));

        while (System.currentTimeMillis() < deadline) {
            AtomicReference<String> currentText = new AtomicReference<>();
            scenario.onActivity(activity -> {
                TextView classificationView = activity.findViewById(R.id.tv_classification_result);
                currentText.set(classificationView.getText().toString());
            });

            String text = currentText.get();
            if (text != null
                    && !text.isEmpty()
                    && !text.equals(classifyingLabel.get())) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    private static boolean waitForOcrSectionVisible(
            ActivityScenario<ImageClassificationActivity> scenario) throws InterruptedException {
        return waitForOcrSectionVisibility(scenario, View.VISIBLE);
    }

    private static boolean waitForOcrSectionHidden(
            ActivityScenario<ImageClassificationActivity> scenario) throws InterruptedException {
        return waitForOcrSectionVisibility(scenario, View.GONE);
    }

    private static boolean waitForOcrSectionVisibility(
            ActivityScenario<ImageClassificationActivity> scenario,
            int expectedVisibility) throws InterruptedException {
        long deadline = System.currentTimeMillis() + UI_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean matches = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                View ocrSection = activity.findViewById(R.id.ll_ocr_section);
                matches.set(ocrSection.getVisibility() == expectedVisibility);
            });
            if (matches.get()) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    private static Uri copyFixtureToAppCache(Context testContext, Context appContext, String assetName)
            throws IOException {
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
}
