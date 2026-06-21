package com.tb.swisstrainspotting.module05.phaseB;

import static org.junit.Assert.assertEquals;

import com.tb.swisstrainspotting.ui.ProfileConfig;

import org.junit.Test;

public class ProfileConfigTest {

    @Test
    public void buildAssetPath_hymenoptera_conformsToConvention() {
        assertEquals("hymenoptera_profile_config.json", ProfileConfig.buildAssetPath("hymenoptera"));
    }

    @Test
    public void buildAssetPath_swiss_trains_conformsToConvention() {
        assertEquals("swiss_trains_profile_config.json", ProfileConfig.buildAssetPath("swiss_trains"));
    }
}
