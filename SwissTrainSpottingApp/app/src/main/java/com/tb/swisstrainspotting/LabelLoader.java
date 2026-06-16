package com.tb.swisstrainspotting;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads label text from an asset file.
 */
public final class LabelLoader {

    private LabelLoader() {}

    /**
     * Load labels line-by-line from the given asset file path.
     *
     * @param assetPath path relative to src/main/assets/
     * @return list of label strings (one per line)
     * @throws IOException if the asset cannot be read
     */
    public static List<String> loadLabels(android.content.Context context, String assetPath) throws IOException {
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
                // Append remaining (unterminated) portion for next read
                if (start < chunk.length()) {
                    lineBuilder.append(chunk.substring(start));
                }
            }
            // Flush remaining content
            String remaining = lineBuilder.toString().trim();
            if (!remaining.isEmpty()) {
                labels.add(remaining);
            }
        }
        return labels;
    }

    /**
     * Convenience: load labels from the Phase 5A default labels file.
     */
    public static List<String> loadDefaultLabels(android.content.Context context) throws IOException {
        return loadLabels(context, ModelConfig.LABELS_FILE);
    }
}
