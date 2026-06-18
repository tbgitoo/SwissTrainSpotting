package com.tb.swisstrainspotting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class ProfileConfigInstrumentedTest {

    private Context appContext;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void load_hymenoptera_parsesExpectedValues() throws IOException, JSONException {
        ProfileConfig config = ProfileConfig.load(appContext, "hymenoptera");

        assertEquals("hymenoptera", config.getProfileId());
        assertEquals("Hymenoptera", config.getDomainDisplayName());
        assertEquals("Not Hymenoptera", config.getOutOfScopePrefix());
    }

    @Test
    public void load_swiss_trains_parsesExpectedValues() throws IOException, JSONException {
        ProfileConfig config = ProfileConfig.load(appContext, "swiss_trains");

        assertEquals("swiss_trains", config.getProfileId());
        assertEquals("SwissTrains", config.getDomainDisplayName());
        assertEquals("Not a train", config.getOutOfScopePrefix());
    }

    @Test
    public void profileConfigs_doNotCrossContaminate() throws IOException, JSONException {
        ProfileConfig hymenoptera = ProfileConfig.load(appContext, "hymenoptera");
        ProfileConfig swissTrains = ProfileConfig.load(appContext, "swiss_trains");

        assertNotEquals(hymenoptera.getOutOfScopePrefix(), swissTrains.getOutOfScopePrefix());
        assertNotEquals(hymenoptera.getDomainDisplayName(), swissTrains.getDomainDisplayName());
    }

    @Test
    public void conditionalLine_hymenoptera_isDomainAware() throws IOException, JSONException {
        ProfileConfig config = ProfileConfig.load(appContext, "hymenoptera");

        String line = RoutedResultFormatter.formatConditionalLine(
                appContext, config, "ants", 99.0f);

        assertTrue(line.contains("Not Hymenoptera"));
        assertTrue(line.contains("if classified within Hymenoptera"));
        assertTrue(line.contains("ants"));
        assertTrue(line.contains("99.0%"));
        assertFalse(line.toLowerCase().contains("train"));
    }

    @Test
    public void conditionalLine_swiss_trains_isDomainAware() throws IOException, JSONException {
        ProfileConfig config = ProfileConfig.load(appContext, "swiss_trains");

        String line = RoutedResultFormatter.formatConditionalLine(
                appContext, config, "Re420", 99.0f);

        assertTrue(line.contains("Not a train"));
        assertTrue(line.contains("if classified within SwissTrains"));
        assertTrue(line.contains("Re420"));
        assertTrue(line.contains("99.0%"));
        assertFalse(line.contains("Hymenoptera"));
    }
}
