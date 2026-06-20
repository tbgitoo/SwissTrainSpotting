package com.tb.swisstrainspotting;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads label text from an assets file and returns an immutable, index-preserving list.
 *
 * <p>Autodetects the label format by file extension:
 * files ending in {@code _labels.json} are parsed per the Python export schema
 * ({@code classes[].index} → display name), producing a dense index-ordered list.
 * All other assets are treated as plain-text (one label per line).
 *
 * <p>The returned list maps flat index to label directly: {@code list.get(i)} is the label
 * for class index {@code i}. This invariant holds for both formats, so callers can pass this
 * list straight to a logits parser without further transformation.
 */
public final class LabelLoader {

    private LabelLoader() {}

    /**
     * Load labels from an asset, auto-detected as either plain-text or exported JSON format.
     *
     * <p>Extension-based dispatch: files ending in {@code _labels.json} use the
     * {@link #loadJsonLabels(Context, String)} path (index-ordered dense list). Everything else
     * goes through line-by-line loading. Callers do not need to know the distinction — the
     * returned list maps flat class index to label for both formats.
     */
    public static List<String> loadLabels(Context context, String assetPath) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (assetPath == null || assetPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset path must not be empty");
        }

        String lower = assetPath.toLowerCase();
        if (lower.endsWith("_labels.json")) {
            return loadJsonLabels(context, assetPath);
        } else {
            return loadPlainTextLabels(context, assetPath);
        }
    }

    /**
     * Convenience: load labels from the Phase 5A default labels file.
     */
    public static List<String> loadDefaultLabels(Context context) throws IOException {
        return loadPlainFileLabels(context, ModelConfig.LABELS_FILE);
    }

    /**
     * Load plain-text labels (one label per line).
     */
    private static List<String> loadPlainTextLabels(Context context, String assetPath) throws IOException {
        return loadPlainFileLabels(context, assetPath);
    }

    /**
     * Load exported JSON labels keyed by {@code classes[].index}.
     * The returned list is ordered so that each index maps to the correct label.
     */
    private static List<String> loadJsonLabels(Context context, String assetPath) throws IOException {
        try (InputStream is = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject root = new JSONObject(sb.toString());
            JSONArray classes = root.getJSONArray("classes");
            int size = classes.length();
            List<String> labels = new ArrayList<>(size);

            for (int i = 0; i < size; i++) {
                labels.add(null);
            }

            for (int i = 0; i < size; i++) {
                JSONObject cls = classes.getJSONObject(i);
                int idx = cls.getInt("index");
                String displayName = cls.getString("display_name");
                if (idx >= 0 && idx < size) {
                    labels.set(idx, displayName);
                }
            }

            for (int i = 0; i < size; i++) {
                if (labels.get(i) == null) {
                    throw new IOException("JSON label missing entry at index " + i + " in: " + assetPath);
                }
            }

            return Collections.unmodifiableList(labels);
        } catch (JSONException e) {
            throw new IOException("Failed to parse JSON labels: " + assetPath, e);
        }
    }

    /**
     * Load plain-text labels (one label per line). Internal method.
     */
    private static List<String> loadPlainFileLabels(Context context, String assetPath) throws IOException {
        List<String> labels = new ArrayList<>();
        try (InputStream is = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    labels.add(trimmed);
                }
            }
        }

        if (labels.isEmpty()) {
            throw new IOException("Label file is empty or contains no valid lines: " + assetPath);
        }

        return Collections.unmodifiableList(labels);
    }
}
