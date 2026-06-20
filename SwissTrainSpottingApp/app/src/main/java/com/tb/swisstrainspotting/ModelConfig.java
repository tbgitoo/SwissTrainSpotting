package com.tb.swisstrainspotting;

/**
 * Constants for the Phase 5A generic MobileNetV2 reference model only.
 *
 * <p>These values apply solely to the ImageNet-supplied reference assets (mobilenetv2.onnx,
 * imagenet_classes.txt) that validate the inference pipeline before any specialized model is loaded.
 * They are the hardcoded fallback in {@link ModelProfile#load(android.content.Context, String)} and
 * serve as the baseline against which exported profiles (via {@code _model_metadata.json}) are compared.
 *
 * <p>From Phase 5B onward, new profiles should not add parallel constants here. Instead, they drive
 * these values from their own metadata assets so that switching models requires only config changes.
 */
public final class ModelConfig {

    /** Expected input tensor shape: [1, 3, 224, 224] */
    public static final long[] INPUT_SHAPE = new long[]{
            1L, 3L,
            ImagePreprocessor.INPUT_WIDTH,
            ImagePreprocessor.INPUT_HEIGHT};

    /** Expected input tensor element count. */
    public static final int INPUT_ELEMENT_COUNT = ImagePreprocessor.TENSOR_LENGTH; // 150528

    /** ONNX model file in assets (Phase 5A). */
    public static final String MODEL_FILE = "mobilenetv2.onnx";

    /** Labels file in assets (Phase 5A — 1000 ImageNet classes). */
    public static final String LABELS_FILE = "imagenet_classes.txt";

    /** Expected input node name (ONNX Model Zoo mobilenetv2-10). */
    public static final String INPUT_NODE_NAME = "input";

    /** Expected output node name (ONNX Model Zoo mobilenetv2-10). */
    public static final String OUTPUT_NODE_NAME = "output";

    private ModelConfig() {}
}
