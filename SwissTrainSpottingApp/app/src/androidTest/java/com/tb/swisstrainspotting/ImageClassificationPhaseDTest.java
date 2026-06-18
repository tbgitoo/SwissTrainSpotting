package com.tb.swisstrainspotting;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class ImageClassificationPhaseDTest {

    private static final String FIXTURE_BASELINE = "Landscape_1.jpg";
    private static final String FIXTURE_EXIF_90 = "Landscape_6.jpg";

    @Test
    public void previewShowsDrawableAfterLoadingBaselineFixture() throws IOException {
        Intent intent = createIntentWithFixture(FIXTURE_BASELINE);

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ImageView preview = activity.findViewById(R.id.ivPlaceholder);
                assertNotNull(preview.getDrawable());
                assertTrue(preview.getVisibility() == View.VISIBLE);
            });
        }
    }

    @Test
    public void previewShowsExifCorrectedDimensionsForLandscape6() throws IOException {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        copyFixtureToAppCache(testContext, appContext, FIXTURE_EXIF_90);
        File fixtureFile = new File(appContext.getCacheDir(), FIXTURE_EXIF_90);
        Bitmap rawDecoded = BitmapFactory.decodeFile(fixtureFile.getAbsolutePath());
        assertNotNull(rawDecoded);
        assertTrue(rawDecoded.getHeight() > rawDecoded.getWidth());

        Intent intent = createIntentWithFixture(FIXTURE_EXIF_90);

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ImageView preview = activity.findViewById(R.id.ivPlaceholder);
                BitmapDrawable drawable = (BitmapDrawable) preview.getDrawable();
                assertNotNull(drawable);
                Bitmap bitmap = drawable.getBitmap();
                assertNotNull(bitmap);
                assertTrue(bitmap.getWidth() > bitmap.getHeight());
            });
        }
    }

    @Test
    public void activityRecreationRestoresDisplayedImage() throws IOException {
        Intent intent = createIntentWithFixture(FIXTURE_BASELINE);

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ImageView preview = activity.findViewById(R.id.ivPlaceholder);
                assertNotNull(preview.getDrawable());
                assertTrue(preview.getVisibility() == View.VISIBLE);
            });

            scenario.recreate();

            scenario.onActivity(activity -> {
                ImageView preview = activity.findViewById(R.id.ivPlaceholder);
                assertNotNull(preview.getDrawable());
                assertTrue(preview.getVisibility() == View.VISIBLE);
                BitmapDrawable drawable = (BitmapDrawable) preview.getDrawable();
                Bitmap bitmap = drawable.getBitmap();
                assertNotNull(bitmap);
                assertTrue(bitmap.getWidth() > bitmap.getHeight());
            });
        }
    }

    @Test
    public void routedDirectResult_isPresentedWithoutConditionalFraming() throws IOException {
        Intent intent = createIntentWithFixture(FIXTURE_BASELINE);

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                RoutedClassificationResult routedResult = new RoutedClassificationResult(
                        new ClassificationResult(466, "train", 0.91f),
                        new ClassificationResult(1, "bees", 0.875f),
                        RoutingMode.DIRECT
                );

                activity.applyRoutedResult(routedResult);

                TextView resultView = activity.findViewById(R.id.tv_classification_result);
                String text = resultView.getText().toString();
                assertTrue(text.contains("bees"));
                assertTrue(text.contains("87.5%"));
                assertTrue(!text.contains("Doesn't look like a train"));
                assertTrue(!text.contains("Generic classification:"));
            });
        }
    }

    @Test
    public void routedConditionalResult_surfacesGenericAndHypotheticalSpecializedText() throws IOException {
        Intent intent = createIntentWithFixture(FIXTURE_BASELINE);

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                RoutedClassificationResult routedResult = new RoutedClassificationResult(
                        new ClassificationResult(980, "volcano", 0.63f),
                        new ClassificationResult(0, "ants", 0.712f),
                        RoutingMode.CONDITIONAL
                );

                activity.applyRoutedResult(routedResult);

                TextView resultView = activity.findViewById(R.id.tv_classification_result);
                String text = resultView.getText().toString();
                assertTrue(text.contains("Generic classification: volcano"));
                assertTrue(text.contains("Not Hymenoptera"));
                assertTrue(text.contains("if classified within Hymenoptera"));
                assertTrue(text.contains("ants"));
                assertTrue(text.contains("71.2%"));
                assertTrue(!text.contains("Doesn't look like a train"));
                assertTrue(!text.toLowerCase().contains("train"));
            });
        }
    }

    @Test
    public void routedConditionalResult_swissTrainsProfile_usesTrainDomainWording() throws Exception {
        Intent intent = createIntentWithFixture(FIXTURE_BASELINE);
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProfileConfig swissTrainsConfig = ProfileConfig.load(appContext, "swiss_trains");

        try (ActivityScenario<ImageClassificationActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                RoutedClassificationResult routedResult = new RoutedClassificationResult(
                        new ClassificationResult(980, "volcano", 0.63f),
                        new ClassificationResult(0, "Re420", 0.99f),
                        RoutingMode.CONDITIONAL
                );

                String text = activity.formatRoutedResult(routedResult, swissTrainsConfig);

                assertTrue(text.contains("Generic classification: volcano"));
                assertTrue(text.contains("Not a train"));
                assertTrue(text.contains("if classified within SwissTrains"));
                assertTrue(text.contains("Re420"));
                assertTrue(text.contains("99.0%"));
                assertTrue(!text.contains("Not Hymenoptera"));
            });
        }
    }

    private static Intent createIntentWithFixture(String assetName) throws IOException {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri imageUri = copyFixtureToAppCache(testContext, appContext, assetName);
        Intent intent = new Intent(appContext, ImageClassificationActivity.class);
        intent.putExtra(ImageClassificationActivity.EXTRA_PICKER_RESULT_URI, imageUri.toString());
        return intent;
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
