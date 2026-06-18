package com.tb.swisstrainspotting;

import android.content.Context;

/**
 * Composes routed classification result text using app-side profile config and string resources.
 */
public final class RoutedResultFormatter {

    private RoutedResultFormatter() {}

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

    static String formatConditionalLine(Context context, ProfileConfig profileConfig,
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
