package com.tb.swisstrainspotting;

import android.content.Context;

/**
 * Formats a {@link RoutedClassificationResult} into user-facing strings using Android string resources.
 *
 * <p>This is the final seam between inference outputs and the UI. It does not perform any routing
 * decisions — it simply renders whatever mode ({@link RoutingMode#DIRECT} or {@link RoutingMode#CONDITIONAL})
 * the router already determined. Key behaviors:
 *
 * <ul>
 *   <li><b>DIRECT mode:</b> emits a single line with the specialized label and confidence.</li>
 *   <li><b>CONDITIONAL mode:</b> emits two lines — the generic classifier's top prediction, then an
 *       out-of-scope–prefixed conditional line that names the domain and the specialized prediction.
 *       The prefix and domain come from {@link ProfileConfig}, not hardcoded in this class.</li>
 * </ul>
 *
 * Callers pass the already-loaded {@link ProfileConfig} for CONDITIONAL rendering; when it is null,
 * domain-sensitive strings default to empty (never crash). This formatter is stateless all its methods are
 * static and contain no mutable fields.
 */
public final class RoutedResultFormatter {

    private RoutedResultFormatter() {}

    /**
     * Render a routed classification result into user-facing text.
     *
     * <p>DIRECT results return the specialized label + confidence on one line.
     * CONDITIONAL results return two lines: generic prediction followed by a conditional
     * line (using {@code profileConfig}'s out-of-scope prefix and domain display name).
     * Returns a single string suitable for setting on a TextView — call sites should not
     * need to format or reassemble this output.
     */
    public static String format(Context context, RoutedClassificationResult routedResult,
                                ProfileConfig profileConfig) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (routedResult == null) {
            throw new IllegalArgumentException("Routed result must not be null");
        }

        ClassificationResult specializedResult = routedResult.getSpecializedResult();
        if (routedResult.getRoutingMode() == RoutingMode.DIRECT) {
            return context.getString(
                    R.string.specialized_direct_result,
                    specializedResult.getLabel(),
                    specializedResult.getConfidence() * 100f
            );
        }

        String genericLine = context.getString(
                R.string.generic_result_label,
                routedResult.getGenericResult().getLabel()
        );
        String conditionalLine = formatConditionalLine(
                context,
                profileConfig,
                specializedResult.getLabel(),
                specializedResult.getConfidence() * 100f
        );
        return genericLine + "\n" + conditionalLine;
    }

    public static String formatConditionalLine(Context context, ProfileConfig profileConfig,
                                               String specializedLabel, float confidencePercent) {
        String outOfScopePrefix = profileConfig != null ? profileConfig.getOutOfScopePrefix() : "";
        String domainDisplayName = profileConfig != null ? profileConfig.getDomainDisplayName() : "";
        return context.getString(
                R.string.specialized_conditional_result,
                outOfScopePrefix,
                domainDisplayName,
                specializedLabel,
                confidencePercent
        );
    }
}
