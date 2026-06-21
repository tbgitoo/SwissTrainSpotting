package com.tb.swisstrainspotting.onnx;

import com.tb.swisstrainspotting.ui.ProfileConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Immutable description of a model family (ONNX artifact bundle) consumed at runtime.
 *
 * <p>Each profile maps to one set of Python-exported assets — an ONNX model, a label file,
 * and {@code _model_metadata.json} metadata produced by the {@code model/} pipeline.
 * Android reads these assets from the app's {@code assets/} folder at start-up or lazily;
 * this class does not mutate its source JSON.
 *
 * <h3>Asset lifecycle</h3>
 * The exported metadata schema contains: {@code model_file}, {@code labels_file},
 * {@code input_name}, {@code output_name}, {@code num_classes}, and
 * {@code compatible_generic_labels}. Android uses these values verbatim — node names,
 * label path, and class count are <em>not</em> re-hardcoded on the Java side.
 * A factory method ({@link #load android.content.Context, String}) resolves the metadata file
 * by convention: {@code <prefix>_model_metadata.json}, falling back to hardcoded MobileNetV2
 * defaults when no metadata file exists.
 *
 * <h3>Label formats</h3>
 * {@value #LABEL_FORMAT_PLAIN_TEXT} labels are one-per-line text files (Phase 5A ImageNet).
 * {@value #LABEL_FORMAT_JSON} labels follow the exported schema with {@code classes[].index}
 * mapping — loaded by {@link LabelLoader} which dispatches on file extension.
 * Two model families may have entirely separate label formats and this class is agnostic to
 * that choice beyond passing it through.
 *
 * <h3>Allowed set vs profile config</h3>
 * {@link #getAllowedSet()} contains ImageNet / MobileNetV2 class names — i.e., generic-labels
 * in scope for routing to this specialized classifier. It is orthogonal to
 * {@link ProfileConfig}, which carries the human-facing domain display name and UI text prefixes.
 * Both live on Android; the allowed set originates from Python metadata's
 * {@code compatible_generic_labels} field, not from Java code.
 *
 * <h3>Immutable config, not runtime data</h3>
 * This class is configuration — it never changes after construction and is safe to share
 * across threads. It describes <em>which</em> model to load and <em>how</em>, whereas
 * {@link ClassificationResult} describes <em>what</em> was predicted at runtime.
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
     * Assemble a profile from already-parsed {@code _model_metadata.json} content.
     *
     * <p>Required field: {@code dataset_profile} (the unique profile identifier).
     * Optional fields with defaults: {@code input_name} → "input", {@code output_name} → "output".
     * The label format is always set to {@value #LABEL_FORMAT_JSON} for exported profiles.
     *
     * @throws JSONException if {@code dataset_profile} is missing or non-string
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
     * Read the allowed set from metadata/config.
     *
     * <p>Phase 5D uses {@code compatible_generic_labels} to define which generic MobileNetV2
     * top-prediction labels are considered in-scope for this specialized model. This is distinct
     * from the specialized model's own {@code class_ids}.
     */
    private static java.util.Set<String> readAllowedSet(JSONObject json) throws JSONException {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        JSONArray idArray = json.optJSONArray("compatible_generic_labels");
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

    /**
     * Build a profile for the Phase 5A generic MobileNetV2 reference family with hardcoded labels.
     *
     * <p>This path bypasses metadata entirely — model, labels, and I/O node names use constants
     * from the original ImageNet baseline. Use only when no exported profile metadata exists
     * (fallback in {@link #load(android.content.Context, String)}).
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
     *
     * <p>Reads the full JSON at once via UTF-8, delegates to {@link #fromMetadata(JSONObject)}.
     * Throws through whatever {@code JSONException} occurs during parsing — no restructuring.
     *
     * @param context    application or activity context with asset access
     * @param assetPath  path relative to {@code assets/} (e.g. {@code "hymenoptera_model_metadata.json"})
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
     * Load a profile by convention: resolve {@code <prefix>_model_metadata.json} from assets,
     * parse it to a {@link ModelProfile}, or fall back to hardcoded MobileNetV2 defaults
     * when the metadata file is absent.
     *
     * <p>This is the standard entry point for production use. Callers pass a profile identifier
     * such as {@code "hymenoptera"} or {@code "swiss_trains"}. If no exported metadata exists,
     * the method silently returns the ImageNet generic profile — callers should not assume the
     * returned model is always specialized.
     *
     * @param context      application or activity context with asset access
     * @param modelPrefix  e.g. {@code "hymenoptera"} — appended with {"_model_metadata.json"} to form the asset path
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
    // JSON helpers (private)
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
