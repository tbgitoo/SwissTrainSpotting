package com.tb.swisstrainspotting;

import com.google.mlkit.vision.text.Text;

/**
 * Pure helper that normalizes OCR output from ML Kit {@link Text} into a consistent,
 * display-ready string.
 *
 * <p>Applies the following transformations:
 * <ol>
 *   <li>Collapse each text block and each sub-block into trimmed, space-separated lines.</li>
 *   <li>Join all non-empty lines with a single space to produce one output string.</li>
 *   <li>Trim the final result.</li>
 *   <li>If the result is empty (whitespace-only OCR input), return an empty string.</li>
 * </ol>
 */
public final class OcrTextNormalizer {

    private OcrTextNormalizer() {
        // Utility class.
    }

    /**
     * Normalize ML Kit {@link Text} output into a display-ready string.
     *
     * @param visionText ML Kit Text output from recognition; must not be null.
     * @return trimmed, whitespace-collapsed text, or empty string when nothing usable was found.
     */
    public static String normalize(Text visionText) {
        if (visionText == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean firstLine = true;

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null || block.getLines() == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line == null || line.getText() == null) continue;

                String trimmed = line.getText().trim();
                if (trimmed.isEmpty()) continue;

                // Collapse internal consecutive whitespace in each line.
                String collapsed = collapseWhitespace(trimmed);

                if (!firstLine) {
                    sb.append(' ');
                }
                firstLine = false;
                sb.append(collapsed);
            }
        }

        return sb.toString().trim();
    }

    /**
     * Collapse runs of whitespace characters to a single ASCII space.
     */
    static String collapseWhitespace(String input) {
        // Use simple regex for readability; the alternative is manual iteration which adds ~20 lines.
        return input.replaceAll("\\s+", " ");
    }
}
