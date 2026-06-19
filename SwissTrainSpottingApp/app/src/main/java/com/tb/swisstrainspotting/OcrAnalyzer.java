package com.tb.swisstrainspotting;

import android.graphics.Bitmap;

/**
 * Seam for OCR text recognition.
 *
 * <p>Implementations may use any on-device OCR engine. Consumers call
 * {@link #recognize(Bitmap)} and receive an {@link OcrResult}. When done, callers should
 * invoke {@link #close()} to release resources (e.g., ML Kit {@code TextRecognizer}).
 */
public interface OcrAnalyzer extends AutoCloseable {

    /**
     * Run OCR on the provided upright Bitmap and return normalized text.
     *
     * @param bitmap an upright display Bitmap (not tensor-ready). Must not be mutated.
     * @return non-null {@link OcrResult} with recognized text, or empty if no usable text found.
     */
    OcrResult recognize(Bitmap bitmap);

    @Override
    void close();
}
