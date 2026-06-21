package com.tb.swisstrainspotting.module05.phaseD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.tb.swisstrainspotting.onnx.ClassificationResult;
import com.tb.swisstrainspotting.onnx.ClassificationRouter;
import com.tb.swisstrainspotting.onnx.ModelConfig;
import com.tb.swisstrainspotting.onnx.RoutedClassificationResult;
import com.tb.swisstrainspotting.onnx.RoutingMode;

import org.junit.Test;

public class ClassificationRouterTest {

    @Test
    public void route_inScopeGeneric_presentsSpecializedDirectly() {
        ClassificationResult genericResult = new ClassificationResult(466, "train", 0.91f);
        ClassificationResult specializedResult = new ClassificationResult(1, "bees", 0.87f);

        RoutedClassificationResult routedResult = ClassificationRouter.route(
                genericResult,
                specializedResult,
                ClassificationRouter.forTesting("train")
        );

        assertEquals(RoutingMode.DIRECT, routedResult.getRoutingMode());
        assertSame(genericResult, routedResult.getGenericResult());
        assertSame(specializedResult, routedResult.getSpecializedResult());
    }

    @Test
    public void route_outOfScopeGeneric_presentsSpecializedConditionallyAndKeepsGeneric() {
        ClassificationResult genericResult = new ClassificationResult(980, "volcano", 0.63f);
        ClassificationResult specializedResult = new ClassificationResult(0, "ants", 0.71f);

        RoutedClassificationResult routedResult = ClassificationRouter.route(
                genericResult,
                specializedResult,
                ClassificationRouter.forTesting("train")
        );

        assertEquals(RoutingMode.CONDITIONAL, routedResult.getRoutingMode());
        assertSame(genericResult, routedResult.getGenericResult());
        assertSame(specializedResult, routedResult.getSpecializedResult());
        assertEquals("volcano", routedResult.getGenericResult().getLabel());
        assertEquals("ants", routedResult.getSpecializedResult().getLabel());
    }

    @Test
    public void runAndRoute_outOfScopeGeneric_doesNotGateSpecializedExecution() {
        boolean[] called = new boolean[2];
        float[] sharedTensor = new float[ModelConfig.INPUT_ELEMENT_COUNT];

        RoutedClassificationResult routedResult = ClassificationRouter.runAndRoute(
                sharedTensor,
                inputTensor -> {
                    called[0] = true;
                    assertSame(sharedTensor, inputTensor);
                    return new ClassificationResult(100, "volcano", 0.55f);
                },
                inputTensor -> {
                    called[1] = true;
                    assertSame(sharedTensor, inputTensor);
                    return new ClassificationResult(1, "bees", 0.88f);
                },
                ClassificationRouter.forTesting("train")
        );

        assertTrue("Generic runner must execute", called[0]);
        assertTrue("Specialized runner must still execute", called[1]);
        assertEquals(RoutingMode.CONDITIONAL, routedResult.getRoutingMode());
        assertEquals("bees", routedResult.getSpecializedResult().getLabel());
    }
}
