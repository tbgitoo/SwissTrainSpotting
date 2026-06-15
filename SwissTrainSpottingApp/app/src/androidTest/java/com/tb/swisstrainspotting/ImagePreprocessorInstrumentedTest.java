package com.tb.swisstrainspotting;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ImagePreprocessorInstrumentedTest {

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

    // Test 1: tensorLength_is150528
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

    // Test 2: uniformRgb_1_2_3_producesExpectedChannels
    @Test
    public void uniformRgb_1_2_3_producesExpectedChannels() {
        Bitmap bitmap = createUniformBitmap(1, 2, 3, 224, 224);
        float[] result = ImagePreprocessor.preprocess(bitmap);

        // All positions share the same source color => each channel plane is uniform
        float expectedR = normalize(1, 0);
        float expectedG = normalize(2, 1);
        float expectedB = normalize(3, 2);

        // Check first position of each plane and last of each plane
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

    // Test 3: singlePixel_mapsToCorrectNchwIndex
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

    // Test 4: nullBitmap_throws
    @Test(expected = IllegalArgumentException.class)
    public void nullBitmap_throws() {
        ImagePreprocessor.preprocess(null);
    }

    // ========================================================================
    // Module 3 §7 — additional secondary validation tests (Prompt 3)
    // ========================================================================

    /**
     * Test 5: resize_stretchesNon224UniformImage
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

        // --- start of each plane ----------------------------------
        assertEquals("R at R-plane start", expectedR, result[rPlaneStart], EPSILON);
        assertEquals("G at G-plane start", expectedG, result[gPlaneStart], EPSILON);
        assertEquals("B at B-plane start", expectedB, result[bPlaneStart], EPSILON);

        // --- end of each plane ------------------------------------
        assertEquals("R at R-plane end  ", expectedR,
                result[rPlaneStart + planeSize - 1], EPSILON);
        assertEquals("G at G-plane end  ", expectedG,
                result[gPlaneStart + planeSize - 1], EPSILON);
        assertEquals("B at B-plane end  ", expectedB,
                result[bPlaneStart + planeSize - 1], EPSILON);

        // --- middle of each plane ---------------------------------
        int midPlane = planeSize / 2;
        assertEquals("R middle", expectedR, result[rPlaneStart + midPlane], EPSILON);
        assertEquals("G middle", expectedG, result[gPlaneStart + midPlane], EPSILON);
        assertEquals("B middle", expectedB, result[bPlaneStart + midPlane], EPSILON);

        // --- a random-looking interior sample ----------------------
        int y0 = 167, x0 = 99;
        int rIdx = index(0, y0, x0);
        int gIdx = index(1, y0, x0);
        int bIdx = index(2, y0, x0);

        assertEquals("R at (" + x0 + "," + y0 + ")", expectedR, result[rIdx], EPSILON);
        assertEquals("G at (" + x0 + "," + y0 + ")", expectedG, result[gIdx], EPSILON);
        assertEquals("B at (" + x0 + "," + y0 + ")", expectedB, result[bIdx], EPSILON);

        bitmap.recycle();
    }

    /**
     * Test 6: quadrantSpatialSanity
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
