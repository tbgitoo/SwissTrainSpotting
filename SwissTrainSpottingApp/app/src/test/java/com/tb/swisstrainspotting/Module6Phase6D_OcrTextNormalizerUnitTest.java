package com.tb.swisstrainspotting;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.mlkit.vision.text.Text;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6D: JVM unit tests for {@link OcrTextNormalizer}.
 */
public class Module6Phase6D_OcrTextNormalizerUnitTest {

    @Test
    public void normalize_nullInput_returnsEmpty() {
        assertEquals("", OcrTextNormalizer.normalize(null));
    }

    @Test
    public void normalize_blankInput_returnsEmpty() {
        Text visionText = createVisionText();
        assertEquals("", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void normalize_whitespaceOnlyInput_returnsEmpty() {
        Text visionText = createVisionText("   ", "\t", "  \n  ");
        assertEquals("", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void normalize_leadingTrailingWhitespace_trimmed() {
        Text visionText = createVisionText("  Re420  ");
        assertEquals("Re420", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void normalize_repeatedInternalWhitespace_collapsed() {
        Text visionText = createVisionText("Re   420");
        assertEquals("Re 420", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void normalize_multiLineInput_joinsLinesWithSingleSpace() {
        Text visionText = createVisionText("Line one", "Line two");
        assertEquals("Line one Line two", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void normalize_multiBlockInput_joinsLinesWithSingleSpace() {
        Text visionText = createVisionTextFromBlocks(
                new String[]{"Block A line"},
                new String[]{"Block B line"}
        );
        assertEquals("Block A line Block B line", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void normalize_alreadyCleanInput_preserved() {
        Text visionText = createVisionText("Re420");
        assertEquals("Re420", OcrTextNormalizer.normalize(visionText));
    }

    @Test
    public void collapseWhitespace_repeatedInternalWhitespace_collapsed() {
        assertEquals("a b c", OcrTextNormalizer.collapseWhitespace("a\t\tb\n\nc"));
    }

    private static Text createVisionText(String... lineTexts) {
        return createVisionTextFromBlocks(lineTexts);
    }

    private static Text createVisionTextFromBlocks(String[]... blockLineTexts) {
        Text visionText = mock(Text.class);
        List<Text.TextBlock> blocks = new ArrayList<>();

        for (String[] lineTexts : blockLineTexts) {
            Text.TextBlock block = mock(Text.TextBlock.class);
            List<Text.Line> lines = new ArrayList<>();
            for (String lineText : lineTexts) {
                Text.Line line = mock(Text.Line.class);
                when(line.getText()).thenReturn(lineText);
                lines.add(line);
            }
            when(block.getLines()).thenReturn(lines);
            blocks.add(block);
        }

        when(visionText.getTextBlocks()).thenReturn(blocks);
        return visionText;
    }
}
