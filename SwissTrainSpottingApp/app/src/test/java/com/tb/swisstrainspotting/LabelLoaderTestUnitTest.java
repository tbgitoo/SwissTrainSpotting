package com.tb.swisstrainspotting;

import com.tb.swisstrainspotting.onnx.LabelLoader;

import java.io.IOException;

/**
 * Tests for LabelLoader label count and empty-label guards.
 */
public class LabelLoaderTestUnitTest {

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void loadLabels_nullContext_throws() throws IOException {
        LabelLoader.loadLabels(null, "imagenet_classes.txt");
    }

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void loadLabels_emptyPath_throws() throws IOException {
        LabelLoader.loadLabels((android.content.Context) null, "");
    }
}
