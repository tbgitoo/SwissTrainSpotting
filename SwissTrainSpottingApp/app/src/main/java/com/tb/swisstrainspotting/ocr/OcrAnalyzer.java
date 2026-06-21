package com.tb.swisstrainspotting.ocr;

import android.graphics.Bitmap;

/**
 * Abstraction over any on-device OCR engine used by the auxiliary OCR path.
 *
 * <p>This interface is the seam between module-level infrastructure (executor lifecycle,
 * bitmap ownership, session tokens) in {@code ImageClassificationActivity} and a concrete
 * implementation. It ensures that future switches (e.g., Google ML Kit ⇐⇒ another engine)
 * require no changes in the activity code beyond wiring a new impl.
 *
 * <p>Implementations must treat the input bitmap as read-only. Returning {@code null} from
 * {@link #recognize(Bitmap)} is forbidden — always return {@link OcrResult#empty()} instead.
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
