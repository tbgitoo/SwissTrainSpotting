package com.tb.swisstrainspotting.module05.phaseB;

import com.tb.swisstrainspotting.onnx.LabelLoader;
import com.tb.swisstrainspotting.onnx.ModelProfile;

import org.junit.Test;

import java.io.IOException;

public class LabelLoaderTest {

    @Test(expected = IllegalArgumentException.class)
    public void loadLabels_nullContext_throws() throws IOException {
        LabelLoader.loadLabels(null, "imagenet_classes.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadLabels_emptyPath_throws() throws IOException {
        LabelLoader.loadLabels((android.content.Context) null, "");
    }

    @Test
    public void mobilenetV2Profile_returnsCorrectlyConfiguredProfile() throws Exception {
        ModelProfile profile = ModelProfile.mobileNetV2("input", "output");
        assert "mobilenetv2-imagenet".equals(profile.getId());
        assert "mobilenetv2.onnx".equals(profile.getModelFile());
        assert "imagenet_classes.txt".equals(profile.getLabelsFile());
        assert ModelProfile.LABEL_FORMAT_PLAIN_TEXT.equals(profile.getLabelsFormat());
        assert "input".equals(profile.getInputNodeName());
        assert "output".equals(profile.getOutputNodeName());
    }

    @Test
    public void loadMobileNetV2_defaultNodeNames_works() throws Exception {
        ModelProfile profile = ModelProfile.mobileNetV2(null, null);
        assert "input".equals(profile.getInputNodeName());
        assert "output".equals(profile.getOutputNodeName());
    }
}
