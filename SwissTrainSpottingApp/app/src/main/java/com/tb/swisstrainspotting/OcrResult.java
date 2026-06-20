package com.tb.swisstrainspotting;

/**
 * Immutable holder for OCR text from the auxiliary image-pass (Module 6).
 *
 * <p>Distinguishes "no text found" from "text was recognized" via {@link #isEmpty()}.
 * An empty result means either no usable text was detected or the recognized text was
 * whitespace-only — both are normal outcomes in low-quality images; they do not indicate errors.
 */
public final class OcrResult {

    private static final OcrResult EMPTY = new OcrResult(true);

    private final boolean empty;
    private final String text;

    private OcrResult(boolean empty) {
        this.empty = empty;
        this.text = "";
    }

    /**
     * Create a non-empty OCR result with the given cleaned text string.
     */
    public OcrResult(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null for non-empty OcrResult");
        }
        this.empty = false;
        this.text = checkNotNull(text);
    }

    /**
     * Return a shared empty result instance (no recognized text).
     */
    public static OcrResult empty() {
        return EMPTY;
    }

    /**
     * True when no recognized text was found or the text was whitespace-only.
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * The normalized (trimmed, whitespace-collapsed) recognized text.
     * Returns the empty string when {@link #isEmpty()} is true.
     */
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OcrResult that = (OcrResult) o;
        return empty == that.empty && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(empty);
        result = 31 * result + text.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "OcrResult{empty=" + empty + ", text='" + text + "'}";
    }

    private static String checkNotNull(String value) {
        if (value == null) {
            throw new NullPointerException("text must not be null");
        }
        return value;
    }
}
