package com.tb.swisstrainspotting;

import com.tb.swisstrainspotting.onnx.ClassificationResult;
import com.tb.swisstrainspotting.onnx.RoutingMode;

/**
 * Holds both classifier outputs alongside the routing decision that determines how to present them.
 *
 * <p>In production (Phase 5D+), two classifiers always run: a generic MobileNetV2 and a
 * specialized profile-trained model. This type carries both results plus the {@link RoutingMode}
 * that was determined by checking whether the generic prediction falls within the specialized
 * profile's allowed set. The routing mode controls presentation semantics in {@link RoutedResultFormatter}:
 * <ul>
 *   <li><b>DIRECT</b> — the image is in-scope; show only the specialized result.</li>
 *   <li><b>CONDITIONAL</b> — out-of-scope; show both generic and a conditional specialized line.</li>
 * </ul>
 *
 * This class carries data, not decision logic. Callers should not infer routing correctness from
 * its contents alone — use it to drive the formatter or test assertion of known outcomes.
 */
public final class RoutedClassificationResult {

    private final ClassificationResult genericResult;
    private final ClassificationResult specializedResult;
    private final RoutingMode routingMode;

    public RoutedClassificationResult(ClassificationResult genericResult,
                                       ClassificationResult specializedResult,
                                       RoutingMode routingMode) {
        this.genericResult = genericResult;
        this.specializedResult = specializedResult;
        this.routingMode = routingMode;
    }

    public ClassificationResult getGenericResult() {
        return genericResult;
    }

    public ClassificationResult getSpecializedResult() {
        return specializedResult;
    }

    public RoutingMode getRoutingMode() {
        return routingMode;
    }
}
