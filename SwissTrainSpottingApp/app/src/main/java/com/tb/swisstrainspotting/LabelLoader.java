package com.tb.swisstrainspotting;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads label text from an asset file.
 */
public final class LabelLoader {

    private LabelLoader() {}

    /**
     * Load labels line-by-line from the given asset file path.
     *
     * @param context   application or activity context with asset access
     * @param assetPath path relative to src/main/assets/
     * @return immutable list of label strings (one per non-empty trimmed line)
     * @throws IOException if the asset cannot be read or contains no valid labels
     */
    public static List<String> loadLabels(Context context, String assetPath) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (assetPath == null || assetPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset path must not be empty");
        }

        List<String> labels = new ArrayList<>();
        try (InputStream is = context.getAssets().open(assetPath)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            StringBuilder lineBuilder = new StringBuilder();
            while ((bytesRead = is.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, bytesRead, "UTF-8");
                int start = 0;
                int nlIndex;
                while ((nlIndex = chunk.indexOf('\n', start)) != -1) {
                    String line = chunk.substring(start, nlIndex).trim();
                    if (!line.isEmpty()) {
                        labels.add(line);
                    }
                    start = nlIndex + 1;
                }
                if (start < chunk.length()) {
                    lineBuilder.append(chunk.substring(start));
                }
            }
            String remaining = lineBuilder.toString().trim();
            if (!remaining.isEmpty()) {
                labels.add(remaining);
            }
        }

        if (labels.isEmpty()) {
            throw new IOException("Label file is empty or contains no valid lines: " + assetPath);
        }

        return Collections.unmodifiableList(labels);
    }

    /**
     * Convenience: load labels from the Phase 5A default labels file.
     */
    public static List<String> loadDefaultLabels(Context context) throws IOException {
        return loadLabels(context, ModelConfig.LABELS_FILE);
    }
}
