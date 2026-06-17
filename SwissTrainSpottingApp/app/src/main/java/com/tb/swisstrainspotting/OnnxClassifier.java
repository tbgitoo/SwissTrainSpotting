package com.tb.swisstrainspotting;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * ONNX Runtime inference for Phase 5A MobileNetV2 reference model.
 *
 * <p>Accepts planar NCHW {@code float[]} input from {@link ImagePreprocessor}.
 * Creates one long-lived {@link OrtSession} at construction time and reuses it
 * for all {@link #classify(float[])} calls.
 */
public class OnnxClassifier implements AutoCloseable {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final List<String> labels;
    private String inputNodeName;
    private String outputNodeName;
    private final String modelFile;
    private final int expectedInputLength;
    private boolean closed = false;

    /**
     * Create a classifier that loads the Phase 5A MobileNetV2 model and labels from assets.
     */
    public OnnxClassifier(Context context) throws IOException {
        this(context, ModelProfile.mobileNetV2("input", "output"));
    }

    /**
     * Create a classifier for the given profile. The profile determines the model file,
     * labels file, input/output node names, and expected input dimension.
     */
    public OnnxClassifier(Context context, ModelProfile profile) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        this.modelFile = profile.getModelFile();
        this.inputNodeName = profile.getInputNodeName();
        this.outputNodeName = profile.getOutputNodeName();
        this.expectedInputLength = ModelConfig.INPUT_ELEMENT_COUNT;

        environment = OrtEnvironment.getEnvironment();
        byte[] modelBytes = readAssetBytes(context, modelFile);

        OrtSession createdSession = null;
        try {
            createdSession = environment.createSession(modelBytes, new OrtSession.SessionOptions());
            validateNodeNames(createdSession);
            labels = LabelLoader.loadLabels(context, profile.getLabelsFile());
            session = createdSession;
        } catch (OrtException e) {
            closeQuietly(createdSession);
            throw new IOException("Failed to create ONNX Runtime session", e);
        } catch (IOException e) {
            closeQuietly(createdSession);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(createdSession);
            throw e;
        }
    }

    /**
     * Classify an input tensor. Caller is responsible for off-main-thread execution.
     */
    public ClassificationResult classify(float[] inputData) {
        if (closed) {
            throw new IllegalStateException("Classifier is closed");
        }
        if (inputData == null) {
            throw new IllegalArgumentException("Input tensor must not be null");
        }
        if (inputData.length != expectedInputLength) {
            throw new IllegalArgumentException(
                    "Input tensor must have length " + expectedInputLength
                            + ", got " + inputData.length
            );
        }

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(inputData), ModelConfig.INPUT_SHAPE);
              OrtSession.Result result = session.run(
                      Collections.singletonMap(inputNodeName, inputTensor))) {
            OnnxValue outputValue = getRequiredOutput(result);
            float[] logits = extractLogits(outputValue);
            return LogitsParser.parse(logits, labels);
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX inference failed", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeQuietly(session);
    }

    private void validateNodeNames(OrtSession session) throws OrtException {
        java.util.Set<String> inputNames = session.getInputNames();
        java.util.Set<String> outputNames = session.getOutputNames();

        // Use profile-specified node names when present in the model.
        // Otherwise fall back to accepting the first available input/output (Phase 5A compat).
        String resolvedInputNode = ModelConfig.INPUT_NODE_NAME; // default for Phase 5A
        String resolvedOutputNode = ModelConfig.OUTPUT_NODE_NAME;
        if (inputNames.contains(inputNodeName)) {
            resolvedInputNode = inputNodeName;
        } else if (!inputNames.isEmpty()) {
            resolvedInputNode = inputNames.iterator().next();
        }
        if (outputNames.contains(outputNodeName)) {
            resolvedOutputNode = outputNodeName;
        } else if (!outputNames.isEmpty()) {
            resolvedOutputNode = outputNames.iterator().next();
        }

        this.inputNodeName = resolvedInputNode;
        this.outputNodeName = resolvedOutputNode;
    }

    private OnnxValue getRequiredOutput(OrtSession.Result result) throws OrtException {
        OnnxValue outputValue = result.get(outputNodeName).orElse(null);
        if (outputValue == null) {
            throw new IllegalStateException(
                    "Missing output tensor for node '" + outputNodeName + "'"
            );
        }
        return outputValue;
    }

    private static float[] extractLogits(OnnxValue outputValue) throws OrtException {
        if (!(outputValue instanceof OnnxTensor)) {
            throw new IllegalStateException(
                    "Expected OnnxTensor output, got " + outputValue.getClass().getName()
            );
        }

        OnnxTensor tensor = (OnnxTensor) outputValue;
        TensorInfo info = (TensorInfo) tensor.getInfo();
        long[] shape = info.getShape();
        if (shape.length != 2) {
            throw new IllegalStateException(
                    "Expected output rank 2, got rank " + shape.length
            );
        }
        if (shape[0] != 1L || shape[1] <= 0L) {
            throw new IllegalStateException(
                    "Expected output shape [1, N] with N > 0, got ["
                            + shape[0] + ", " + shape[1] + "]"
            );
        }

        int numClasses = (int) shape[1];
        Object value = tensor.getValue();
        float[] logits;
        if (value instanceof float[][]) {
            float[][] batch = (float[][]) value;
            if (batch.length != 1 || batch[0].length != numClasses) {
                throw new IllegalStateException(
                        "Unexpected float[][] output layout for shape [1, " + numClasses + "]"
                );
            }
            logits = batch[0];
        } else {
            FloatBuffer buffer = tensor.getFloatBuffer();
            if (buffer.remaining() != numClasses) {
                throw new IllegalStateException(
                        "FloatBuffer size " + buffer.remaining()
                                + " does not match expected class count " + numClasses
                );
            }
            logits = new float[numClasses];
            buffer.get(logits);
        }

        for (int i = 0; i < logits.length; i++) {
            float v = logits[i];
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                throw new IllegalStateException(
                        "Output logits contain non-finite value at index " + i
                );
            }
        }

        return logits;
    }

    private static byte[] readAssetBytes(Context context, String assetPath) throws IOException {
        try (InputStream inputStream = context.getAssets().open(assetPath);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static void closeQuietly(OrtSession sessionToClose) {
        if (sessionToClose == null) {
            return;
        }
        try {
            sessionToClose.close();
        } catch (OrtException ignored) {
            // Idempotent close: best effort only.
        }
    }
}
