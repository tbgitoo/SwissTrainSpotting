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
