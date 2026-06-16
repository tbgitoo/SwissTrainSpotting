package com.tb.swisstrainspotting;

/**
 * Phase 5A configuration constants for the reference MobileNetV2 model.
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
