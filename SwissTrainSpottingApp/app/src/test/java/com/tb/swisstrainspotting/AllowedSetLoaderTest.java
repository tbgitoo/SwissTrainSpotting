package com.tb.swisstrainspotting;

import static org.junit.Assert.*;

import com.tb.swisstrainspotting.onnx.AllowedSetLoader;

import org.junit.Test;

public class AllowedSetLoaderTest {

    @Test
    public void createAssetPath_hymenoptera_conformsToConvention() {
        String path = AllowedSetLoader.createAssetPath("hymenoptera");
        assertEquals("hymenoptera_allowed_mobilenetv2_labels.txt", path);
    }

    @Test
    public void createAssetPath_swiss_trains_conformsToConvention() {
        String path = AllowedSetLoader.createAssetPath("swiss_trains");
        assertEquals("swiss_trains_allowed_mobilenetv2_labels.txt", path);
    }
}
