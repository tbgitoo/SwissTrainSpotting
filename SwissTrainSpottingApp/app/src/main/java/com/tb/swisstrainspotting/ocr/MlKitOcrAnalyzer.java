package com.tb.swisstrainspotting.ocr;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * On-device OCR powered by ML Kit Text Recognition, implementing {@link OcrAnalyzer}.
 *
 * <p>Lifecycle: lazy-creates one {@link TextRecognizer} on first call, reuses it for all subsequent invocations.
 * Callers must invoke {@link #close()} when the analyzer is discarded (e.g., activity teardown) to release native resources.
 * Calling methods after close throws {@link IllegalStateException}.
 *
 * <p>Bitmap contract: accepts the upright display Bitmap (same orientation as shown to the user).
 * If either dimension exceeds 2048px, a scaled copy is created for OCR — the original is never mutated or replaced.
 * The scaled bitmap is recycled in a finally block; callers own the original.
 *
 * <p>Failure guarantee: any exception during recognition returns {@link OcrResult#empty()} and logs at WARN level.
 * This ensures OCR never crashes the app or blocks the classification critical path.
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
