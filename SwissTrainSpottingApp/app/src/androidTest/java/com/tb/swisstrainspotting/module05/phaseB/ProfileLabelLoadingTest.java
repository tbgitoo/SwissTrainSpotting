package com.tb.swisstrainspotting.module05.phaseB;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tb.swisstrainspotting.onnx.LabelLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

/**
 * Phase 5B: tests for loading Hymenoptera-specific model labels via LabelLoader.
 */
@RunWith(AndroidJUnit4.class)
public class ProfileLabelLoadingTest {

    private Context appContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void loadJsonLabels_hymenoptera_returnsTwoLabels() throws IOException {
        List<String> labels = LabelLoader.loadLabels(appContext(), "hymenoptera_labels.json");

        assertNotNull("labels must not be null", labels);
        assertEquals(2, labels.size());
        assertFalse(labels.isEmpty());
    }

    @Test
    public void loadJsonLabels_hymenoptera_labelsCorrect() throws IOException {
        List<String> labels = LabelLoader.loadLabels(appContext(), "hymenoptera_labels.json");

        assertEquals("ants", labels.get(0));
        assertEquals("bees", labels.get(1));
    }

    @Test
    public void loadJsonLabels_hymenoptera_notEqualImagenetCount() {
        try {
            List<String> imagenet = LabelLoader.loadDefaultLabels(appContext());
            List<String> hymenoptera = LabelLoader.loadLabels(appContext(), "hymenoptera_labels.json");

            assertNotEquals("Hymenoptera labels count must differ from ImageNet",
                    imagenet.size(), hymenoptera.size());
        } catch (IOException e) {
            fail("Should not throw for existing label files: " + e.getMessage());
        }
    }

    @Test(expected = IOException.class)
    public void loadJsonLabels_nonexistentFile_throws() throws IOException {
        LabelLoader.loadLabels(appContext(), "nonexistent_labels.json");
    }
}
