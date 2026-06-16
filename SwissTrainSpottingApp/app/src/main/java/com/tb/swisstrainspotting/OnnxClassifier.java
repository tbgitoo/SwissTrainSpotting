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
    private boolean closed = false;

    /**
     * Create a classifier that loads the Phase 5A model and labels from assets.
     *
     * @param context application or activity context with asset access
     * @throws IOException if model or labels cannot be read from assets
     */
    public OnnxClassifier(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        environment = OrtEnvironment.getEnvironment();
        byte[] modelBytes = readAssetBytes(context, ModelConfig.MODEL_FILE);

        OrtSession createdSession = null;
        try {
            createdSession = environment.createSession(modelBytes, new OrtSession.SessionOptions());
            validateNodeNames(createdSession);
            labels = LabelLoader.loadDefaultLabels(context);
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
     *
     * @param inputData planar NCHW float[] of length {@link ModelConfig#INPUT_ELEMENT_COUNT}
     * @return classification result with label, index, and confidence
     * @throws IllegalStateException if this instance has been closed or inference fails
     * @throws IllegalArgumentException if input is null or wrong length
     */
    public ClassificationResult classify(float[] inputData) {
        if (closed) {
            throw new IllegalStateException("Classifier is closed");
        }
        if (inputData == null) {
            throw new IllegalArgumentException("Input tensor must not be null");
        }
        if (inputData.length != ModelConfig.INPUT_ELEMENT_COUNT) {
            throw new IllegalArgumentException(
                    "Input tensor must have length " + ModelConfig.INPUT_ELEMENT_COUNT
                            + ", got " + inputData.length
            );
        }

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(inputData), ModelConfig.INPUT_SHAPE);
             OrtSession.Result result = session.run(
                     Collections.singletonMap(ModelConfig.INPUT_NODE_NAME, inputTensor))) {
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

    private static void validateNodeNames(OrtSession session) throws OrtException {
        Set<String> inputNames = session.getInputNames();
        Set<String> outputNames = session.getOutputNames();

        if (!inputNames.contains(ModelConfig.INPUT_NODE_NAME)) {
            throw new IllegalStateException(
                    "Expected input node '" + ModelConfig.INPUT_NODE_NAME
                            + "' but model inputs are: " + inputNames
            );
        }
        if (!outputNames.contains(ModelConfig.OUTPUT_NODE_NAME)) {
            throw new IllegalStateException(
                    "Expected output node '" + ModelConfig.OUTPUT_NODE_NAME
                            + "' but model outputs are: " + outputNames
            );
        }
    }

    private static OnnxValue getRequiredOutput(OrtSession.Result result) throws OrtException {
        OnnxValue outputValue = result.get(ModelConfig.OUTPUT_NODE_NAME).orElse(null);
        if (outputValue == null) {
            throw new IllegalStateException(
                    "Missing output tensor for node '" + ModelConfig.OUTPUT_NODE_NAME + "'"
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
