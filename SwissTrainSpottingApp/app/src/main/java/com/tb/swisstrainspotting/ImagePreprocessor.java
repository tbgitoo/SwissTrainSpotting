package com.tb.swisstrainspotting;

import android.graphics.Bitmap;

final class ImagePreprocessor {

    static final int INPUT_WIDTH = 224;
    static final int INPUT_HEIGHT = 224;
    static final int CHANNELS = 3;
    static final int TENSOR_LENGTH = CHANNELS * INPUT_WIDTH * INPUT_HEIGHT;

    private ImagePreprocessor() {}

    static float[] preprocess(Bitmap bitmap) {
        throw new UnsupportedOperationException("ImagePreprocessor.preprocess not yet implemented");
    }
}
