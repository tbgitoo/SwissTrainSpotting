package com.tb.swisstrainspotting.imageprocess;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.tb.swisstrainspotting.ImageClassificationActivity;

/**
 * Converts an upright Bitmap into the planar NCHW {@code float[]} tensor expected by ONNX models.
 *
 * <p>Pipeline: stretch-resize to 224×224 → ARGB int buffer → per-channel normalization using
 * ImageNet mean/std → write to NCHW layout (channel-major, spatial-linear within each plane).
 *
 * <p><b>Packed-to-planar contract:</b> reads packed ARGB pixels via {@code Bitmap.getPixels()},
 * converts RGB to [0,1] range, applies {@code (x − mean) / std} per channel, and stores in an
 * output array of length 150&nbsp;528 where index  C×224×224 + y×224 + x holds the value for
 * channel C at pixel (y, x). This matches the Python preprocessing order exactly.
 *
 * <p>Caller is responsible for supplying an upright Bitmap (EXIF rotation corrected before
 * invocation, typically by {@link ImageClassificationActivity}). Returns a fresh array — the
 * caller owns it and must close any ONNX tensors built from it.
 */
public final class ImagePreprocessor {

    public static final int INPUT_WIDTH = 224;
    public static final int INPUT_HEIGHT = 224;
    public static final int CHANNELS = 3;
    public static final int TENSOR_LENGTH = CHANNELS * INPUT_WIDTH * INPUT_HEIGHT;

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private ImagePreprocessor() {}

    public static float[] preprocess(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            throw new IllegalArgumentException("Invalid Bitmap input");
        }

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true);

        int[] pixelBuffer = new int[INPUT_WIDTH * INPUT_HEIGHT];
        resized.getPixels(pixelBuffer, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT);

        float[] tensor = new float[TENSOR_LENGTH];
        int planeSize = INPUT_WIDTH * INPUT_HEIGHT;

        for (int y = 0; y < INPUT_HEIGHT; y++) {
            for (int x = 0; x < INPUT_WIDTH; x++) {
                int idx = y * INPUT_WIDTH + x;
                int argb = pixelBuffer[idx];

                float r = Color.red(argb) / 255.0f;
                float g = Color.green(argb) / 255.0f;
                float b = Color.blue(argb) / 255.0f;

                tensor[idx] = (r - MEAN[0]) / STD[0];
                tensor[planeSize + idx] = (g - MEAN[1]) / STD[1];
                tensor[2 * planeSize + idx] = (b - MEAN[2]) / STD[2];
            }
        }

        return tensor;
    }
}