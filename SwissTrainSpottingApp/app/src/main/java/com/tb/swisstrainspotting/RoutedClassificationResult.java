package com.tb.swisstrainspotting;

/**
 * Holds a generic classifier result alongside a specialized classifier result,
 * together with the routing mode determined by Phase 5D logic.
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
