package com.tb.swisstrainspotting;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * On-device OCR backed by ML Kit Text Recognition (v17+).
 *
 * <p>Lazy-creates one {@link TextRecognizer} on first use and reuses it for all calls.
 * Callers must invoke {@link #close()} when the analyzer is no longer needed to release
 * the underlying native resources.
 *
 * <h3>Bitmap safety</h3>
 * Accepts an upright display Bitmap (same orientation as shown to the user).
 * If the bitmap exceeds 2048 pixels in width or height, a downsampled copy is created
 * for OCR-only purposes; the original Bitmap is never mutated or replaced.
 */
public class MlKitOcrAnalyzer implements OcrAnalyzer {

    private static final String TAG = "MlKitOcrAnalyzer";
    private static final int MAX_OCR_DIMENSION_PX = 2048;

    private TextRecognizer recognizer;
    private boolean closed;

    @Override
    public OcrResult recognize(Bitmap bitmap) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap must not be null");
        }
        if (closed) {
            throw new IllegalStateException("MlKitOcrAnalyzer is closed");
        }

        // Scale the bitmap for OCR safety while never mutating the original.
        Bitmap ocrBitmap = prepareOcrBitmap(bitmap);

        Text visionText;
        try {
            InputImage image = InputImage.fromBitmap(ocrBitmap, 0);
            // process() returns an async Task; block on ocrExecutor thread until complete.
            visionText = Tasks.await(getRecognizer().process(image));
            if (visionText == null) {
                return OcrResult.empty();
            }
            String normalized = OcrTextNormalizer.normalize(visionText);
            return normalized.isEmpty() ? OcrResult.empty() : new OcrResult(normalized);
        } catch (Exception e) {
            Log.w(TAG, "ML Kit OCR failed", e);
            return OcrResult.empty();
        } finally {
            // The scaled bitmap was created for ML Kit; recycle it here.
            if (ocrBitmap != bitmap && !ocrBitmap.isRecycled()) {
                ocrBitmap.recycle();
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
    }

    /**
     * Lazy-initialize and reuse the shared TextRecognizer.
     */
    private synchronized TextRecognizer getRecognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        return recognizer;
    }

    /**
     * If the bitmap exceeds {@link #MAX_OCR_DIMENSION_PX} in either dimension,
     * create a scaled-down copy for OCR processing. The original Bitmap is never mutated.
     */
    private static Bitmap prepareOcrBitmap(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= MAX_OCR_DIMENSION_PX && height <= MAX_OCR_DIMENSION_PX) {
            return original; // No scaling needed.
        }

        float scale = Math.min((float) MAX_OCR_DIMENSION_PX / width,
                               (float) MAX_OCR_DIMENSION_PX / height);

        int scaledWidth = Math.max(1, (int) (width * scale));
        int scaledHeight = Math.max(1, (int) (height * scale));

        Bitmap scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, false);
        // The caller owns the original bitmap; we own this scaled copy.
        return scaled;
    }
}
