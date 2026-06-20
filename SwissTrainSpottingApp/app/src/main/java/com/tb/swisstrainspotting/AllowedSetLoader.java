package com.tb.swisstrainspotting;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads phase-5E allowed-set artifacts — plain-text files listing which generic ImageNet /
 * MobileNetV2 predictions are in-scope for a given specialized classifier.
 *
 * <p>Asset name convention: {@code <profileId>_allowed_mobilenetv2_labels.txt}.
 * The file contents are ImageNet class names (not the specialized profile's own labels),
 * and serve as the bridge between the generic classifier's output space and the specialized
 * model's applicability domain. Both families must share a label vocabulary for this to work;
 * if the Python export already embeds {@code compatible_generic_labels} in metadata,
 * that path bypasses this loader entirely (see {@link ModelProfile#readAllowedSet}).
 */
public final class AllowedSetLoader {

    private static final String SUFFIX = "_allowed_mobilenetv2_labels.txt";

    private AllowedSetLoader() {}

    /**
     * Build the asset file path for a profile's allowed-set using the naming convention:
     * {@code <profileId>_allowed_mobilenetv2_labels.txt}.
     */
    public static String createAssetPath(String profileId) {
        return profileId + SUFFIX;
    }

    /**
     * Load non-blank lines from the allowed-set asset as an unmodifiable Set.
     *
     * @throws IOException if the asset is missing or has no valid labels
     */
    public static Set<String> load(Context context, String profileId) throws IOException {
        if (context == null || profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("context and profileId must not be null/empty");
        }

        Set<String> labels = new HashSet<>();
        try (InputStream is = context.getAssets().open(createAssetPath(profileId));
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    labels.add(trimmed);
                }
            }
        }

        if (labels.isEmpty()) {
            throw new IOException(
                    "Allowed-set asset has no valid labels: " + createAssetPath(profileId));
        }

        return Collections.unmodifiableSet(labels);
    }

    /**
     * Convert a loaded label set to an AllowedSet for use with {@link ClassificationRouter}.
     */
    public static ClassificationRouter.AllowedSet toAllowedSet(Set<String> labels) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels must not be null or empty");
        }
        return new ClassificationRouter.AllowedSet(labels.toArray(new String[0]));
    }
}
