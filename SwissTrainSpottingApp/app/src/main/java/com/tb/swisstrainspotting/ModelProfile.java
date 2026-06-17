package com.tb.swisstrainspotting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phase 5B: model-profile description loaded from asset metadata.
 *
 * <p>A profile binds the ONNX model file, labels file (plain-text or JSON),
 * input/output node names, and num_classes — all driven by metadata when present
 * rather than hard-coded constants.
 */
public final class ModelProfile {

    public static final String LABEL_FORMAT_PLAIN_TEXT = "PLAIN_TEXT";
    public static final String LABEL_FORMAT_JSON = "JSON";

    private final String id;
    private final String modelFile;
    private final String labelsFile;
    private final String labelsFormat;
    private final String inputNodeName;
    private final String outputNodeName;
    private final int numClasses;
    private final java.util.Set<String> allowedSet;

    private ModelProfile(String id, String modelFile, String labelsFile,
                          String labelsFormat, String inputNodeName,
                          String outputNodeName, int numClasses) {
        this(id, modelFile, labelsFile, labelsFormat, inputNodeName, outputNodeName, numClasses, java.util.Collections.emptySet());
    }

    private ModelProfile(String id, String modelFile, String labelsFile,
                          String labelsFormat, String inputNodeName,
                          String outputNodeName, int numClasses,
                          java.util.Set<String> allowedSet) {
        this.id = id;
        this.modelFile = modelFile;
        this.labelsFile = labelsFile;
        this.labelsFormat = labelsFormat;
        this.inputNodeName = inputNodeName;
        this.outputNodeName = outputNodeName;
        this.numClasses = numClasses;
        this.allowedSet = java.util.Collections.unmodifiableSet(allowedSet != null ? allowedSet : java.util.Collections.emptySet());
    }

    public String getId() { return id; }
    public String getModelFile() { return modelFile; }
    public String getLabelsFile() { return labelsFile; }
    public String getLabelsFormat() { return labelsFormat; }
    public String getInputNodeName() { return inputNodeName; }
    public String getOutputNodeName() { return outputNodeName; }
    public int getNumClasses() { return numClasses; }

    /**
     * Returns the set of generic ImageNet top-predictions class labels that are considered
     * "in-scope" for this specialized classifier. Empty means no allowed-set filtering.
     */
    public java.util.Set<String> getAllowedSet() { return allowedSet; }

    @Override
    public String toString() {
        return "ModelProfile{id=" + id
                + ", modelFile=" + modelFile
                + ", labelsFile=" + labelsFile
                + ", numClasses=" + numClasses
                + "}";
    }

    // -----------------------------------------------------------------------
    // Static factories
    // -----------------------------------------------------------------------

    /**
     * Build a profile from metadata JSON asset.
     * Reads: model_file, labels_file, input_name, output_name, num_classes, dataset_profile.
     */
    public static ModelProfile fromMetadata(org.json.JSONObject json) throws JSONException {
        String id = safeString(json, "dataset_profile");
        if (id == null || id.isEmpty()) {
            throw new JSONException("Missing or empty 'dataset_profile' in metadata");
        }

        java.util.Set<String> allowedSet = readAllowedSet(json);

        return new ModelProfile(
                id,
                requiredString(json, "model_file"),
                requiredString(json, "labels_file"),
                LABEL_FORMAT_JSON,
                safeString(json, "input_name", "input"),
                safeString(json, "output_name", "output"),
                json.optInt("num_classes", 0),
                allowedSet
        );
    }

    /**
     * Build a profile for the Phase 5A generic MobileNetV2 reference family.
     */
    public static ModelProfile mobileNetV2(String inputNode, String outputNode) {
        return new ModelProfile(
                "mobilenetv2-imagenet",
                "mobilenetv2.onnx",
                "imagenet_classes.txt",
                LABEL_FORMAT_PLAIN_TEXT,
                inputNode != null ? inputNode : "input",
                outputNode != null ? outputNode : "output",
                0 // generic ImageNet: class count not tracked in config
        );
    }

    /**
     * Load a profile from a metadata JSON asset in the app's assets folder.
     */
    public static ModelProfile fromMetadataAsset(android.content.Context context, String assetPath) throws JSONException, java.io.IOException {
        try (java.io.InputStream is = context.getAssets().open(assetPath);
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return fromMetadata(new JSONObject(sb.toString()));
        }
    }

    /**
     * Metadata-aware helper to load a profile.
     * Checks for a metadata file named {@code <prefix>_model_metadata.json} where
     * prefix is the active model type identifier (e.g. "hymenoptera", "mobilenetv2").
     * Falls back to Phase 5A defaults if no metadata is present.
     *
     * @param context        application or activity context with asset access
     * @param modelPrefix    prefix used for file naming (e.g., "hymenoptera")
     *                       or the literal model filename when no prefix pattern applies
     * @return loaded ModelProfile
     */
    public static ModelProfile load(android.content.Context context, String modelPrefix) throws JSONException, java.io.IOException {
        String metadataAsset = modelPrefix + "_model_metadata.json";
        try {
            return fromMetadataAsset(context, metadataAsset);
        } catch (org.json.JSONException e) {
            // fall-through to generic MobileNetV2 default when metadata is not found
        } catch (java.io.FileNotFoundException e) {
            // metadata asset does not exist for this prefix → use generic defaults
        }
        return mobileNetV2("input", "output");
    }

    // -----------------------------------------------------------------------
    // JSON helpers
    // -----------------------------------------------------------------------

    private static JSONObject requiredObject(JSONObject json, String key) throws JSONException {
        Object val = json.opt(key);
        if (val == null || !(val instanceof JSONObject)) {
            throw new JSONException("Missing or invalid required field: " + key);
        }
        return (JSONObject) val;
    }

    private static double safeDouble(JSONObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        Object val = json.opt(key);
        return (val instanceof Number) ? ((Number) val).doubleValue() : fallback;
    }

    private static long safeLong(JSONObject json, String key, long fallback) {
        if (!json.has(key)) return fallback;
        Object val = json.opt(key);
        return (val instanceof Number) ? ((Number) val).longValue() : fallback;
    }

    // -----------------------------------------------------------------------
    // Allowed-set support for Phase 5D routing
    // -----------------------------------------------------------------------

    /**
     * Read the allowed set from metadata's "class_ids" field.
     * These represent ImageNet top-prediction labels considered in-scope for this specialized model.
     */
    private static java.util.Set<String> readAllowedSet(JSONObject json) throws JSONException {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        JSONArray idArray = json.optJSONArray("class_ids");
        if (idArray != null) {
            for (int i = 0; i < idArray.length(); i++) {
                String id = idArray.getString(i);
                if (id != null && !id.isEmpty()) {
                    allowed.add(id);
                }
            }
        }
        return allowed;
    }

    // -----------------------------------------------------------------------
    // JSON helpers
    // -----------------------------------------------------------------------

    private static String requiredString(JSONObject json, String key) throws JSONException {
        Object val = json.opt(key);
        if (val == null) {
            throw new JSONException("Missing required field: " + key);
        }
        return val.toString();
    }

    private static String safeString(JSONObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        Object val = json.opt(key);
        return (val != null && val.toString().length() > 0) ? val.toString() : fallback;
    }

    private static String safeString(JSONObject json, String key) {
        return json.optString(key, null);
    }
}
