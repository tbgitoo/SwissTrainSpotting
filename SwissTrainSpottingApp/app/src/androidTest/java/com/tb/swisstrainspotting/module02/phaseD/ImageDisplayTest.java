package com.tb.swisstrainspotting.module02.phaseD;

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

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tb.swisstrainspotting.ImageClassificationActivity;
import com.tb.swisstrainspotting.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class ImageDisplayTest {

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
