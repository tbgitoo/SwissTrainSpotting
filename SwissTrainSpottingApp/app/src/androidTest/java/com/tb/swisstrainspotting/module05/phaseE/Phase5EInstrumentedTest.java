package com.tb.swisstrainspotting.module05.phaseE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tb.swisstrainspotting.AllowedSetLoader;
import com.tb.swisstrainspotting.onnx.ClassificationResult;
import com.tb.swisstrainspotting.ClassificationRouter;
import com.tb.swisstrainspotting.RoutedClassificationResult;
import com.tb.swisstrainspotting.onnx.RoutingMode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class Phase5EInstrumentedTest {

    private Context appContext;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void allowedSetLoader_hymenoptera_loadsExpectedAsset() throws IOException {
        Set<String> labels = AllowedSetLoader.load(appContext, "hymenoptera");

        assertTrue(labels.contains("bee"));
        assertTrue(labels.contains("ant"));
        assertTrue(labels.contains("fly"));
        assertFalse(labels.contains("train"));
    }

    @Test
    public void allowedSetLoader_swiss_trains_loadsExpectedAsset() throws IOException {
        Set<String> labels = AllowedSetLoader.load(appContext, "swiss_trains");

        assertTrue(labels.contains("bullet train"));
        assertTrue(labels.contains("electric locomotive"));
        assertFalse(labels.contains("bee"));
    }

    @Test
    public void allowedSets_areDistinctAcrossProfiles() throws IOException {
        Set<String> hymenoptera = AllowedSetLoader.load(appContext, "hymenoptera");
        Set<String> swissTrains = AllowedSetLoader.load(appContext, "swiss_trains");

        assertNotEquals(hymenoptera, swissTrains);
        assertFalse(hymenoptera.contains("bullet train"));
        assertFalse(swissTrains.contains("ant"));
    }

    @Test
    public void routing_followsAllowedSetContents() {
        ClassificationRouter.AllowedSet hymenopteraAllowed =
                AllowedSetLoader.toAllowedSet(new HashSet<>(Arrays.asList("bee", "ant", "fly")));
        ClassificationRouter.AllowedSet swissTrainsAllowed =
                AllowedSetLoader.toAllowedSet(new HashSet<>(Arrays.asList("bullet train", "electric locomotive")));

        ClassificationResult specialized = new ClassificationResult(0, "ants", 0.9f);

        RoutedClassificationResult inScope = ClassificationRouter.route(
                new ClassificationResult(1, "bee", 0.8f),
                specialized,
                hymenopteraAllowed
        );
        RoutedClassificationResult outOfScope = ClassificationRouter.route(
                new ClassificationResult(2, "volcano", 0.7f),
                specialized,
                hymenopteraAllowed
        );
        RoutedClassificationResult trainInScope = ClassificationRouter.route(
                new ClassificationResult(3, "bullet train", 0.85f),
                new ClassificationResult(0, "Re420", 0.92f),
                swissTrainsAllowed
        );

        assertEquals(RoutingMode.DIRECT, inScope.getRoutingMode());
        assertEquals(RoutingMode.CONDITIONAL, outOfScope.getRoutingMode());
        assertEquals(RoutingMode.DIRECT, trainInScope.getRoutingMode());
    }
}
