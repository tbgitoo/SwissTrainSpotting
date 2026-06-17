package com.tb.swisstrainspotting;

/**
 * Presentation mode for a routed classification result in Phase 5D.
 */
public enum RoutingMode {
    /** Specialized result is presented directly — generic top prediction matches the allowed set. */
    DIRECT,
    /** Specialized result is presented conditionally/hypothetically — generic top prediction did not match. */
    CONDITIONAL
}
