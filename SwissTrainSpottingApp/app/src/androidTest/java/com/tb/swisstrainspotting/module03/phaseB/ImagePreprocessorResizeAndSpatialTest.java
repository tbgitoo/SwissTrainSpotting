package com.tb.swisstrainspotting.module03.phaseB;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import com.tb.swisstrainspotting.imageprocess.ImagePreprocessor;

@RunWith(AndroidJUnit4.class)
public class ImagePreprocessorResizeAndSpatialTest {

    // Helper: compute the expected normalized value for a given channel value [0,255]
    private static float normalize(int rawChannel, int channelIndex) {
        return ((rawChannel / 255.0f) - MEAN[channelIndex]) / STD[channelIndex];
    }

    // Helper: compute flat NCHW index for channel c at (y, x)
    private static int index(int c, int y, int x) {
        int width = ImagePreprocessor.INPUT_WIDTH;
        int plane = ImagePreprocessor.INPUT_WIDTH * ImagePreprocessor.INPUT_HEIGHT;
        return c * plane + y * width + x;
    }

    // Build a uniform ARGB bitmap with the given color in every pixel
    private static Bitmap createUniformBitmap(int r, int g, int b, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = -0x1000000 | (r << 16) | (g << 8) | b; // set ARGB: A=255, R, G, B
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private static final float EPSILON = 1e-4f;



    // ========================================================================
    // Module 3B — additional secondary validation tests (Prompt 3)
    // ========================================================================

    /**
     * Test 1: resize_stretchesNon224UniformImage
     *
     * Create a 100×50 synthetic ARGB_8888 bitmap, fill every pixel with
     * uniform RGB(128,128,128), run preprocess(), and assert that the full
     * 150528-element output tensor equals the ImageNet-normalized value for
     * 128/255f in all R, G, and B planes.
     *
     * Proves:
     *   • resize to 224×224 occurred (output covers full plane)
     *   • stretch-resize of uniform image yields uniform tensor
     *   • no gaps or uninitialized slots in output
     */
    @Test
    public void resize_stretchesNon224UniformImage() {
        int w = 100;
        int h = 50;
        Bitmap bitmap = createUniformBitmap(128, 128, 128, w, h);
        float[] result = ImagePreprocessor.preprocess(bitmap);

        // --- length -----------------------------------------------
        assertEquals("tensor length must be 3 × "
                        + ImagePreprocessor.INPUT_WIDTH
                        + " × "
                        + ImagePreprocessor.INPUT_HEIGHT
                , ImagePreprocessor.TENSOR_LENGTH, result.length);

        float expectedR = normalize(128, 0); // 128/255 − mean_R / std_R
        float expectedG = normalize(128, 1);
        float expectedB = normalize(128, 2);

        int planeSize = ImagePreprocessor.INPUT_WIDTH * ImagePreprocessor.INPUT_HEIGHT; // 50176
        int rPlaneStart = index(0, 0, 0);                         // 0
        int gPlaneStart = index(1, 0, 0);                         // 50176
        int bPlaneStart = index(2, 0, 0);                         // 100352

        // --- every element of every plane -------------------------
        for (int i = 0; i < planeSize; i++) {
            assertEquals("R[" + i + "]", expectedR, result[rPlaneStart + i], EPSILON);
            assertEquals("G[" + i + "]", expectedG, result[gPlaneStart + i], EPSILON);
            assertEquals("B[" + i + "]", expectedB, result[bPlaneStart + i], EPSILON);
        }

        bitmap.recycle();
    }

    /**
     * Test 2: quadrantSpatialSanity
     *
     * Create a 224×224 synthetic ARGB_8888 bitmap where the top-left quadrant
     * (x=0…111, y=0…111) is pure red RGB(255,0,0) and all remaining pixels
     * are black RGB(0,0,0).  After preprocess() assert that R-plane samples
     * from the two regions differ beyond epsilon, confirming spatial structure
     * survived sampling and the planar NCHW layout is not scrambled.
     */
    @Test
    public void quadrantSpatialSanity() {
        int w = ImagePreprocessor.INPUT_WIDTH;   // 224
        int h = ImagePreprocessor.INPUT_HEIGHT;  // 224

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x < (w / 2) && y < (h / 2)) { // top-left quadrant
                    pixels[y * w + x] = 0xFF_FF_00_00; // red
                } else {
                    pixels[y * w + x] = 0xFF_00_00_00; // black
                }
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);

        float[] result = ImagePreprocessor.preprocess(bitmap);

        float expectedRedR   = normalize(255, 0);
        float expectedBlackR = normalize(0, 0);
        float expectedBlankG = normalize(0, 1);
        float expectedBlankB = normalize(0, 2);

        // ---- top-left quadrant (x=50, y=50) --------------------
        int tlRIdx  = index(0, 50, 50);
        int tlGIdx  = index(1, 50, 50);
        int tlBIdx  = index(2, 50, 50);

        assertEquals("R at TL(50,50)", expectedRedR,   result[tlRIdx], EPSILON);
        assertEquals("G at TL(50,50)", expectedBlankG, result[tlGIdx], EPSILON);
        assertEquals("B at TL(50,50)", expectedBlankB, result[tlBIdx], EPSILON);

        // ---- bottom-right quadrant (x=200, y=200) -----------
        int brRIdx  = index(0, 200, 200);
        int brGIdx  = index(1, 200, 200);
        int brBIdx  = index(2, 200, 200);

        assertEquals("R at BR(200,200)", expectedBlackR, result[brRIdx], EPSILON);
        assertEquals("G at BR(200,200)", expectedBlankG, result[brGIdx], EPSILON);
        assertEquals("B at BR(200,200)", expectedBlankB, result[brBIdx], EPSILON);

        // ---- cross-region difference (the core sanity check) ---
        assertNotEquals(
                "R-plane top-left must differ from bottom-right",
                result[tlRIdx], result[brRIdx], EPSILON);

        bitmap.recycle();

    }
}
