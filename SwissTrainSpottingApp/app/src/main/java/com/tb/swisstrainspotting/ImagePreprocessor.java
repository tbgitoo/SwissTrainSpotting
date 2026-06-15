package com.tb.swisstrainspotting;

import android.graphics.Bitmap;
import android.graphics.Color;

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