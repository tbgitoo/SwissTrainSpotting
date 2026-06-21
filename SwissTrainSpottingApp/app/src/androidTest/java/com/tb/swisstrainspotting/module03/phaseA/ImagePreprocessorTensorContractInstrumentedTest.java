package com.tb.swisstrainspotting.module03.phaseA;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import com.tb.swisstrainspotting.imageprocess.ImagePreprocessor;

/**
 * Phase A: core tensor contract tests for {@link ImagePreprocessor}.
 */
@RunWith(AndroidJUnit4.class)
public class ImagePreprocessorTensorContractInstrumentedTest {

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private static final float EPSILON = 1e-4f;

    private static float normalize(int rawChannel, int channelIndex) {
        return ((rawChannel / 255.0f) - MEAN[channelIndex]) / STD[channelIndex];
    }

    private static int index(int c, int y, int x) {
        int width = ImagePreprocessor.INPUT_WIDTH;
        int plane = ImagePreprocessor.INPUT_WIDTH * ImagePreprocessor.INPUT_HEIGHT;
        return c * plane + y * width + x;
    }

    private static Bitmap createUniformBitmap(int r, int g, int b, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = -0x1000000 | (r << 16) | (g << 8) | b;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    // Test: tensorLength_is150528
    @Test
    public void tensorLength_is150528() {
        Bitmap bitmap = createUniformBitmap(128, 64, 32,
                ImagePreprocessor.INPUT_WIDTH,
                ImagePreprocessor.INPUT_HEIGHT);
        float[] result = ImagePreprocessor.preprocess(bitmap);
        assertEquals("tensor length must be 3 × "
                        + ImagePreprocessor.INPUT_WIDTH
                        + " × "
                        + ImagePreprocessor.INPUT_HEIGHT
                , ImagePreprocessor.TENSOR_LENGTH, result.length);
    }

    // Test: uniformRgb_1_2_3_producesExpectedChannels
    @Test
    public void uniformRgb_1_2_3_producesExpectedChannels() {
        Bitmap bitmap = createUniformBitmap(1, 2, 3, 224, 224);
        float[] result = ImagePreprocessor.preprocess(bitmap);

        float expectedR = normalize(1, 0);
        float expectedG = normalize(2, 1);
        float expectedB = normalize(3, 2);

        int rPlaneStart = index(0, 0, 0);    // 0
        int gPlaneStart = index(1, 0, 0);    // 50176
        int bPlaneStart = index(2, 0, 0);    // 100352

        assertEquals("R plane at start", expectedR, result[rPlaneStart], EPSILON);
        assertEquals("R plane at end",   expectedR, result[rPlaneStart + 50175], EPSILON);

        assertEquals("G plane at start", expectedG, result[gPlaneStart], EPSILON);
        assertEquals("G plane at end",   expectedG, result[gPlaneStart + 50175], EPSILON);

        assertEquals("B plane at start", expectedB, result[bPlaneStart], EPSILON);
        assertEquals("B plane at end",   expectedB, result[bPlaneStart + 50175], EPSILON);
    }

    // Test: singlePixel_mapsToCorrectNchwIndex
    @Test
    public void singlePixel_mapsToCorrectNchwIndex() {
        int w = 224;
        int h = 224;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        // Fill with black (R=0, G=0, B=0)
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF000000; // A=255, R=0, G=0, B=0
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);

        // Set single red pixel at (x=20, y=10)
        pixels[10 * w + 20] = 0xFF_FF_00_00; // R=255, G=0, B=0
        bitmap.setPixels(new int[]{pixels[10 * w + 20]}, 0, 1, 20, 10, 1, 1);

        float[] result = ImagePreprocessor.preprocess(bitmap);

        int rIndex = index(0, 10, 20); // channel=R
        int gIndex = index(1, 10, 20); // channel=G
        int bIndex = index(2, 10, 20); // channel=B

        float expectedR = normalize(255, 0);
        float expectedG = normalize(0, 1);
        float expectedB = normalize(0, 2);

        assertEquals("Red at (x=20, y=10)", expectedR, result[rIndex], EPSILON);
        assertEquals("Green at (x=20, y=10)", expectedG, result[gIndex], EPSILON);
        assertEquals("Blue at (x=20, y=10)", expectedB, result[bIndex], EPSILON);

        // Green channel at black (0,0) should equal normalize(0, 1) which equals expectedG
        int gAtBlack = index(1, 0, 0);
        assertEquals("Green at black pixel (0,0)", expectedG, result[gAtBlack], EPSILON);

        // Far-away location must differ from the red pixel in the R channel
        int rFar = index(0, 200, 200);
        assertNotEquals("R value far away should differ from red pixel",
                expectedR, result[rFar], EPSILON);
    }

    // Test: nullBitmap_throws
    @Test(expected = IllegalArgumentException.class)
    public void nullBitmap_throws() {
        ImagePreprocessor.preprocess(null);
    }
}
